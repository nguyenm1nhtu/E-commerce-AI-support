package com.ecommerce.aicommercesupport.order.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.shipment.entity.Carrier;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommercePersistenceTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsCommerceRelationshipsMoneyAndTimestamps() {
        var order = persistOrder();
        var paidAt = Instant.parse("2026-09-01T10:05:00Z");
        var deliveredAt = Instant.parse("2026-09-03T08:00:00Z");
        var item = new OrderItem(order, "Headphones", 2, new BigDecimal("99.95"));
        var secondItem = new OrderItem(order, "Cable", 1, new BigDecimal("10.00"));
        var payment = new Payment(order, PaymentStatus.PAID, "provider-101", paidAt);
        var shipment = new Shipment(order, ShipmentStatus.DELIVERED, Carrier.GHN, "TRACK-101", deliveredAt);
        entityManager.persist(item);
        entityManager.persist(secondItem);
        entityManager.persist(payment);
        entityManager.persist(shipment);
        entityManager.flush();
        entityManager.clear();

        var loadedOrder = entityManager.find(Order.class, order.getId());
        assertThat(loadedOrder.getUserId()).isEqualTo(order.getUserId());
        assertThat(loadedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(loadedOrder.getOrderedAt()).isEqualTo(order.getOrderedAt());
        assertThat(loadedOrder.getTotalAmount()).isEqualByComparingTo("209.90");
        var loadedItem = entityManager.find(OrderItem.class, item.getId());
        assertThat(loadedItem.getOrder().getId()).isEqualTo(order.getId());
        assertThat(loadedItem.getProductName()).isEqualTo("Headphones");
        assertThat(loadedItem.getQuantity()).isEqualTo(2);
        assertThat(loadedItem.getUnitPrice()).isEqualByComparingTo("99.95");
        assertThat(entityManager.find(OrderItem.class, secondItem.getId()).getOrder().getId())
                .isEqualTo(order.getId());
        var loadedPayment = entityManager.find(Payment.class, payment.getId());
        assertThat(loadedPayment.getOrder().getId()).isEqualTo(order.getId());
        assertThat(loadedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(loadedPayment.getProviderRef()).isEqualTo("provider-101");
        assertThat(loadedPayment.getPaidAt()).isEqualTo(paidAt);
        var loadedShipment = entityManager.find(Shipment.class, shipment.getId());
        assertThat(loadedShipment.getOrder().getId()).isEqualTo(order.getId());
        assertThat(loadedShipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(loadedShipment.getCarrier()).isEqualTo(Carrier.GHN);
        assertThat(loadedShipment.getTrackingCode()).isEqualTo("TRACK-101");
        assertThat(loadedShipment.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, payment.getId()))
                .isEqualTo("PAID");
    }

    @Test
    void persistsAllCarriersAsStringsAndRejectsUnsupportedCarrier() {
        for (var carrier : Carrier.values()) {
            var order = persistOrder();
            var shipment = new Shipment(order, ShipmentStatus.PENDING, carrier, null, null);
            entityManager.persist(shipment);
            entityManager.flush();
            entityManager.clear();

            assertThat(entityManager.find(Shipment.class, shipment.getId()).getCarrier()).isEqualTo(carrier);
            assertThat(jdbc.queryForObject("SELECT carrier FROM shipments WHERE id = ?", String.class, shipment.getId()))
                    .isEqualTo(carrier.name());
        }
        var order = persistOrder();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO shipments (id, order_id, status, carrier) VALUES (?, ?, 'PENDING', 'UNSUPPORTED')",
                UUID.randomUUID(), order.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsPendingPaymentAndShipmentWithoutCompletionDetails() {
        var order = persistOrder();
        var payment = new Payment(order, PaymentStatus.PENDING, null, null);
        var shipment = new Shipment(order, ShipmentStatus.PENDING, null, null, null);
        entityManager.persist(payment);
        entityManager.persist(shipment);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Payment.class, payment.getId()).getPaidAt()).isNull();
        assertThat(entityManager.find(Shipment.class, shipment.getId()).getDeliveredAt()).isNull();
        assertThat(entityManager.find(Shipment.class, shipment.getId()).getCarrier()).isNull();
    }

    @Test
    void databaseRejectsDuplicatePaymentAndShipmentForAnOrder() {
        var order = persistOrder();
        for (var table : new String[] { "payments", "shipments" }) {
            var sql = "INSERT INTO " + table + " (id, order_id, status) VALUES (?, ?, 'PENDING')";
            jdbc.update(sql, UUID.randomUUID(), order.getId());
            assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), order.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void databaseRejectsOrphanRecordsAndDeletingReferencedOrder() {
        for (var table : new String[] { "payments", "shipments" }) {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO " + table + " (id, order_id, status) VALUES (?, ?, 'PENDING')",
                    UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertItem(UUID.randomUUID(), "Item", 1, "1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        var order = persistOrder();
        insertItem(order.getId(), "Item", 1, "1.00");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM orders WHERE id = ?", order.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidAmountsQuantityNameAndStatus() {
        var order = persistOrder();
        assertThatThrownBy(() -> insertItem(order.getId(), "Item", 0, "1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(order.getId(), "Item", 1, "-1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(order.getId(), " ", 1, "1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET total_amount = -1 WHERE id = ?", order.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'UNKNOWN' WHERE id = ?", order.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (var table : new String[] { "payments", "shipments" }) {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO " + table + " (id, order_id, status) VALUES (?, ?, 'UNKNOWN')",
                    UUID.randomUUID(), order.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private Order persistOrder() {
        var order = new Order(UUID.randomUUID(), OrderStatus.CONFIRMED,
                Instant.parse("2026-09-01T10:00:00Z"), new BigDecimal("209.90"));
        entityManager.persist(order);
        entityManager.flush();
        return order;
    }

    private void insertItem(UUID orderId, String name, int quantity, String price) {
        jdbc.update("INSERT INTO order_items (id, order_id, product_name, quantity, unit_price) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), orderId, name, quantity, new BigDecimal(price));
    }
}
