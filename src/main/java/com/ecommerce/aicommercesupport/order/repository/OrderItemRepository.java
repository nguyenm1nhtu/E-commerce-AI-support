package com.ecommerce.aicommercesupport.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrder_Id(UUID orderId);

    Optional<OrderItem> findByIdAndOrder_Id(UUID id, UUID orderId);
}
