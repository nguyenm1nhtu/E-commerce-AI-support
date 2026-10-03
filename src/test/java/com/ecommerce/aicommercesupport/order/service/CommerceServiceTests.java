package com.ecommerce.aicommercesupport.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;
import com.ecommerce.aicommercesupport.payment.service.PaymentService;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.shipment.entity.Carrier;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;
import com.ecommerce.aicommercesupport.shipment.service.ShipmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommerceServiceTests {

    @Autowired
    private OrderService orders;

    @Autowired
    private OrderItemService items;

    @Autowired
    private PaymentService payments;

    @Autowired
    private ShipmentService shipments;

    @Autowired
    private EntityManager entityManager;

    @Test
    void assemblesOrderDetailsAndMapsEveryFieldWithoutMixingOrders() {
        var order = persistOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var otherOrder = persistOrder(UUID.randomUUID(), "2026-09-02T10:00:00Z");
        var item = new OrderItem(order, "Headphones", 2, new BigDecimal("10.50"));
        var secondItem = new OrderItem(order, "Cable", 1, new BigDecimal("5.00"));
        var payment = new Payment(order, PaymentStatus.PAID, "REF-1", Instant.parse("2026-09-01T10:05:00Z"));
        var shipment = new Shipment(order, ShipmentStatus.DELIVERED, Carrier.GHN,
                "TRACK-1", Instant.parse("2026-09-03T10:00:00Z"));
        persist(item, secondItem, payment, shipment,
                new OrderItem(otherOrder, "Other", 1, BigDecimal.ONE),
                new Payment(otherOrder, PaymentStatus.PENDING, null, null),
                new Shipment(otherOrder, ShipmentStatus.PENDING, null, null, null));

        var detail = orders.getOrderById(order.getId());

        assertThat(detail.order()).isEqualTo(new OrderDto(order.getId(), order.getUserId(),
                OrderStatus.CONFIRMED, order.getOrderedAt(), new BigDecimal("26.00")));
        assertThat(detail.items()).containsExactlyInAnyOrder(
                new OrderItemDto(item.getId(), order.getId(), "Headphones", 2, new BigDecimal("10.50")),
                new OrderItemDto(secondItem.getId(), order.getId(), "Cable", 1, new BigDecimal("5.00")));
        assertThat(detail.payment()).isEqualTo(new PaymentDto(payment.getId(), order.getId(),
                PaymentStatus.PAID, "REF-1", payment.getPaidAt()));
        assertThat(detail.shipment()).isEqualTo(new ShipmentDto(shipment.getId(), order.getId(),
                ShipmentStatus.DELIVERED, Carrier.GHN, "TRACK-1", shipment.getDeliveredAt()));
        assertThat(orders.getOrderByIdAndUserId(order.getId(), order.getUserId())).isEqualTo(detail);
        assertThatThrownBy(() -> detail.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsEmptyDetailsForExistingOrderWithoutRelatedRecords() {
        var order = persistOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");

        var detail = orders.getOrderById(order.getId());

        assertThat(detail.items()).isEmpty();
        assertThat(detail.payment()).isNull();
        assertThat(detail.shipment()).isNull();
        assertThat(items.getOrderItemsByOrderId(order.getId())).isEmpty();
        assertThat(payments.findPaymentByOrderId(order.getId())).isEmpty();
        assertThat(shipments.findShipmentByOrderId(order.getId())).isEmpty();
        assertThatThrownBy(() -> payments.getPaymentByOrderId(order.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> shipments.getShipmentByOrderId(order.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsMissingOrderAndWrongOwner() {
        var missingId = UUID.randomUUID();
        var order = persistOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");

        assertThatThrownBy(() -> orders.getOrderById(missingId))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(missingId.toString());
        assertThatThrownBy(() -> orders.getOrderByIdAndUserId(order.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orders.getOrderByIdAndUserId(missingId, order.getUserId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> items.getOrderItemsByOrderId(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void paginatesAndMapsOnlyOrdersForRequestedUser() {
        var userId = UUID.randomUUID();
        var older = persistOrder(userId, "2026-09-01T10:00:00Z");
        var newer = persistOrder(userId, "2026-09-02T10:00:00Z");
        persistOrder(UUID.randomUUID(), "2026-09-03T10:00:00Z");
        var sort = Sort.by(Sort.Direction.DESC, "orderedAt");

        var firstPage = orders.getOrdersByUserId(userId, PageRequest.of(0, 1, sort));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(OrderDto::id).containsExactly(newer.getId());
        assertThat(orders.getOrdersByUserId(userId, PageRequest.of(1, 1, sort)).getContent())
                .extracting(OrderDto::id).containsExactly(older.getId());
        assertThat(orders.getOrdersByUserId(UUID.randomUUID(), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void looksUpItemByIdOrByIdWithinOrder() {
        var order = persistOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var otherOrder = persistOrder(UUID.randomUUID(), "2026-09-02T10:00:00Z");
        var item = new OrderItem(order, "Headphones", 2, new BigDecimal("10.50"));
        persist(item);
        var expected = new OrderItemDto(item.getId(), order.getId(), "Headphones", 2, new BigDecimal("10.50"));

        assertThat(items.getOrderItemById(item.getId())).isEqualTo(expected);
        assertThat(items.getOrderItemByIdAndOrderId(item.getId(), order.getId())).isEqualTo(expected);
        assertThatThrownBy(() -> items.getOrderItemByIdAndOrderId(item.getId(), otherOrder.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> items.getOrderItemById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> items.getOrderItemByIdAndOrderId(UUID.randomUUID(), order.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void looksUpPendingPaymentAndShipmentWithNullableFields() {
        var order = persistOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var payment = new Payment(order, PaymentStatus.PENDING, null, null);
        var shipment = new Shipment(order, ShipmentStatus.PENDING, null, null, null);
        persist(payment, shipment);
        var expectedPayment = new PaymentDto(payment.getId(), order.getId(), PaymentStatus.PENDING, null, null);
        var expectedShipment = new ShipmentDto(shipment.getId(), order.getId(),
                ShipmentStatus.PENDING, null, null, null);

        assertThat(payments.getPaymentById(payment.getId())).isEqualTo(expectedPayment);
        assertThat(payments.getPaymentByOrderId(order.getId())).isEqualTo(expectedPayment);
        assertThat(shipments.getShipmentById(shipment.getId())).isEqualTo(expectedShipment);
        assertThat(shipments.getShipmentByOrderId(order.getId())).isEqualTo(expectedShipment);
        assertThat(orders.getOrderById(order.getId()).payment()).isEqualTo(expectedPayment);
        assertThat(orders.getOrderById(order.getId()).shipment()).isEqualTo(expectedShipment);
    }

    @Test
    void rejectsMissingPaymentAndShipmentOnDirectLookup() {
        var id = UUID.randomUUID();

        assertThatThrownBy(() -> payments.getPaymentById(id))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(id.toString());
        assertThatThrownBy(() -> payments.getPaymentByOrderId(id))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(id.toString());
        assertThatThrownBy(() -> shipments.getShipmentById(id))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(id.toString());
        assertThatThrownBy(() -> shipments.getShipmentByOrderId(id))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(id.toString());
    }

    private Order persistOrder(UUID userId, String orderedAt) {
        var order = new Order(userId, OrderStatus.CONFIRMED,
                Instant.parse(orderedAt), new BigDecimal("26.00"));
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
