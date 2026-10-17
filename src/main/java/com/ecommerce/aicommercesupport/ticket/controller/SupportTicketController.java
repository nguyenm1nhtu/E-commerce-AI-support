package com.ecommerce.aicommercesupport.ticket.controller;

import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.ticket.dto.TicketDto;
import com.ecommerce.aicommercesupport.ticket.dto.UpdateTicketPriorityRequest;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/tickets")
@Tag(name = "Support tickets")
@RequiredArgsConstructor
public class SupportTicketController {
    private final TicketWorkflowService workflow;
    private final CurrentUserId currentUserId;

    @GetMapping
    @Operation(summary = "List tickets assigned to the current support agent")
    public PagedModel<TicketDto> assigned(Principal principal,
            @RequestParam(defaultValue = "HUMAN_HANDLING") TicketStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        return new PagedModel<>(workflow.getAssignedTickets(currentUserId.get(principal), status, pageable));
    }

    @GetMapping("/escalations")
    @Operation(summary = "List unassigned escalated tickets for support agents")
    public PagedModel<TicketDto> escalations(Principal principal,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        return new PagedModel<>(workflow.getEscalationQueue(currentUserId.get(principal), pageable));
    }

    @PostMapping("/{ticketId}/claim")
    @Operation(summary = "Claim an escalated ticket for the current support agent")
    public TicketDto claim(@PathVariable UUID ticketId, Principal principal) {
        return workflow.claimTicket(ticketId, currentUserId.get(principal));
    }

    @PatchMapping("/{ticketId}/priority")
    @Operation(summary = "Change priority of a ticket assigned to the current support agent")
    public TicketDto priority(@PathVariable UUID ticketId,
            @Valid @RequestBody UpdateTicketPriorityRequest request, Principal principal) {
        return workflow.updatePriority(ticketId, currentUserId.get(principal), request.priority());
    }
}
