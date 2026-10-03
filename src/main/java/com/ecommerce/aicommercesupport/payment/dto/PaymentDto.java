package com.ecommerce.aicommercesupport.payment.dto;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;

public record PaymentDto(
        UUID id,
        UUID orderId,
        PaymentStatus status,
        String providerRef,
        Instant paidAt
) {
}
