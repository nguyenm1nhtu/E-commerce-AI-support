package com.ecommerce.aicommercesupport.order.service;

import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderRequest;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderItemRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.order.repository.OrderItemRepository;
import com.ecommerce.aicommercesupport.payment.dto.CreatePaymentRequest;
import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import com.ecommerce.aicommercesupport.shipment.dto.CreateShipmentRequest;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Fixture writes only; these operations do not invoke payment or shipping providers. */
@Service
@Profile("(dev | test) & !prod")
@RequiredArgsConstructor
@Transactional
public class CommerceFixtureService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final ShipmentRepository shipments;
    private final CacheManager cacheManager;

    public OrderDto createOrder(UUID userId, CreateOrderRequest request) {
        var order = orders.save(new Order(userId, request.status(), request.orderedAt(), request.totalAmount()));
        return new OrderDto(order.getId(), userId, order.getStatus(), order.getOrderedAt(), order.getTotalAmount());
    }

    public OrderItemDto createItem(UUID userId, UUID orderId, CreateOrderItemRequest request) {
        var order = owned(userId, orderId);
        var item = items.save(new OrderItem(order, request.productName(), request.quantity(), request.unitPrice()));
        evict("order-items", orderId);
        return new OrderItemDto(item.getId(), orderId, item.getProductName(), item.getQuantity(), item.getUnitPrice());
    }

    public PaymentDto createPayment(UUID userId, UUID orderId, CreatePaymentRequest request) {
        var order = owned(userId, orderId);
        if (payments.findByOrder_Id(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a payment");
        }
        var payment = payments.save(new Payment(order, request.status(), request.providerRef(), request.paidAt()));
        evict("payment-by-order", orderId);
        return new PaymentDto(payment.getId(), orderId, payment.getStatus(), payment.getProviderRef(), payment.getPaidAt());
    }

    public ShipmentDto createShipment(UUID userId, UUID orderId, CreateShipmentRequest request) {
        var order = owned(userId, orderId);
        if (shipments.findByOrder_Id(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a shipment");
        }
        var shipment = shipments.save(new Shipment(order, request.status(), request.carrier(),
                request.trackingCode(), request.deliveredAt()));
        evict("shipment-by-order", orderId);
        return new ShipmentDto(shipment.getId(), orderId, shipment.getStatus(), shipment.getCarrier(),
                shipment.getTrackingCode(), shipment.getDeliveredAt());
    }

    public void deleteItem(UUID userId, UUID orderId, UUID itemId) {
        owned(userId, orderId);
        var item = items.findByIdAndOrder_Id(itemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));
        items.delete(item);
        evict("order-item", orderId + ":" + itemId);
        evict("order-items", orderId);
    }

    public void deleteItems(UUID userId, UUID orderId) {
        owned(userId, orderId);
        removeItems(orderId);
    }

    public void deletePayment(UUID userId, UUID orderId) {
        owned(userId, orderId);
        var payment = payments.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        payments.delete(payment);
        evict("payment-by-order", orderId);
    }

    public void deleteShipment(UUID userId, UUID orderId) {
        owned(userId, orderId);
        var shipment = shipments.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order: " + orderId));
        shipments.delete(shipment);
        evict("shipment-by-order", orderId);
    }

    public void deleteOrder(UUID userId, UUID orderId) {
        var order = owned(userId, orderId);
        removeItems(orderId);
        removePayment(orderId);
        removeShipment(orderId);
        // Flush child deletions before deleting the parent with non-cascading foreign keys.
        orders.flush();
        orders.delete(order);
    }

    public void deleteAllOrders(UUID userId) {
        for (var order : orders.findAllByUserIdOrderById(userId)) {
            deleteOrder(userId, order.getId());
        }
    }

    public void deleteAllItems(UUID userId) {
        for (var order : orders.findAllByUserIdOrderById(userId)) {
            owned(userId, order.getId());
            removeItems(order.getId());
        }
    }

    public void deleteAllPayments(UUID userId) {
        for (var order : orders.findAllByUserIdOrderById(userId)) {
            owned(userId, order.getId());
            removePayment(order.getId());
        }
    }

    public void deleteAllShipments(UUID userId) {
        for (var order : orders.findAllByUserIdOrderById(userId)) {
            owned(userId, order.getId());
            removeShipment(order.getId());
        }
    }

    private Order owned(UUID userId, UUID orderId) {
        return orders.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void removeItems(UUID orderId) {
        var existing = items.findByOrder_Id(orderId);
        existing.forEach(item -> evict("order-item", orderId + ":" + item.getId()));
        items.deleteAll(existing);
        evict("order-items", orderId);
    }

    private void removePayment(UUID orderId) {
        payments.findByOrder_Id(orderId).ifPresent(payments::delete);
        evict("payment-by-order", orderId);
    }

    private void removeShipment(UUID orderId) {
        shipments.findByOrder_Id(orderId).ifPresent(shipments::delete);
        evict("shipment-by-order", orderId);
    }

    private void evict(String cacheName, Object key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.evictIfPresent(key);
                }
            }
        });
    }
}
