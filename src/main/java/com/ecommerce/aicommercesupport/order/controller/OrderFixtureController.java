package com.ecommerce.aicommercesupport.order.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
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
@Tag(name = "Test data - Order", description = "Dev/test only. All operations are scoped to the authenticated user's orders.")
@RequiredArgsConstructor
public class OrderFixtureController {

    private final CommerceFixtureService fixtures;
    private final CurrentUserId currentUserId;

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Order test data")
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request, Principal principal) {
        return fixtures.createOrder(currentUserId.get(principal), request);
    }

    @DeleteMapping("/api/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned Order test data")
    public void delete(@PathVariable UUID orderId, Principal principal) {
        fixtures.deleteOrder(currentUserId.get(principal), orderId);
    }

    @DeleteMapping("/api/orders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all Order test data belonging to the current user")
    public void deleteAll(Principal principal) {
        fixtures.deleteAllOrders(currentUserId.get(principal));
    }
}
