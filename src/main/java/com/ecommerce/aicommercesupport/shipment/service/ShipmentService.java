package com.ecommerce.aicommercesupport.shipment.service;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentDto getShipmentById(UUID id) {
        return shipmentRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + id));
    }

    @Cacheable(cacheNames = "shipment-by-order", key = "#orderId")
    public ShipmentDto getShipmentByOrderId(UUID orderId) {
        return findShipmentByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order: " + orderId));
    }

    public Optional<ShipmentDto> findShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrder_Id(orderId).map(this::toDto);
    }

    private ShipmentDto toDto(Shipment shipment) {
        return new ShipmentDto(shipment.getId(), shipment.getOrder().getId(), shipment.getStatus(),
                shipment.getCarrier(), shipment.getTrackingCode(), shipment.getDeliveredAt());
    }
}
