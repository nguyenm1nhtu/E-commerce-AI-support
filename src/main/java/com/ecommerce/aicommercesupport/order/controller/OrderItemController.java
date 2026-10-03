package com.ecommerce.aicommercesupport.order.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.service.OrderItemService;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Order items")
@RequestMapping("/api/orders/{orderId}/items")
@RequiredArgsConstructor
public class OrderItemController {

    private final OrderItemService orderItemService;
    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping
    public List<OrderItemDto> getItems(@PathVariable UUID orderId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return orderItemService.getOrderItemsByOrderId(orderId);
    }

    @GetMapping("/{itemId}")
    public OrderItemDto getItem(@PathVariable UUID orderId, @PathVariable UUID itemId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return orderItemService.getOrderItemByIdAndOrderId(itemId, orderId);
    }
}
