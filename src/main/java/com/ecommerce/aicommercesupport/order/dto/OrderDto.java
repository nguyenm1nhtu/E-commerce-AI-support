package com.ecommerce.aicommercesupport.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.entity.OrderStatus;

public record OrderDto(
        UUID id,
        UUID userId,
        OrderStatus status,
        Instant orderedAt,
        BigDecimal totalAmount
) {
}
