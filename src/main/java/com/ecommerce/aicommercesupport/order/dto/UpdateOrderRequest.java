package com.ecommerce.aicommercesupport.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderRequest(@NotNull OrderStatus status, @NotNull Instant orderedAt,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal totalAmount) {
}
