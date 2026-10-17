package com.ecommerce.aicommercesupport.shipment.dto;

import java.time.Instant;

import com.ecommerce.aicommercesupport.shipment.entity.Carrier;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateShipmentRequest(@NotNull ShipmentStatus status, Carrier carrier,
        @Size(max = 255) String trackingCode, Instant deliveredAt) {
}
