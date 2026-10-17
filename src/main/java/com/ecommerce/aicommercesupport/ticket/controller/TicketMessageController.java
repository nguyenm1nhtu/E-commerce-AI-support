package com.ecommerce.aicommercesupport.ticket.controller;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketMessageRequest;
import com.ecommerce.aicommercesupport.ticket.dto.TicketMessageDto;
import com.ecommerce.aicommercesupport.ticket.service.TicketMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/tickets/{ticketId}/messages")
@Tag(name = "Ticket messages")
@RequiredArgsConstructor
public class TicketMessageController {
    private final TicketMessageService messages;
    private final CurrentUserId currentUserId;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a customer or assigned agent message",
            description = "The sender is derived from the authenticated user. No LLM is invoked yet.")
    public ResponseEntity<TicketMessageDto> create(@PathVariable UUID ticketId,
            @Valid @RequestBody CreateTicketMessageRequest request, Principal principal) {
        var message = messages.addMessage(ticketId, currentUserId.get(principal), request);
        return ResponseEntity.created(URI.create("/api/tickets/" + ticketId + "/messages/" + message.id())).body(message);
    }

    @GetMapping
    @Operation(summary = "Read conversation history as the customer or assigned agent")
    public PagedModel<TicketMessageDto> list(@PathVariable UUID ticketId, Principal principal,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}) @ParameterObject Pageable pageable) {
        return new PagedModel<>(messages.getMessagesForActor(ticketId, currentUserId.get(principal), pageable));
    }

    @GetMapping("/{messageId}")
    public TicketMessageDto detail(@PathVariable UUID ticketId, @PathVariable UUID messageId, Principal principal) {
        return messages.getMessage(ticketId, messageId, currentUserId.get(principal));
    }
}
