package com.ecommerce.aicommercesupport.order.controller;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.dto.OrderDetailDto;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Orders")
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "orderedAt", "status", "totalAmount");

    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping
    public PagedModel<OrderDto> getOrders(Principal principal,
            @PageableDefault(size = 20, sort = {"orderedAt", "id"}, direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        var userId = currentUserId.get(principal);
        if (pageable.getSort().stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported order sort field");
        }
        return new PagedModel<>(orderService.getOrdersByUserId(userId, pageable));
    }

    @GetMapping("/{orderId}")
    public OrderDetailDto getOrder(@PathVariable UUID orderId, Principal principal) {
        return orderService.getOrderByIdAndUserId(orderId, currentUserId.get(principal));
    }
}
