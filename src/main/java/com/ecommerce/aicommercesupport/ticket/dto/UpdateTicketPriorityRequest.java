package com.ecommerce.aicommercesupport.ticket.dto;

import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketPriorityRequest(@NotNull TicketPriority priority) {
}
