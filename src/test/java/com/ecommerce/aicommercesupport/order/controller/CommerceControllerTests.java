package com.ecommerce.aicommercesupport.order.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;
import com.ecommerce.aicommercesupport.shipment.entity.Carrier;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommerceControllerTests {

    private static final UUID USER_ID = UUID.fromString("c63fdc08-e5a2-43ed-a995-1c13e7bccdba");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsOrderDetailsAndChildResourcesAsJson() throws Exception {
        var order = persistOrder(USER_ID, "2026-09-01T10:00:00Z");
        var item = new OrderItem(order, "Headphones", 2, new BigDecimal("10.50"));
        var payment = new Payment(order, PaymentStatus.PAID, "REF-1", Instant.parse("2026-09-01T10:05:00Z"));
        var shipment = new Shipment(order, ShipmentStatus.DELIVERED, Carrier.GHN,
                "TRACK-1", Instant.parse("2026-09-03T10:00:00Z"));
        persist(item, payment, shipment);
        var path = "/api/orders/" + order.getId();

        mockMvc.perform(asUser(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.order.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.order.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.order.orderedAt").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.order.totalAmount").value(21.00))
                .andExpect(jsonPath("$.items[0].id").value(item.getId().toString()))
                .andExpect(jsonPath("$.payment.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.shipment.id").value(shipment.getId().toString()));
        mockMvc.perform(asUser(path + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(order.getId().toString()));
        mockMvc.perform(asUser(path + "/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Headphones"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(10.50))
                .andExpect(jsonPath("$.order").doesNotExist());
        mockMvc.perform(asUser(path + "/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.providerRef").value("REF-1"))
                .andExpect(jsonPath("$.paidAt").value("2026-09-01T10:05:00Z"));
        mockMvc.perform(asUser(path + "/shipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.carrier").value("GHN"))
                .andExpect(jsonPath("$.trackingCode").value("TRACK-1"))
                .andExpect(jsonPath("$.deliveredAt").value("2026-09-03T10:00:00Z"));
    }

    @Test
    void paginatesOnlyCurrentUsersOrdersAndIgnoresClientSuppliedUserId() throws Exception {
        var older = persistOrder(USER_ID, "2026-09-01T10:00:00Z");
        var newer = persistOrder(USER_ID, "2026-09-02T10:00:00Z");
        var otherUserId = UUID.randomUUID();
        persistOrder(otherUserId, "2026-09-03T10:00:00Z");

        mockMvc.perform(asUser("/api/orders").param("size", "1").param("userId", otherUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newer.getId().toString()));
        mockMvc.perform(asUser("/api/orders").param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(older.getId().toString()));
        mockMvc.perform(asUser("/api/orders").param("sort", "orderedAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(older.getId().toString()));
    }

    @Test
    void returnsEmptyPageForUserWithoutOrders() throws Exception {
        mockMvc.perform(asUser("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void requiresAuthenticationForEveryEndpoint() throws Exception {
        for (var path : pathsForOrder(UUID.randomUUID(), UUID.randomUUID())) {
            mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/orders").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPrincipalsWithoutUuidIdentity() throws Exception {
        mockMvc.perform(get("/api/orders").with(user("user")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void returnsNotFoundForOtherUsersOrderAndAllChildEndpoints() throws Exception {
        var otherUserId = UUID.randomUUID();
        var order = persistOrder(otherUserId, "2026-09-01T10:00:00Z");
        var item = new OrderItem(order, "Private item", 1, BigDecimal.ONE);
        persist(item, new Payment(order, PaymentStatus.PENDING, null, null),
                new Shipment(order, ShipmentStatus.PENDING, null, null, null));
        for (var path : pathsForOrder(order.getId(), item.getId())) {
            mockMvc.perform(asUser(path).param("userId", otherUserId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    void returnsStandardErrorWhenOrderIsNotFound() throws Exception {
        var orderId = UUID.randomUUID();
        var path = "/api/orders/" + orderId;
        mockMvc.perform(asUser(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found: " + orderId))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void returnsNotFoundForMissingOrderOnEveryChildEndpoint() throws Exception {
        for (var path : pathsForOrder(UUID.randomUUID(), UUID.randomUUID())) {
            mockMvc.perform(asUser(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    void handlesAbsentRelatedRecordsWithoutFailingOrderDetails() throws Exception {
        var order = persistOrder(USER_ID, "2026-09-01T10:00:00Z");
        var path = "/api/orders/" + order.getId();
        mockMvc.perform(asUser(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.payment").value(nullValue()))
                .andExpect(jsonPath("$.shipment").value(nullValue()));
        mockMvc.perform(asUser(path + "/items"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        for (var suffix : new String[] {"/payment", "/shipment", "/items/" + UUID.randomUUID()}) {
            mockMvc.perform(asUser(path + suffix))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    void rejectsItemBelongingToAnotherOrder() throws Exception {
        var order = persistOrder(USER_ID, "2026-09-01T10:00:00Z");
        var otherOrder = persistOrder(USER_ID, "2026-09-02T10:00:00Z");
        var item = new OrderItem(otherOrder, "Other", 1, BigDecimal.ONE);
        persist(item);
        mockMvc.perform(asUser("/api/orders/" + order.getId() + "/items/" + item.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestForMalformedIdsAndUnsupportedSort() throws Exception {
        for (var suffix : new String[] {"", "/items", "/payment", "/shipment"}) {
            mockMvc.perform(asUser("/api/orders/not-a-uuid" + suffix))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        mockMvc.perform(asUser("/api/orders/" + UUID.randomUUID() + "/items/not-a-uuid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asUser("/api/orders").param("sort", "unknown,asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    private MockHttpServletRequestBuilder asUser(String path) {
        return get(path).with(user(USER_ID.toString())).accept(MediaType.APPLICATION_JSON);
    }

    private String[] pathsForOrder(UUID orderId, UUID itemId) {
        var base = "/api/orders/" + orderId;
        return new String[] {base, base + "/items", base + "/items/" + itemId, base + "/payment", base + "/shipment"};
    }

    private Order persistOrder(UUID userId, String orderedAt) {
        var order = new Order(userId, OrderStatus.CONFIRMED,
                Instant.parse(orderedAt), new BigDecimal("21.00"));
        persist(order);
        return order;
    }

    private void persist(Object... entities) {
        for (var entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();
    }
}
