package com.ecommerce.aicommercesupport.order.controller;

import java.util.UUID;

import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=simple")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommerceCrudControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private com.ecommerce.aicommercesupport.auth.service.JwtService jwt;
    @Autowired private CacheManager caches;
    @Autowired private OrderRepository orders;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.ecommerce.aicommercesupport.order.service.OrderItemService orderItems;
    private UUID owner;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
    }

    @Test
    void exposesCrudOperationsInSwagger() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/orders'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/orders'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/order-items'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/payments'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/shipments'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/items/{itemId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/payment'].put").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/shipment'].put").exists());
    }

    @Test
    void createsReadsAndDeletesEachResourceWithCacheEviction() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id;
        mvc.perform(as(owner, get(path + "/items"))).andExpect(jsonPath("$.length()").value(0));
        var itemId = createChild(owner, path + "/items", "{\"productName\":\"Mouse\",\"quantity\":2,\"unitPrice\":10.50}");
        mvc.perform(as(owner, get(path + "/items"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(owner, get(path + "/items/" + itemId))).andExpect(status().isOk());
        createChild(owner, path + "/payment", "{\"status\":\"PAID\",\"providerRef\":\"TEST\"}");
        createChild(owner, path + "/shipment", "{\"status\":\"SHIPPED\",\"carrier\":\"GHN\"}");
        for (var child : new String[] {"payment", "shipment"}) {
            mvc.perform(as(owner, get(path + "/" + child))).andExpect(status().isOk());
            assertThat(caches.getCache(child + "-by-order").get(UUID.fromString(id))).isNotNull();
            mvc.perform(as(owner, delete(path + "/" + child))).andExpect(status().isNoContent());
            assertThat(caches.getCache(child + "-by-order").get(UUID.fromString(id))).isNull();
            mvc.perform(as(owner, get(path + "/" + child))).andExpect(status().isNotFound());
            mvc.perform(as(owner, delete(path + "/" + child))).andExpect(status().isNotFound());
            createChild(owner, path + "/" + child, "{\"status\":\"PENDING\"}");
            mvc.perform(as(owner, get(path + "/" + child))).andExpect(jsonPath("$.status").value("PENDING"));
        }
        mvc.perform(as(owner, delete(path + "/items/" + itemId))).andExpect(status().isNoContent());
        assertThat(caches.getCache("order-item").get(id + ":" + itemId)).isNull();
        mvc.perform(as(owner, get(path + "/items"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(owner, delete(path))).andExpect(status().isNoContent());
        mvc.perform(as(owner, get(path))).andExpect(status().isNotFound());
    }

    @Test
    void bulkDeletesOnlyCurrentUsersDataAndCascadesOrderChildren() throws Exception {
        var foreign = UUID.randomUUID();
        var foreignId = createOrder(foreign);
        var first = createOrder(owner);
        var second = createOrder(owner);
        for (var id : new String[] {first, second, foreignId}) {
            var actor = id.equals(foreignId) ? foreign : owner;
            var path = "/api/orders/" + id;
            createChild(actor, path + "/items", "{\"productName\":\"A\",\"quantity\":1,\"unitPrice\":1}");
            createChild(actor, path + "/payment", "{\"status\":\"PENDING\"}");
            createChild(actor, path + "/shipment", "{\"status\":\"PENDING\"}");
            mvc.perform(as(actor, get(path))).andExpect(status().isOk());
            mvc.perform(as(actor, get(path + "/payment"))).andExpect(status().isOk());
            mvc.perform(as(actor, get(path + "/shipment"))).andExpect(status().isOk());
        }
        for (var resource : new String[] {"order-items", "payments", "shipments"}) {
            mvc.perform(as(owner, delete("/api/" + resource))).andExpect(status().isNoContent());
            mvc.perform(as(owner, delete("/api/" + resource))).andExpect(status().isNoContent());
        }
        for (var id : new String[] {first, second}) {
            mvc.perform(as(owner, get("/api/orders/" + id)))
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.payment").isEmpty()).andExpect(jsonPath("$.shipment").isEmpty());
        }
        createChild(owner, "/api/orders/" + first + "/items", "{\"productName\":\"B\",\"quantity\":1,\"unitPrice\":1}");
        createChild(owner, "/api/orders/" + first + "/payment", "{\"status\":\"PENDING\"}");
        createChild(owner, "/api/orders/" + first + "/shipment", "{\"status\":\"PENDING\"}");
        mvc.perform(as(owner, delete("/api/orders"))).andExpect(status().isNoContent());
        mvc.perform(as(owner, delete("/api/orders"))).andExpect(status().isNoContent());
        assertThat(orders.existsById(UUID.fromString(first))).isFalse();
        assertThat(orders.existsById(UUID.fromString(second))).isFalse();
        mvc.perform(as(foreign, get("/api/orders/" + foreignId)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.shipment.status").value("PENDING"));
    }

    @Test
    void rejectsForeignWritesEvenWithWarmCachesAndRejectsMissingAuthentication() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id;
        var item = createChild(owner, path + "/items", "{\"productName\":\"A\",\"quantity\":1,\"unitPrice\":1}");
        createChild(owner, path + "/payment", "{\"status\":\"PENDING\"}");
        createChild(owner, path + "/shipment", "{\"status\":\"PENDING\"}");
        var foreign = UUID.randomUUID();
        for (var suffix : new String[] {"", "/items", "/items/" + item, "/payment", "/shipment"}) {
            mvc.perform(as(owner, get(path + suffix))).andExpect(status().isOk());
            mvc.perform(as(foreign, delete(path + suffix))).andExpect(status().isNotFound());
            mvc.perform(delete(path + suffix).with(csrf())).andExpect(status().isUnauthorized());
        }
        for (var child : new String[] {"payment", "shipment"}) {
            mvc.perform(as(foreign, post(path + "/" + child).content("{\"status\":\"PENDING\"}")))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(as(foreign, post(path + "/items").content("{\"productName\":\"A\",\"quantity\":1,\"unitPrice\":1}")))
                .andExpect(status().isNotFound());
        for (var root : new String[] {"orders", "order-items", "payments", "shipments"}) {
            mvc.perform(delete("/api/" + root).with(csrf())).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesInputAndRejectsDuplicateOneToOneResources() throws Exception {
        mvc.perform(as(owner, post("/api/orders").content("{}"))).andExpect(status().isBadRequest());
        mvc.perform(as(owner, post("/api/orders").content("{\"status\":\"PENDING\",\"orderedAt\":\"2026-10-17T00:00:00Z\",\"totalAmount\":-1}")))
                .andExpect(status().isBadRequest());
        var path = "/api/orders/" + createOrder(owner);
        mvc.perform(as(owner, post(path + "/items").content("{\"productName\":\"\",\"quantity\":0,\"unitPrice\":-1}")))
                .andExpect(status().isBadRequest());
        for (var child : new String[] {"payment", "shipment"}) {
            mvc.perform(as(owner, post(path + "/" + child).content("{\"status\":\"INVALID\"}"))).andExpect(status().isBadRequest());
            createChild(owner, path + "/" + child, "{\"status\":\"PENDING\"}");
            mvc.perform(as(owner, post(path + "/" + child).content("{\"status\":\"PENDING\"}"))).andExpect(status().isConflict());
        }
        mvc.perform(as(owner, delete("/api/orders/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void deletesItemsWithinOneOrderAndKeepsCacheOnRollback() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id;
        var item = createChild(owner, path + "/items", "{\"productName\":\"A\",\"quantity\":1,\"unitPrice\":1}");
        mvc.perform(as(owner, get(path + "/items"))).andExpect(status().isOk());
        mvc.perform(as(owner, get(path + "/items/" + item))).andExpect(status().isOk());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            orderItems.deleteItems(owner, UUID.fromString(id));
            assertThat(caches.getCache("order-item").get(id + ":" + item)).isNotNull();
            tx.setRollbackOnly();
        });
        assertThat(caches.getCache("order-item").get(id + ":" + item)).isNotNull();
        mvc.perform(as(owner, get(path + "/items"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(owner, delete(path + "/items"))).andExpect(status().isNoContent());
        assertThat(caches.getCache("order-item").get(id + ":" + item)).isNull();
        mvc.perform(as(owner, get(path + "/items"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updatesAllResourcesAndReloadsWarmCaches() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id;
        var item = createChild(owner, path + "/items", "{\"productName\":\"Before\",\"quantity\":1,\"unitPrice\":1}");
        createChild(owner, path + "/payment", "{\"status\":\"PENDING\",\"providerRef\":\"old\"}");
        createChild(owner, path + "/shipment", "{\"status\":\"PENDING\",\"trackingCode\":\"old\"}");
        for (var suffix : new String[] {"/items", "/items/" + item, "/payment", "/shipment"}) {
            mvc.perform(as(owner, get(path + suffix))).andExpect(status().isOk());
        }
        mvc.perform(as(owner, put(path).content("{\"status\":\"CONFIRMED\",\"orderedAt\":\"2026-10-18T00:00:00Z\",\"totalAmount\":30}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userId").value(owner.toString()));
        mvc.perform(as(owner, put(path + "/items/" + item).content("{\"productName\":\"After\",\"quantity\":3,\"unitPrice\":10}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(item));
        mvc.perform(as(owner, put(path + "/payment").content("{\"status\":\"PAID\",\"paidAt\":\"2026-10-18T01:00:00Z\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.providerRef").isEmpty());
        mvc.perform(as(owner, put(path + "/shipment").content("{\"status\":\"DELIVERED\",\"carrier\":\"GHN\",\"deliveredAt\":\"2026-10-19T00:00:00Z\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trackingCode").isEmpty());
        mvc.perform(as(owner, get(path))).andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.order.totalAmount").value(30))
                .andExpect(jsonPath("$.items[0].productName").value("After"));
        mvc.perform(as(owner, get(path + "/items/" + item)))
                .andExpect(jsonPath("$.quantity").value(3)).andExpect(jsonPath("$.unitPrice").value(10));
        mvc.perform(as(owner, get(path + "/payment"))).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(as(owner, get(path + "/shipment"))).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void updatesRejectForeignOwnersInvalidBodiesAndMissingResources() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id;
        var item = createChild(owner, path + "/items", "{\"productName\":\"Before\",\"quantity\":1,\"unitPrice\":1}");
        createChild(owner, path + "/payment", "{\"status\":\"PENDING\"}");
        createChild(owner, path + "/shipment", "{\"status\":\"PENDING\"}");
        var foreign = UUID.randomUUID();
        var bodies = java.util.Map.of(
                "", "{\"status\":\"PENDING\",\"orderedAt\":\"2026-10-17T00:00:00Z\",\"totalAmount\":10}",
                "/items/" + item, "{\"productName\":\"After\",\"quantity\":1,\"unitPrice\":10}",
                "/payment", "{\"status\":\"PENDING\"}",
                "/shipment", "{\"status\":\"PENDING\"}");
        for (var entry : bodies.entrySet()) {
            mvc.perform(as(owner, get(path + entry.getKey()))).andExpect(status().isOk());
            mvc.perform(as(foreign, put(path + entry.getKey()).content(entry.getValue())))
                    .andExpect(status().isNotFound());
            mvc.perform(put(path + entry.getKey()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(entry.getValue()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(as(owner, put(path + entry.getKey()).content("{}"))).andExpect(status().isBadRequest());
            mvc.perform(as(owner, put("/api/orders/" + UUID.randomUUID() + entry.getKey()).content(entry.getValue())))
                    .andExpect(status().isNotFound());
        }
        var empty = "/api/orders/" + createOrder(owner);
        mvc.perform(as(owner, put(empty + "/items/" + item).content(bodies.get("/items/" + item))))
                .andExpect(status().isNotFound());
        for (var child : new String[] {"payment", "shipment"}) {
            mvc.perform(as(owner, put(empty + "/" + child).content("{\"status\":\"PENDING\"}")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void rolledBackUpdatePreservesDataAndCache() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id + "/items";
        var item = createChild(owner, path, "{\"productName\":\"Before\",\"quantity\":1,\"unitPrice\":1}");
        mvc.perform(as(owner, get(path))).andExpect(status().isOk());
        mvc.perform(as(owner, get(path + "/" + item))).andExpect(status().isOk());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            orderItems.updateItem(owner, UUID.fromString(id), UUID.fromString(item),
                    new com.ecommerce.aicommercesupport.order.dto.UpdateOrderItemRequest("After", 2, java.math.BigDecimal.TEN));
            assertThat(caches.getCache("order-item").get(id + ":" + item)).isNotNull();
            tx.setRollbackOnly();
        });
        mvc.perform(as(owner, get(path + "/" + item))).andExpect(jsonPath("$.productName").value("Before"));
        caches.getCache("order-item").evict(id + ":" + item);
        mvc.perform(as(owner, get(path + "/" + item))).andExpect(jsonPath("$.productName").value("Before"));
    }

    private String createOrder(UUID actor) throws Exception {
        return createChild(actor, "/api/orders", "{\"status\":\"PENDING\",\"orderedAt\":\"2026-10-17T00:00:00Z\",\"totalAmount\":21}");
    }

    private String createChild(UUID actor, String path, String body) throws Exception {
        var result = mvc.perform(as(actor, post(path).content(body))).andExpect(status().isCreated()).andReturn();
        return JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MockHttpServletRequestBuilder as(UUID actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor,
                com.ecommerce.aicommercesupport.user.entity.UserRole.CUSTOMER).getTokenValue())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
