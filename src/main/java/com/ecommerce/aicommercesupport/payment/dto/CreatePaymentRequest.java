package com.ecommerce.aicommercesupport.payment.dto;

import java.time.Instant;
import com.ecommerce.aicommercesupport.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(@NotNull PaymentStatus status, @Size(max = 255) String providerRef, Instant paidAt) {
}
