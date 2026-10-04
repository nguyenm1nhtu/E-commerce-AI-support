package com.ecommerce.aicommercesupport.order.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import com.ecommerce.aicommercesupport.order.repository.OrderItemRepository;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import com.ecommerce.aicommercesupport.shipment.entity.Carrier;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=simple")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommerceCacheTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    protected CacheManager cacheManager;

    @MockitoBean
    private OrderRepository orders;

    @MockitoBean
    private PaymentRepository payments;

    @MockitoBean
    private ShipmentRepository shipments;

    @MockitoBean
    private OrderItemRepository items;

    private UUID ownerId;
    protected UUID orderId;
    protected UUID itemId;
    private Order order;

    @BeforeEach
    void prepareOrder() {
        ownerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        order = new Order(ownerId, OrderStatus.CONFIRMED, Instant.parse("2026-10-04T00:00:00Z"), BigDecimal.TEN);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orders.findByIdAndUserId(orderId, ownerId)).thenReturn(Optional.of(order));
        when(orders.existsById(orderId)).thenReturn(true);
        populateRelatedRecords();
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "shipment", "items", "item"})
    void repeatedRequestsHitCacheButAlwaysCheckOwnership(String resource) throws Exception {
        var first = request(resource);
        assertThat(request(resource)).isEqualTo(first);
        verifyLookup(resource, 1);
        verify(orders, times(2)).findByIdAndUserId(orderId, ownerId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "shipment", "items", "item"})
    void warmCacheDoesNotAllowOtherUsersAnonymousRequestsOrRevokedOwnership(String resource) throws Exception {
        request(resource);
        mvc.perform(get(path(resource)).with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(resource)).accept("application/json"))
                .andExpect(status().isUnauthorized());
        when(orders.findByIdAndUserId(orderId, ownerId)).thenReturn(Optional.empty());
        mvc.perform(get(path(resource)).with(user(ownerId.toString())))
                .andExpect(status().isNotFound());
        verifyLookup(resource, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "shipment", "item"})
    void missingResourceIsNotCachedAndCanAppearOnNextRequest(String resource) throws Exception {
        if (resource.equals("payment")) {
            when(payments.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        } else if (resource.equals("shipment")) {
            when(shipments.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        } else {
            when(items.findByIdAndOrder_Id(itemId, orderId)).thenReturn(Optional.empty());
        }
        mvc.perform(get(path(resource)).with(user(ownerId.toString())))
                .andExpect(status().isNotFound());
        populateRelatedRecords();
        request(resource);
        request(resource);
        verifyLookup(resource, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "shipment", "items", "item"})
    void evictionReloadsUpdatedData(String resource) throws Exception {
        var first = request(resource);
        when(payments.findByOrder_Id(orderId)).thenReturn(Optional.of(
                new Payment(order, PaymentStatus.PAID, "NEW-REF", Instant.parse("2026-10-04T01:00:00Z"))));
        when(shipments.findByOrder_Id(orderId)).thenReturn(Optional.of(
                new Shipment(order, ShipmentStatus.DELIVERED, Carrier.GHN, "NEW-TRACK",
                        Instant.parse("2026-10-04T02:00:00Z"))));
        populateItems("Updated item");
        assertThat(request(resource)).isEqualTo(first);
        cacheManager.getCache(cacheName(resource)).evict(cacheKey(resource));
        assertThat(request(resource)).isNotEqualTo(first);
        verifyLookup(resource, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "shipment", "items", "item"})
    void differentOrdersUseDifferentCacheKeys(String resource) throws Exception {
        request(resource);
        var originalKey = cacheKey(resource);
        prepareOrder();
        request(resource);
        assertThat(cacheManager.getCache(cacheName(resource)).get(originalKey)).isNotNull();
        assertThat(cacheManager.getCache(cacheName(resource)).get(cacheKey(resource))).isNotNull();
        verifyLookup(resource, 1);
    }

    protected String request(String resource) throws Exception {
        return mvc.perform(get(path(resource)).with(user(ownerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(resource.equals("items") ? "$[0].orderId" : "$.orderId").value(orderId.toString()))
                .andReturn().getResponse().getContentAsString();
    }

    private String path(String resource) {
        return "/api/orders/" + orderId + "/" + (resource.equals("item") ? "items/" + itemId : resource);
    }

    private void populateRelatedRecords() {
        populateItems("Headphones");
        when(payments.findByOrder_Id(orderId)).thenReturn(Optional.of(
                new Payment(order, PaymentStatus.PENDING, null, null)));
        when(shipments.findByOrder_Id(orderId)).thenReturn(Optional.of(
                new Shipment(order, ShipmentStatus.PENDING, null, null, null)));
    }

    private void verifyLookup(String resource, int count) {
        if (resource.equals("payment")) {
            verify(payments, times(count)).findByOrder_Id(orderId);
        } else if (resource.equals("shipment")) {
            verify(shipments, times(count)).findByOrder_Id(orderId);
        } else if (resource.equals("items")) {
            verify(items, times(count)).findByOrder_Id(orderId);
        } else {
            verify(items, times(count)).findByIdAndOrder_Id(itemId, orderId);
        }
    }

    protected String cacheName(String resource) {
        return switch (resource) {
            case "items" -> "order-items";
            case "item" -> "order-item";
            default -> resource + "-by-order";
        };
    }

    protected Object cacheKey(String resource) {
        return resource.equals("item") ? orderId + ":" + itemId : orderId;
    }

    private void populateItems(String productName) {
        var item = new OrderItem(order, productName, 2, BigDecimal.TEN);
        ReflectionTestUtils.setField(item, "id", itemId);
        when(items.findByOrder_Id(orderId)).thenReturn(List.of(item));
        when(items.findByIdAndOrder_Id(itemId, orderId)).thenReturn(Optional.of(item));
    }

    @Test
    void orderDetailsReuseItemListCacheAfterOwnershipCheck() throws Exception {
        request("items");
        mvc.perform(get("/api/orders/" + orderId).with(user(ownerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(itemId.toString()));
        verifyLookup("items", 1);
        verify(orders, times(2)).findByIdAndUserId(orderId, ownerId);
    }

    @Test
    void cachesEmptyItemListForExistingOrder() throws Exception {
        when(items.findByOrder_Id(orderId)).thenReturn(List.of());
        for (int i = 0; i < 2; i++) {
            mvc.perform(get(path("items")).with(user(ownerId.toString())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
        verifyLookup("items", 1);
        verify(orders, times(2)).findByIdAndUserId(orderId, ownerId);
        populateItems("New item");
        cacheManager.getCache("order-items").evict(orderId);
        request("items");
        verifyLookup("items", 2);
    }

    @Test
    void missingOrderDoesNotCacheAnEmptyItemList() throws Exception {
        when(orders.findByIdAndUserId(orderId, ownerId)).thenReturn(Optional.empty());
        mvc.perform(get(path("items")).with(user(ownerId.toString())))
                .andExpect(status().isNotFound());
        assertThat(cacheManager.getCache("order-items").get(orderId)).isNull();
        verifyLookup("items", 0);
        when(orders.findByIdAndUserId(orderId, ownerId)).thenReturn(Optional.of(order));
        request("items");
    }

    @Test
    void itemCacheCannotBeReusedUnderAnotherOrder() throws Exception {
        request("item");
        var originalItemId = itemId;
        prepareOrder();
        itemId = originalItemId;
        mvc.perform(get(path("item")).with(user(ownerId.toString())))
                .andExpect(status().isNotFound());
        verifyLookup("item", 1);
    }

    @Test
    void differentItemsInSameOrderUseDifferentKeys() throws Exception {
        request("item");
        var originalKey = cacheKey("item");
        itemId = UUID.randomUUID();
        populateItems("Cable");
        assertThat(request("item")).contains("Cable");
        assertThat(cacheManager.getCache("order-item").get(originalKey)).isNotNull();
        assertThat(cacheManager.getCache("order-item").get(cacheKey("item"))).isNotNull();
    }
}
