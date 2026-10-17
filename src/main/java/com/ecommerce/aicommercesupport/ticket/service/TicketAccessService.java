package com.ecommerce.aicommercesupport.ticket.service;

import java.util.UUID;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.ticket.entity.Ticket;
import com.ecommerce.aicommercesupport.ticket.repository.TicketRepository;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Caller supplies an identity obtained from authentication, never a request body. */
@Service
@RequiredArgsConstructor
class TicketAccessService {
    private final UserRepository users;
    private final TicketRepository tickets;

    UserRole role(UUID actorId) {
        if (actorId == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return users.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorId)).getRole();
    }

    void requireRole(UUID actorId, UserRole required) {
        if (role(actorId) != required) {
            throw new AccessDeniedException("Ticket operation is not permitted");
        }
    }

    Ticket load(UUID ticketId, boolean lock) {
        if (ticketId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket ID is required");
        }
        return (lock ? tickets.findForUpdateById(ticketId) : tickets.findById(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
    }

    Ticket accessible(UUID ticketId, UUID actorId, boolean lock) {
        var role = role(actorId);
        var ticket = load(ticketId, lock);
        if (role == UserRole.CUSTOMER && actorId.equals(ticket.getUserId())) {
            return ticket;
        }
        if (role == UserRole.SUPPORT_AGENT && actorId.equals(ticket.getAssignedTo())) {
            return ticket;
        }
        throw new ResourceNotFoundException("Ticket not found: " + ticketId);
    }
}
