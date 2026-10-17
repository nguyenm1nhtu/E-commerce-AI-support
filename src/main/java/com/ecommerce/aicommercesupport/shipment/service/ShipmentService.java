package com.ecommerce.aicommercesupport.shipment.service;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.cache.CacheEviction;
import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.shipment.dto.CreateShipmentRequest;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;
import com.ecommerce.aicommercesupport.shipment.dto.UpdateShipmentRequest;
import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {

    private final CacheEviction cacheEviction;
    private final OrderRepository orderRepository;

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

    @Transactional
    public ShipmentDto createShipment(UUID userId, UUID orderId, CreateShipmentRequest request) {
        var order = owned(userId, orderId);
        if (shipmentRepository.findByOrder_Id(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a shipment");
        }
        var shipment = shipmentRepository.save(new Shipment(order, request.status(), request.carrier(),
                request.trackingCode(), request.deliveredAt()));
        cacheEviction.afterCommit("shipment-by-order", orderId);
        return new ShipmentDto(shipment.getId(), orderId, shipment.getStatus(), shipment.getCarrier(),
                shipment.getTrackingCode(), shipment.getDeliveredAt());
    }

    @Transactional
    public void deleteShipment(UUID userId, UUID orderId) {
        owned(userId, orderId);
        var shipment = shipmentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order: " + orderId));
        shipmentRepository.delete(shipment);
        cacheEviction.afterCommit("shipment-by-order", orderId);
    }

    @Transactional
    public void deleteAllShipments(UUID userId) {
        for (var order : orderRepository.findAllByUserIdOrderById(userId)) {
            owned(userId, order.getId());
            removeShipment(order.getId());
        }
    }

    private Order owned(UUID userId, UUID orderId) {
        return orderRepository.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void removeShipment(UUID orderId) {
        shipmentRepository.findByOrder_Id(orderId).ifPresent(shipmentRepository::delete);
        cacheEviction.afterCommit("shipment-by-order", orderId);
    }

    @Transactional
    public void deleteShipmentsForOrder(UUID userId, UUID orderId) {
        owned(userId, orderId);
        removeShipment(orderId);
    }

    @Transactional
    public ShipmentDto updateShipment(UUID userId, UUID orderId, UpdateShipmentRequest request) {
        owned(userId, orderId);
        var shipment = shipmentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order: " + orderId));
        shipment.setStatus(request.status());
        shipment.setCarrier(request.carrier());
        shipment.setTrackingCode(request.trackingCode());
        shipment.setDeliveredAt(request.deliveredAt());
        cacheEviction.afterCommit("shipment-by-order", orderId);
        return toDto(shipment);
    }
}
