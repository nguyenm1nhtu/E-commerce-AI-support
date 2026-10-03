package com.ecommerce.aicommercesupport.common.dto;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors
) {
    public ApiErrorResponse {
        errors = Map.copyOf(errors);
    }
}
