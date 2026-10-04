package com.ecommerce.aicommercesupport.auth.dto;

import java.util.UUID;

import com.ecommerce.aicommercesupport.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResponseTests {

    @Test
    void hidesAccessTokenInToStringButPreservesItForTheResponse() {
        var response = new AuthResponse("secret-access-token", "Bearer", 900,
                UUID.randomUUID(), "customer@example.com", "Minh Tú", "Nguyễn", UserRole.CUSTOMER);

        assertThat(response.toString()).doesNotContain("secret-access-token", "refreshToken", "refreshExpiresIn")
                .contains("accessToken=[REDACTED]");
        assertThat(response.accessToken()).isEqualTo("secret-access-token");
    }

    @Test
    void serializesOnlyAccessTokenAndPublicUserInformation() {
        var userId = UUID.randomUUID();
        var response = new AuthResponse("secret-access-token", "Bearer", 900,
                userId, "customer@example.com", "Minh Tu", "Nguyen", UserRole.CUSTOMER);
        var mapper = JsonMapper.builder().build();

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.has("refreshToken")).isFalse();
        assertThat(json.has("refreshExpiresIn")).isFalse();
        assertThat(json).isEqualTo(mapper.readTree("""
                {
                  "accessToken": "secret-access-token",
                  "tokenType": "Bearer",
                  "expiresIn": 900,
                  "userId": "%s",
                  "email": "customer@example.com",
                  "firstName": "Minh Tu",
                  "lastName": "Nguyen",
                  "role": "CUSTOMER"
                }
                """.formatted(userId)));
    }
}
