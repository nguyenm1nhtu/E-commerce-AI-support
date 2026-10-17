package com.ecommerce.aicommercesupport.order.service;

import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.common.cache.CacheEviction;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderDetailDto;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.dto.UpdateOrderRequest;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.repository.OrderItemRepository;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import com.ecommerce.aicommercesupport.payment.service.PaymentService;
import com.ecommerce.aicommercesupport.shipment.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemService orderItemService;
    private final PaymentService paymentService;
    private final ShipmentService shipmentService;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final CacheEviction cacheEviction;

    public OrderDetailDto getOrderById(UUID id) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toDetailDto(order);
    }

    public OrderDetailDto getOrderByIdAndUserId(UUID id, UUID userId) {
        var order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toDetailDto(order);
    }

    public Page<OrderDto> getOrdersByUserId(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(this::toDto);
    }

    public void requireOrderOwnership(UUID id, UUID userId) {
        orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private OrderDetailDto toDetailDto(Order order) {
        var orderId = order.getId();
        return new OrderDetailDto(toDto(order), orderItemService.getOrderItemsByOrderId(orderId),
                paymentService.findPaymentByOrderId(orderId).orElse(null),
                shipmentService.findShipmentByOrderId(orderId).orElse(null));
    }

    private OrderDto toDto(Order order) {
        return new OrderDto(order.getId(), order.getUserId(), order.getStatus(),
                order.getOrderedAt(), order.getTotalAmount());
    }

    @Transactional
    public OrderDto createOrder(UUID userId, CreateOrderRequest request) {
        var order = orderRepository.save(new Order(userId, request.status(), request.orderedAt(), request.totalAmount()));
        return new OrderDto(order.getId(), userId, order.getStatus(), order.getOrderedAt(), order.getTotalAmount());
    }

    @Transactional
    public void deleteOrder(UUID userId, UUID orderId) {
        var order = owned(userId, orderId);
        var existingItems = orderItemRepository.findByOrder_Id(orderId);
        existingItems.forEach(item -> cacheEviction.afterCommit("order-item", orderId + ":" + item.getId()));
        orderItemRepository.deleteAll(existingItems);
        cacheEviction.afterCommit("order-items", orderId);
        paymentRepository.findByOrder_Id(orderId).ifPresent(paymentRepository::delete);
        cacheEviction.afterCommit("payment-by-order", orderId);
        shipmentRepository.findByOrder_Id(orderId).ifPresent(shipmentRepository::delete);
        cacheEviction.afterCommit("shipment-by-order", orderId);
        // Flush child deletions before deleting the parent with non-cascading foreign keys.
        orderRepository.flush();
        orderRepository.delete(order);
    }

    @Transactional
    public void deleteAllOrders(UUID userId) {
        for (var order : orderRepository.findAllByUserIdOrderById(userId)) {
            deleteOrder(userId, order.getId());
        }
    }

    private Order owned(UUID userId, UUID orderId) {
        return orderRepository.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    @Transactional
    public OrderDto updateOrder(UUID userId, UUID orderId, UpdateOrderRequest request) {
        var order = owned(userId, orderId);
        order.setStatus(request.status());
        order.setOrderedAt(request.orderedAt());
        order.setTotalAmount(request.totalAmount());
        return toDto(order);
    }
}
