package com.ecommerce.aicommercesupport.ticket.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketRequest;
import com.ecommerce.aicommercesupport.ticket.dto.TicketDto;
import com.ecommerce.aicommercesupport.ticket.entity.Ticket;
import com.ecommerce.aicommercesupport.ticket.entity.TicketMessage;
import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import com.ecommerce.aicommercesupport.ticket.repository.TicketRepository;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final Validator validator;

    public TicketDto createTicket(UUID userId, CreateTicketRequest createTicketRequest) {
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        validateRequest(createTicketRequest);

        try {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            if (user.getRole() != UserRole.CUSTOMER) {
                throw new AccessDeniedException("Only customers can create tickets");
            }

            var now = Instant.now();
            var ticket = new Ticket(
                    userId,
                    createTicketRequest.category(),
                    TicketStatus.OPEN,
                    TicketPriority.NORMAL,
                    null,
                    now);

            var savedTicket = ticketRepository.saveAndFlush(ticket);

            var ticketMessage = new TicketMessage(
                    savedTicket,
                    TicketSenderType.CUSTOMER,
                    createTicketRequest.content(),
                    now);
            ticketMessageRepository.saveAndFlush(ticketMessage);

            return toDto(savedTicket);
        } catch (DataAccessResourceFailureException | QueryTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ticket storage is temporarily unavailable", exception);
        } catch (DataIntegrityViolationException exception) {
            var detail = exception.getMostSpecificCause().getMessage();
            if (detail != null && detail.toLowerCase(Locale.ROOT).contains("fk_tickets_user")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ticket owner changed while creating the ticket", exception);
            }
            throw exception;
        }
    }

    private void validateRequest(CreateTicketRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket request is required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            var message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted().collect(Collectors.joining("; "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }


    private TicketDto toDto(Ticket ticket) {
        return new TicketDto(ticket.getId(), ticket.getUserId(),
                ticket.getCategory(), ticket.getStatus(),
                ticket.getPriority(), ticket.getAssignedTo(), ticket.getCreatedAt());
    }
}
