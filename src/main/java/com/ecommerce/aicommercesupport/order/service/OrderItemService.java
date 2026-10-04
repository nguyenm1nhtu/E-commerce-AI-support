package com.ecommerce.aicommercesupport.order.service;

import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import com.ecommerce.aicommercesupport.order.repository.OrderItemRepository;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    public OrderItemDto getOrderItemById(UUID id) {
        return orderItemRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + id));
    }

    @Cacheable(cacheNames = "order-item", key = "#orderId.toString() + ':' + #id.toString()")
    public OrderItemDto getOrderItemByIdAndOrderId(UUID id, UUID orderId) {
        return orderItemRepository.findByIdAndOrder_Id(id, orderId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found in order: " + orderId));
    }

    @Cacheable(cacheNames = "order-items", key = "#orderId")
    public List<OrderItemDto> getOrderItemsByOrderId(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return orderItemRepository.findByOrder_Id(orderId).stream().map(this::toDto).toList();
    }

    private OrderItemDto toDto(OrderItem item) {
        return new OrderItemDto(item.getId(), item.getOrder().getId(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice());
    }
}
