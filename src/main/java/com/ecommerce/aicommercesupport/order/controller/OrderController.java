package com.ecommerce.aicommercesupport.order.controller;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.dto.CreateOrderRequest;
import com.ecommerce.aicommercesupport.order.dto.OrderDetailDto;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.dto.UpdateOrderRequest;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "orderedAt", "status", "totalAmount");

    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping("/api/orders")
    public PagedModel<OrderDto> getOrders(Principal principal,
            @PageableDefault(size = 20, sort = {"orderedAt", "id"}, direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        var userId = currentUserId.get(principal);
        if (pageable.getSort().stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported order sort field");
        }
        return new PagedModel<>(orderService.getOrdersByUserId(userId, pageable));
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderDetailDto getOrder(@PathVariable UUID orderId, Principal principal) {
        return orderService.getOrderByIdAndUserId(orderId, currentUserId.get(principal));
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Order")
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request,
            Principal principal) {
        return orderService.createOrder(currentUserId.get(principal), request);
    }

    @DeleteMapping("/api/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned Order")
    public void delete(@PathVariable UUID orderId, Principal principal) {
        orderService.deleteOrder(currentUserId.get(principal), orderId);
    }

    @DeleteMapping("/api/orders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all Order belonging to the current user")
    public void deleteAll(Principal principal) {
        orderService.deleteAllOrders(currentUserId.get(principal));
    }

    @PutMapping("/api/orders/{orderId}")
    @Operation(summary = "Update Order", description = "Replace mutable fields of an owned resource; does not create a missing resource.")
    public OrderDto update(@PathVariable UUID orderId, @Valid @RequestBody UpdateOrderRequest request,
            Principal principal) {
        return orderService.updateOrder(currentUserId.get(principal), orderId, request);
    }
}
