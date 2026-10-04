package com.ecommerce.aicommercesupport.payment.service;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentDto getPaymentById(UUID id) {
        return paymentRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    @Cacheable(cacheNames = "payment-by-order", key = "#orderId")
    public PaymentDto getPaymentByOrderId(UUID orderId) {
        return findPaymentByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
    }

    public Optional<PaymentDto> findPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrder_Id(orderId).map(this::toDto);
    }

    private PaymentDto toDto(Payment payment) {
        return new PaymentDto(payment.getId(), payment.getOrder().getId(), payment.getStatus(),
                payment.getProviderRef(), payment.getPaidAt());
    }
}
