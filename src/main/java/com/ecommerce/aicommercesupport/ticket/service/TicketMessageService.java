package com.ecommerce.aicommercesupport.ticket.service;

import java.util.UUID;
import java.time.Instant;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketMessageRequest;
import com.ecommerce.aicommercesupport.ticket.dto.TicketMessageDto;
import com.ecommerce.aicommercesupport.ticket.entity.TicketMessage;
import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.validation.Validator;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketMessageService {

    private final TicketService ticketService;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketAccessService access;
    private final Validator validator;

    @Transactional
    public TicketMessageDto addMessage(UUID ticketId, UUID actorId, CreateTicketMessageRequest request) {
        var ticket = access.accessible(ticketId, actorId, true);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must contain 1 to 10000 characters");
        }
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved tickets cannot receive messages");
        }
        var customer = actorId.equals(ticket.getUserId());
        if (!customer) {
            TicketWorkflowService.requireStatus(ticket, TicketStatus.HUMAN_HANDLING, TicketStatus.WAITING_CUSTOMER);
        }
        var sender = customer ? TicketSenderType.CUSTOMER : TicketSenderType.SUPPORT_AGENT;
        var message = ticketMessageRepository.saveAndFlush(
                new TicketMessage(ticket, sender, request.content(), Instant.now()));
        if (!customer) {
            ticket.setStatus(TicketStatus.WAITING_CUSTOMER);
        } else if (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.WAITING_CUSTOMER) {
            ticket.setStatus(ticket.getAssignedTo() == null ? TicketStatus.AI_HANDLING : TicketStatus.HUMAN_HANDLING);
        }
        return toDto(message);
    }

    public Page<TicketMessageDto> getMessagesForSupport(UUID ticketId, UUID agentId, Pageable pageable) {
        access.requireRole(agentId, UserRole.SUPPORT_AGENT);
        access.accessible(ticketId, agentId, false);
        return ticketMessageRepository.findByTicket_Id(ticketId, validatePageable(pageable)).map(this::toDto);
    }

    public Page<TicketMessageDto> getMessagesForActor(UUID ticketId, UUID actorId, Pageable pageable) {
        access.accessible(ticketId, actorId, false);
        return ticketMessageRepository.findByTicket_Id(ticketId, validatePageable(pageable)).map(this::toDto);
    }

    public TicketMessageDto getMessage(UUID ticketId, UUID messageId, UUID actorId) {
        access.accessible(ticketId, actorId, false);
        if (messageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message ID is required");
        }
        return ticketMessageRepository.findByIdAndTicket_Id(messageId, ticketId).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
    }

    public Page<TicketMessageDto> getMessagesByTicketIdAndUserId(UUID ticketId, UUID userId, Pageable pageable) {
        ticketService.requireTicketOwnership(ticketId, userId);
        var requestedPage = validatePageable(pageable);
        try {
            return ticketMessageRepository.findByTicket_IdAndTicket_UserId(ticketId, userId, requestedPage)
                    .map(this::toDto);
        } catch (DataAccessResourceFailureException | QueryTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ticket storage is temporarily unavailable", exception);
        }
    }

    private Pageable validatePageable(Pageable pageable) {
        return TicketPages.validate(pageable, true);
    }

    private TicketMessageDto toDto(TicketMessage message) {
        return new TicketMessageDto(message.getId(), message.getTicket().getId(), message.getSenderType(),
                message.getContent(), message.getCreatedAt());
    }
}
