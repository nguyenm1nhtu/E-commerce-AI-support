package com.ecommerce.aicommercesupport.order.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderItemRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import com.ecommerce.aicommercesupport.order.service.CommerceFixtureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(dev | test) & !prod")
@Tag(name = "Test data - OrderItem", description = "Dev/test only. All operations are scoped to the authenticated user's orders.")
@RequiredArgsConstructor
public class OrderItemFixtureController {

    private final CommerceFixtureService fixtures;
    private final CurrentUserId currentUserId;

    @PostMapping("/api/orders/{orderId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create OrderItem test data")
    public OrderItemDto create(@PathVariable UUID orderId, @Valid @RequestBody CreateOrderItemRequest request, Principal principal) {
        return fixtures.createItem(currentUserId.get(principal), orderId, request);
    }

    @DeleteMapping("/api/orders/{orderId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned OrderItem test data")
    public void delete(@PathVariable UUID orderId, @PathVariable UUID itemId, Principal principal) {
        fixtures.deleteItem(currentUserId.get(principal), orderId, itemId);
    }

    @DeleteMapping("/api/order-items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all OrderItem test data belonging to the current user")
    public void deleteAll(Principal principal) {
        fixtures.deleteAllItems(currentUserId.get(principal));
    }

    @DeleteMapping("/api/orders/{orderId}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all fixture items in an owned order")
    public void deleteItems(@PathVariable UUID orderId, Principal principal) {
        fixtures.deleteItems(currentUserId.get(principal), orderId);
    }
}
