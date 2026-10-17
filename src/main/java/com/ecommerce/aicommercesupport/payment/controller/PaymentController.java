package com.ecommerce.aicommercesupport.payment.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import com.ecommerce.aicommercesupport.payment.dto.CreatePaymentRequest;
import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.payment.dto.UpdatePaymentRequest;
import com.ecommerce.aicommercesupport.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping("/api/orders/{orderId}/payment")
    public PaymentDto getPayment(@PathVariable UUID orderId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return paymentService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/api/orders/{orderId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Payment",
            responses = @ApiResponse(responseCode = "409", description = "Order already has a payment"))
    public PaymentDto create(@PathVariable UUID orderId, @Valid @RequestBody CreatePaymentRequest request,
            Principal principal) {
        return paymentService.createPayment(currentUserId.get(principal), orderId, request);
    }

    @DeleteMapping("/api/orders/{orderId}/payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned Payment")
    public void delete(@PathVariable UUID orderId, Principal principal) {
        paymentService.deletePayment(currentUserId.get(principal), orderId);
    }

    @DeleteMapping("/api/payments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all Payment belonging to the current user")
    public void deleteAll(Principal principal) {
        paymentService.deleteAllPayments(currentUserId.get(principal));
    }

    @PutMapping("/api/orders/{orderId}/payment")
    @Operation(summary = "Update Payment", description = "Replace mutable fields of an owned resource; does not create a missing resource.")
    public PaymentDto update(@PathVariable UUID orderId, @Valid @RequestBody UpdatePaymentRequest request,
            Principal principal) {
        return paymentService.updatePayment(currentUserId.get(principal), orderId, request);
    }
}
