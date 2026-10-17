package com.ecommerce.aicommercesupport.order.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=simple")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFixtureApiTests {

    @Autowired private MockMvc mvc;
    @Autowired private CacheManager caches;
    @Autowired private com.ecommerce.aicommercesupport.auth.service.JwtService jwt;
    @Autowired private com.ecommerce.aicommercesupport.order.repository.OrderRepository orders;
    @Autowired private com.ecommerce.aicommercesupport.order.repository.OrderItemRepository items;
    @Autowired private com.ecommerce.aicommercesupport.payment.repository.PaymentRepository payments;
    @Autowired private com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository shipments;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private com.ecommerce.aicommercesupport.order.service.CommerceFixtureService fixtures;
    private final UUID owner = UUID.randomUUID();

    @Test
    void createsOnlyForJwtOwnerValidatesAndScopesBulkDeletion() throws Exception {
        var foreign = UUID.randomUUID();
        var id = createOrder(owner);
        var other = createOrder(foreign);
        mvc.perform(as(owner, get("/api/orders/" + id))).andExpect(jsonPath("$.order.userId").value(owner.toString()));
        mvc.perform(as(foreign, delete("/api/orders/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(owner, post("/api/orders").content("{}"))).andExpect(status().isBadRequest());
        mvc.perform(as(owner, delete("/api/orders"))).andExpect(status().isNoContent());
        mvc.perform(as(owner, get("/api/orders/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(foreign, get("/api/orders/" + other))).andExpect(status().isOk());
        mvc.perform(as(owner, delete("/api/orders"))).andExpect(status().isNoContent());
        mvc.perform(as(owner, delete("/api/orders/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void deletesChildrenAndEvictsCachesOnlyAfterCommit() throws Exception {
        var id = UUID.fromString(createOrder(owner));
        var order = orders.findById(id).orElseThrow();
        var item = items.saveAndFlush(new com.ecommerce.aicommercesupport.order.entity.OrderItem(
                order, "Test", 1, java.math.BigDecimal.ONE));
        payments.saveAndFlush(new com.ecommerce.aicommercesupport.payment.entity.Payment(order,
                com.ecommerce.aicommercesupport.payment.entity.PaymentStatus.PENDING, null, null));
        shipments.saveAndFlush(new com.ecommerce.aicommercesupport.shipment.entity.Shipment(order,
                com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus.PENDING, null, null, null));
        var path = "/api/orders/" + id;
        for (var suffix : new String[] {"/items", "/items/" + item.getId(), "/payment", "/shipment"}) {
            mvc.perform(as(owner, get(path + suffix))).andExpect(status().isOk());
        }
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            fixtures.deleteOrder(owner, id);
            assertThat(caches.getCache("order-items").get(id)).isNotNull();
            tx.setRollbackOnly();
        });
        assertThat(orders.existsById(id)).isTrue();
        assertThat(caches.getCache("order-items").get(id)).isNotNull();
        mvc.perform(as(owner, delete(path))).andExpect(status().isNoContent());
        assertThat(items.findByOrder_Id(id)).isEmpty();
        assertThat(payments.findByOrder_Id(id)).isEmpty();
        assertThat(shipments.findByOrder_Id(id)).isEmpty();
        for (var cache : new String[] {"order-items", "payment-by-order", "shipment-by-order"}) {
            assertThat(caches.getCache(cache).get(id)).isNull();
        }
        assertThat(caches.getCache("order-item").get(id + ":" + item.getId())).isNull();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/orders").with(csrf())).andExpect(status().isUnauthorized());
    }

    private String createOrder(UUID actor) throws Exception {
        return create(actor, "/api/orders", "{\"status\":\"PENDING\",\"orderedAt\":\"2026-10-17T00:00:00Z\",\"totalAmount\":10}");
    }

    private String create(UUID actor, String path, String body) throws Exception {
        var result = mvc.perform(as(actor, post(path).content(body))).andExpect(status().isCreated()).andReturn();
        return JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MockHttpServletRequestBuilder as(UUID actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor,
                com.ecommerce.aicommercesupport.user.entity.UserRole.CUSTOMER).getTokenValue())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
