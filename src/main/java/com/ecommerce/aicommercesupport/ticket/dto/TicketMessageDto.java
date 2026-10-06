package com.ecommerce.aicommercesupport.ticket.dto;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;

public record TicketMessageDto(
        UUID id,
        UUID ticketId,
        TicketSenderType senderType,
        String content,
        Instant createdAt
) {
}
