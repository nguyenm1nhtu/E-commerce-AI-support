package com.ecommerce.aicommercesupport.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 64) String category,
        @NotBlank @Size(max = 10000) String content
) {
}
