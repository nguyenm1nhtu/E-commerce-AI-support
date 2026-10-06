package com.ecommerce.aicommercesupport.ticket.dto;

import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotNull TicketCategory category,
        @NotBlank @Size(max = 10000) String content
) {
}
