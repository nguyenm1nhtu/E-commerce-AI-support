package com.ecommerce.aicommercesupport.shipment.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import com.ecommerce.aicommercesupport.shipment.dto.CreateShipmentRequest;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.shipment.dto.UpdateShipmentRequest;
import com.ecommerce.aicommercesupport.shipment.service.ShipmentService;
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
@Tag(name = "Shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping("/api/orders/{orderId}/shipment")
    public ShipmentDto getShipment(@PathVariable UUID orderId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return shipmentService.getShipmentByOrderId(orderId);
    }

    @PostMapping("/api/orders/{orderId}/shipment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Shipment",
            responses = @ApiResponse(responseCode = "409", description = "Order already has a shipment"))
    public ShipmentDto create(@PathVariable UUID orderId, @Valid @RequestBody CreateShipmentRequest request,
            Principal principal) {
        return shipmentService.createShipment(currentUserId.get(principal), orderId, request);
    }

    @DeleteMapping("/api/orders/{orderId}/shipment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned Shipment")
    public void delete(@PathVariable UUID orderId, Principal principal) {
        shipmentService.deleteShipment(currentUserId.get(principal), orderId);
    }

    @DeleteMapping("/api/shipments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all Shipment belonging to the current user")
    public void deleteAll(Principal principal) {
        shipmentService.deleteAllShipments(currentUserId.get(principal));
    }

    @PutMapping("/api/orders/{orderId}/shipment")
    @Operation(summary = "Update Shipment", description = "Replace mutable fields of an owned resource; does not create a missing resource.")
    public ShipmentDto update(@PathVariable UUID orderId, @Valid @RequestBody UpdateShipmentRequest request,
            Principal principal) {
        return shipmentService.updateShipment(currentUserId.get(principal), orderId, request);
    }
}
