package com.ecommerce.aicommercesupport.order.service;

import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.order.repository.OrderItemRepository;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
