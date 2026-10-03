package com.ecommerce.aicommercesupport.payment.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments")
@RequestMapping("/api/orders/{orderId}/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping
    public PaymentDto getPayment(@PathVariable UUID orderId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return paymentService.getPaymentByOrderId(orderId);
    }
}
