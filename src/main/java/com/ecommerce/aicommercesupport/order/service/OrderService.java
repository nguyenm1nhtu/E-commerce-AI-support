package com.ecommerce.aicommercesupport.order.service;

import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.dto.OrderDetailDto;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
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
}
