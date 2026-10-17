package com.ecommerce.aicommercesupport.ticket.repository;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.Ticket;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findForUpdateById(UUID id);

    Page<Ticket> findByUserId(UUID userId, Pageable pageable);

    Page<Ticket> findByUserIdAndStatus(UUID userId, TicketStatus status, Pageable pageable);

    Optional<Ticket> findByIdAndUserId(UUID id, UUID userId);

    Page<Ticket> findByAssignedToAndStatus(UUID assignedTo, TicketStatus status, Pageable pageable);

    Page<Ticket> findByStatusAndAssignedToIsNull(TicketStatus status, Pageable pageable);
}
