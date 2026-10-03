package com.ecommerce.aicommercesupport.shipment.repository;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrder_Id(UUID orderId);
}
