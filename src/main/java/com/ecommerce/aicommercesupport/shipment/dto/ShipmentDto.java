package com.ecommerce.aicommercesupport.shipment.dto;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.shipment.entity.Carrier;
import com.ecommerce.aicommercesupport.shipment.entity.ShipmentStatus;

public record ShipmentDto(
        UUID id,
        UUID orderId,
        ShipmentStatus status,
        Carrier carrier,
        String trackingCode,
        Instant deliveredAt
) {
}
