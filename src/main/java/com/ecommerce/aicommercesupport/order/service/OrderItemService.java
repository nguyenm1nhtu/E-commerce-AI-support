package com.ecommerce.aicommercesupport.order.service;

import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.cache.CacheEviction;
import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderItemRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.dto.UpdateOrderItemRequest;
import com.ecommerce.aicommercesupport.order.entity.Order;
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

    private final CacheEviction cacheEviction;

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

    @Transactional
    public OrderItemDto createItem(UUID userId, UUID orderId, CreateOrderItemRequest request) {
        var order = owned(userId, orderId);
        var item = orderItemRepository.save(new OrderItem(order, request.productName(), request.quantity(), request.unitPrice()));
        cacheEviction.afterCommit("order-items", orderId);
        return new OrderItemDto(item.getId(), orderId, item.getProductName(), item.getQuantity(), item.getUnitPrice());
    }

    @Transactional
    public void deleteItem(UUID userId, UUID orderId, UUID itemId) {
        owned(userId, orderId);
        var item = orderItemRepository.findByIdAndOrder_Id(itemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));
        orderItemRepository.delete(item);
        cacheEviction.afterCommit("order-item", orderId + ":" + itemId);
        cacheEviction.afterCommit("order-items", orderId);
    }

    @Transactional
    public void deleteItems(UUID userId, UUID orderId) {
        owned(userId, orderId);
        removeItems(orderId);
    }

    @Transactional
    public void deleteAllItems(UUID userId) {
        for (var order : orderRepository.findAllByUserIdOrderById(userId)) {
            owned(userId, order.getId());
            removeItems(order.getId());
        }
    }

    private Order owned(UUID userId, UUID orderId) {
        return orderRepository.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void removeItems(UUID orderId) {
        var existing = orderItemRepository.findByOrder_Id(orderId);
        existing.forEach(item -> cacheEviction.afterCommit("order-item", orderId + ":" + item.getId()));
        orderItemRepository.deleteAll(existing);
        cacheEviction.afterCommit("order-items", orderId);
    }

    @Transactional
    public OrderItemDto updateItem(UUID userId, UUID orderId, UUID itemId, UpdateOrderItemRequest request) {
        owned(userId, orderId);
        var item = orderItemRepository.findByIdAndOrder_Id(itemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));
        item.setProductName(request.productName());
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        cacheEviction.afterCommit("order-items", orderId);
        cacheEviction.afterCommit("order-item", orderId + ":" + itemId);
        return toDto(item);
    }
}
