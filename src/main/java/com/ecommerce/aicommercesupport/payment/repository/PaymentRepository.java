package com.ecommerce.aicommercesupport.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrder_Id(UUID orderId);
}
