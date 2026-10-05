package com.ecommerce.aicommercesupport.auth.dto;

import java.util.UUID;

import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        UserRole role
) {

    @Override
    public String toString() {
        return "AuthResponse[accessToken=[REDACTED], tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", userId=" + userId
                + ", email=" + email + ", firstName=" + firstName
                + ", lastName=" + lastName + ", role=" + role + "]";
    }
}
