package com.ecommerce.aicommercesupport.order.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderItemRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.dto.UpdateOrderItemRequest;
import com.ecommerce.aicommercesupport.order.service.OrderItemService;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Order items")
@RequiredArgsConstructor
public class OrderItemController {

    private final OrderItemService orderItemService;
    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping("/api/orders/{orderId}/items")
    public List<OrderItemDto> getItems(@PathVariable UUID orderId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return orderItemService.getOrderItemsByOrderId(orderId);
    }

    @GetMapping("/api/orders/{orderId}/items/{itemId}")
    public OrderItemDto getItem(@PathVariable UUID orderId, @PathVariable UUID itemId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return orderItemService.getOrderItemByIdAndOrderId(itemId, orderId);
    }

    @PostMapping("/api/orders/{orderId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create OrderItem")
    public OrderItemDto create(@PathVariable UUID orderId, @Valid @RequestBody CreateOrderItemRequest request,
            Principal principal) {
        return orderItemService.createItem(currentUserId.get(principal), orderId, request);
    }

    @DeleteMapping("/api/orders/{orderId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned OrderItem")
    public void delete(@PathVariable UUID orderId, @PathVariable UUID itemId, Principal principal) {
        orderItemService.deleteItem(currentUserId.get(principal), orderId, itemId);
    }

    @DeleteMapping("/api/order-items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all OrderItem belonging to the current user")
    public void deleteAll(Principal principal) {
        orderItemService.deleteAllItems(currentUserId.get(principal));
    }

    @DeleteMapping("/api/orders/{orderId}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all items in an owned order")
    public void deleteItems(@PathVariable UUID orderId, Principal principal) {
        orderItemService.deleteItems(currentUserId.get(principal), orderId);
    }

    @PutMapping("/api/orders/{orderId}/items/{itemId}")
    @Operation(summary = "Update OrderItem", description = "Replace mutable fields of an owned resource; does not create a missing resource.")
    public OrderItemDto update(@PathVariable UUID orderId, @PathVariable UUID itemId, @Valid @RequestBody UpdateOrderItemRequest request,
            Principal principal) {
        return orderItemService.updateItem(currentUserId.get(principal), orderId, itemId, request);
    }
}
