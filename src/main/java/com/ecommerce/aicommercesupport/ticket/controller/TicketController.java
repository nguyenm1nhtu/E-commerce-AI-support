package com.ecommerce.aicommercesupport.ticket.controller;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketRequest;
import com.ecommerce.aicommercesupport.ticket.dto.TicketDto;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.ticket.service.TicketService;
import com.ecommerce.aicommercesupport.ticket.service.TicketWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService tickets;
    private final TicketWorkflowService workflow;
    private final CurrentUserId currentUserId;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer ticket and its first message")
    public ResponseEntity<TicketDto> create(@Valid @RequestBody CreateTicketRequest request, Principal principal) {
        var ticket = tickets.createTicket(currentUserId.get(principal), request);
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.id())).body(ticket);
    }

    @GetMapping
    @Operation(summary = "List the current customer's tickets, optionally filtered by status")
    public PagedModel<TicketDto> list(Principal principal,
            @RequestParam(required = false) TicketStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        return new PagedModel<>(tickets.getTicketsByUserId(currentUserId.get(principal), status, pageable));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Read a ticket as its customer or assigned support agent")
    public TicketDto detail(@PathVariable UUID ticketId, Principal principal) {
        return workflow.getTicketForActor(ticketId, currentUserId.get(principal));
    }

    @PostMapping("/{ticketId}/escalate")
    @Operation(summary = "Request human support for the current customer's unassigned ticket")
    public TicketDto escalate(@PathVariable UUID ticketId, Principal principal) {
        return workflow.escalateTicket(ticketId, currentUserId.get(principal));
    }

    @PostMapping("/{ticketId}/resolve")
    @Operation(summary = "Resolve a ticket as its customer or assigned support agent")
    public TicketDto resolve(@PathVariable UUID ticketId, Principal principal) {
        return workflow.resolveTicket(ticketId, currentUserId.get(principal));
    }
}
