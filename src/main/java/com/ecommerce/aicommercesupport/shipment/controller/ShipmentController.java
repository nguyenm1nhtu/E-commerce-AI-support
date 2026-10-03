package com.ecommerce.aicommercesupport.shipment.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Shipments")
@RequestMapping("/api/orders/{orderId}/shipment")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final OrderService orderService;
    private final CurrentUserId currentUserId;

    @GetMapping
    public ShipmentDto getShipment(@PathVariable UUID orderId, Principal principal) {
        orderService.requireOrderOwnership(orderId, currentUserId.get(principal));
        return shipmentService.getShipmentByOrderId(orderId);
    }
}
