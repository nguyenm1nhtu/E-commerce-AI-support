package com.ecommerce.aicommercesupport.ticket.dto;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;
import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;

public record TicketDto(
        UUID id,
        UUID userId,
        TicketCategory category,
        TicketStatus status,
        TicketPriority priority,
        UUID assignedTo,
        Instant createdAt
) {
}
