package com.ecommerce.aicommercesupport.ticket.service;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketMessageRequest;
import com.ecommerce.aicommercesupport.ticket.dto.TicketDto;
import com.ecommerce.aicommercesupport.ticket.entity.Ticket;
import com.ecommerce.aicommercesupport.ticket.entity.TicketMessage;
import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import com.ecommerce.aicommercesupport.ticket.repository.TicketRepository;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketWorkflowService {
    private final TicketAccessService access;
    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final Validator validator;

    @Transactional(readOnly = true)
    public Page<TicketDto> getEscalationQueue(UUID agentId, Pageable pageable) {
        access.requireRole(agentId, UserRole.SUPPORT_AGENT);
        return tickets.findByStatusAndAssignedToIsNull(TicketStatus.ESCALATED,
                TicketPages.validate(pageable, false)).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<TicketDto> getAssignedTickets(UUID agentId, TicketStatus status, Pageable pageable) {
        access.requireRole(agentId, UserRole.SUPPORT_AGENT);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket status is required");
        }
        return tickets.findByAssignedToAndStatus(agentId, status, TicketPages.validate(pageable, false))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TicketDto getAssignedTicket(UUID ticketId, UUID agentId) {
        access.requireRole(agentId, UserRole.SUPPORT_AGENT);
        return toDto(access.accessible(ticketId, agentId, false));
    }

    @Transactional(readOnly = true)
    public TicketDto getTicketForActor(UUID ticketId, UUID actorId) {
        return toDto(access.accessible(ticketId, actorId, false));
    }

    public TicketDto startAiHandling(UUID ticketId, UUID userId) {
        access.requireRole(userId, UserRole.CUSTOMER);
        var ticket = access.accessible(ticketId, userId, true);
        requireStatus(ticket, TicketStatus.OPEN);
        ticket.setStatus(TicketStatus.AI_HANDLING);
        return save(ticket);
    }

    /** Internal orchestration entry point; never expose AI sender or this method directly to clients. */
    public TicketDto recordAiReply(UUID ticketId, UUID userId, CreateTicketMessageRequest request) {
        access.requireRole(userId, UserRole.CUSTOMER);
        validateMessage(request);
        var ticket = access.accessible(ticketId, userId, true);
        requireStatus(ticket, TicketStatus.AI_HANDLING);
        messages.saveAndFlush(new TicketMessage(ticket, TicketSenderType.AI, request.content(), Instant.now()));
        ticket.setStatus(TicketStatus.WAITING_CUSTOMER);
        return save(ticket);
    }

    public TicketDto escalateTicket(UUID ticketId, UUID userId) {
        access.requireRole(userId, UserRole.CUSTOMER);
        var ticket = access.accessible(ticketId, userId, true);
        if (ticket.getAssignedTo() != null) {
            throw conflict("Ticket is already assigned");
        }
        requireStatus(ticket, TicketStatus.OPEN, TicketStatus.AI_HANDLING, TicketStatus.WAITING_CUSTOMER);
        ticket.setStatus(TicketStatus.ESCALATED);
        return save(ticket);
    }

    public TicketDto claimTicket(UUID ticketId, UUID agentId) {
        access.requireRole(agentId, UserRole.SUPPORT_AGENT);
        var ticket = access.load(ticketId, true);
        requireStatus(ticket, TicketStatus.ESCALATED);
        if (ticket.getAssignedTo() != null) {
            throw conflict("Ticket is already assigned");
        }
        ticket.setAssignedTo(agentId);
        ticket.setStatus(TicketStatus.HUMAN_HANDLING);
        return save(ticket);
    }

    public TicketDto resolveTicket(UUID ticketId, UUID actorId) {
        var ticket = access.accessible(ticketId, actorId, true);
        if (actorId.equals(ticket.getUserId())) {
            requireStatus(ticket, TicketStatus.WAITING_CUSTOMER);
        } else {
            requireStatus(ticket, TicketStatus.HUMAN_HANDLING, TicketStatus.WAITING_CUSTOMER);
        }
        ticket.setStatus(TicketStatus.RESOLVED);
        return save(ticket);
    }

    public TicketDto updatePriority(UUID ticketId, UUID agentId, TicketPriority priority) {
        access.requireRole(agentId, UserRole.SUPPORT_AGENT);
        if (priority == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket priority is required");
        }
        var ticket = access.accessible(ticketId, agentId, true);
        requireStatus(ticket, TicketStatus.HUMAN_HANDLING, TicketStatus.WAITING_CUSTOMER);
        ticket.setPriority(priority);
        return save(ticket);
    }

    private void validateMessage(CreateTicketMessageRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must contain 1 to 10000 characters");
        }
    }

    static void requireStatus(Ticket ticket, TicketStatus... allowed) {
        for (var status : allowed) {
            if (ticket.getStatus() == status) {
                return;
            }
        }
        throw conflict("Operation is not allowed in the current ticket status");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private TicketDto save(Ticket ticket) {
        return toDto(tickets.saveAndFlush(ticket));
    }

    private TicketDto toDto(Ticket ticket) {
        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getCategory(), ticket.getStatus(),
                ticket.getPriority(), ticket.getAssignedTo(), ticket.getCreatedAt());
    }
}
