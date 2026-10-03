package com.ecommerce.aicommercesupport.order.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommerceRepositoryTests {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderItemRepository items;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private EntityManager entityManager;

    @Test
    void paginatesAndSortsOnlyOrdersBelongingToRequestedUser() {
        var userId = UUID.randomUUID();
        var older = saveOrder(userId, "2026-09-01T10:00:00Z");
        var newer = saveOrder(userId, "2026-09-02T10:00:00Z");
        saveOrder(UUID.randomUUID(), "2026-09-03T10:00:00Z");
        entityManager.clear();

        var sort = Sort.by(Sort.Direction.DESC, "orderedAt");
        var firstPage = orders.findByUserId(userId, PageRequest.of(0, 1, sort));
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(Order::getId).containsExactly(newer.getId());
        assertThat(orders.findByUserId(userId, PageRequest.of(1, 1, sort)).getContent())
                .extracting(Order::getId).containsExactly(older.getId());
        assertThat(orders.findByUserId(UUID.randomUUID(), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void findsOrderOnlyWhenIdAndOwnerBothMatch() {
        var userId = UUID.randomUUID();
        var order = saveOrder(userId, "2026-09-01T10:00:00Z");
        entityManager.clear();

        assertThat(orders.findByIdAndUserId(order.getId(), userId))
                .map(Order::getId).contains(order.getId());
        assertThat(orders.findByIdAndUserId(order.getId(), UUID.randomUUID())).isEmpty();
        assertThat(orders.findByIdAndUserId(UUID.randomUUID(), userId)).isEmpty();
    }

    @Test
    void findsItemsOnlyWithinRequestedOrder() {
        var order = saveOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var otherOrder = saveOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var first = items.saveAndFlush(new OrderItem(order, "Headphones", 1, new BigDecimal("10.00")));
        var second = items.saveAndFlush(new OrderItem(order, "Cable", 1, new BigDecimal("5.00")));
        items.saveAndFlush(new OrderItem(otherOrder, "Other", 1, BigDecimal.ONE));
        entityManager.clear();

        assertThat(items.findByOrder_Id(order.getId())).extracting(OrderItem::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(items.findByIdAndOrder_Id(first.getId(), order.getId()))
                .map(OrderItem::getId).contains(first.getId());
        assertThat(items.findByIdAndOrder_Id(first.getId(), otherOrder.getId())).isEmpty();
        assertThat(items.findByIdAndOrder_Id(UUID.randomUUID(), order.getId())).isEmpty();
        assertThat(items.findByOrder_Id(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsPaymentAndShipmentByOrderAndReturnsEmptyWhenAbsent() {
        var order = saveOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var otherOrder = saveOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        var payment = payments.saveAndFlush(new Payment(order, PaymentStatus.PENDING, null, null));
        var shipment = shipments.saveAndFlush(new Shipment(order, ShipmentStatus.PENDING, null, null, null));
        var otherPayment = payments.saveAndFlush(new Payment(otherOrder, PaymentStatus.PENDING, null, null));
        var otherShipment = shipments.saveAndFlush(new Shipment(otherOrder, ShipmentStatus.PENDING, null, null, null));
        var emptyOrder = saveOrder(UUID.randomUUID(), "2026-09-01T10:00:00Z");
        entityManager.clear();

        assertThat(payments.findByOrder_Id(order.getId())).map(Payment::getId).contains(payment.getId());
        assertThat(shipments.findByOrder_Id(order.getId())).map(Shipment::getId).contains(shipment.getId());
        assertThat(payments.findByOrder_Id(otherOrder.getId())).map(Payment::getId).contains(otherPayment.getId());
        assertThat(shipments.findByOrder_Id(otherOrder.getId())).map(Shipment::getId).contains(otherShipment.getId());
        assertThat(payments.findByOrder_Id(emptyOrder.getId())).isEmpty();
        assertThat(shipments.findByOrder_Id(emptyOrder.getId())).isEmpty();
        assertThat(payments.findByOrder_Id(UUID.randomUUID())).isEmpty();
        assertThat(shipments.findByOrder_Id(UUID.randomUUID())).isEmpty();
    }

    private Order saveOrder(UUID userId, String orderedAt) {
        return orders.saveAndFlush(new Order(userId, OrderStatus.PENDING,
                Instant.parse(orderedAt), new BigDecimal("15.00")));
    }
}
