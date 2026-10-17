package com.ecommerce.aicommercesupport.payment.service;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.cache.CacheEviction;
import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.payment.dto.CreatePaymentRequest;
import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.payment.dto.UpdatePaymentRequest;
import com.ecommerce.aicommercesupport.payment.entity.Payment;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final CacheEviction cacheEviction;
    private final OrderRepository orderRepository;

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

    @Transactional
    public PaymentDto createPayment(UUID userId, UUID orderId, CreatePaymentRequest request) {
        var order = owned(userId, orderId);
        if (paymentRepository.findByOrder_Id(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a payment");
        }
        var payment = paymentRepository.save(new Payment(order, request.status(), request.providerRef(), request.paidAt()));
        cacheEviction.afterCommit("payment-by-order", orderId);
        return new PaymentDto(payment.getId(), orderId, payment.getStatus(), payment.getProviderRef(), payment.getPaidAt());
    }

    @Transactional
    public void deletePayment(UUID userId, UUID orderId) {
        owned(userId, orderId);
        var payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        paymentRepository.delete(payment);
        cacheEviction.afterCommit("payment-by-order", orderId);
    }

    @Transactional
    public void deleteAllPayments(UUID userId) {
        for (var order : orderRepository.findAllByUserIdOrderById(userId)) {
            owned(userId, order.getId());
            removePayment(order.getId());
        }
    }

    private Order owned(UUID userId, UUID orderId) {
        return orderRepository.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void removePayment(UUID orderId) {
        paymentRepository.findByOrder_Id(orderId).ifPresent(paymentRepository::delete);
        cacheEviction.afterCommit("payment-by-order", orderId);
    }

    @Transactional
    public void deletePaymentsForOrder(UUID userId, UUID orderId) {
        owned(userId, orderId);
        removePayment(orderId);
    }

    @Transactional
    public PaymentDto updatePayment(UUID userId, UUID orderId, UpdatePaymentRequest request) {
        owned(userId, orderId);
        var payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        payment.setStatus(request.status());
        payment.setProviderRef(request.providerRef());
        payment.setPaidAt(request.paidAt());
        cacheEviction.afterCommit("payment-by-order", orderId);
        return toDto(payment);
    }
}
