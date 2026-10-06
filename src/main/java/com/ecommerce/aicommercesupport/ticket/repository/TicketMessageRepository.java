package com.ecommerce.aicommercesupport.ticket.repository;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.TicketMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    Page<TicketMessage> findByTicket_Id(UUID ticketId, Pageable pageable);

    Page<TicketMessage> findByTicket_IdAndTicket_UserId(UUID ticketId, UUID userId, Pageable pageable);

    Optional<TicketMessage> findByIdAndTicket_Id(UUID id, UUID ticketId);
}
