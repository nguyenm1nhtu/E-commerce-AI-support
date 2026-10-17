package com.ecommerce.aicommercesupport.shipment.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.shipment.dto.CreateShipmentRequest;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.order.service.CommerceFixtureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(dev | test) & !prod")
@Tag(name = "Test data - Shipment", description = "Dev/test only. All operations are scoped to the authenticated user's orders.")
@RequiredArgsConstructor
public class ShipmentFixtureController {

    private final CommerceFixtureService fixtures;
    private final CurrentUserId currentUserId;

    @PostMapping("/api/orders/{orderId}/shipment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Shipment test data",
            responses = @ApiResponse(responseCode = "409", description = "Order already has a shipment"))
    public ShipmentDto create(@PathVariable UUID orderId, @Valid @RequestBody CreateShipmentRequest request, Principal principal) {
        return fixtures.createShipment(currentUserId.get(principal), orderId, request);
    }

    @DeleteMapping("/api/orders/{orderId}/shipment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete owned Shipment test data")
    public void delete(@PathVariable UUID orderId, Principal principal) {
        fixtures.deleteShipment(currentUserId.get(principal), orderId);
    }

    @DeleteMapping("/api/shipments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete all Shipment test data belonging to the current user")
    public void deleteAll(Principal principal) {
        fixtures.deleteAllShipments(currentUserId.get(principal));
    }
}
