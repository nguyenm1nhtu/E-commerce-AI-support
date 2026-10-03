package com.ecommerce.aicommercesupport.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID orderId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
