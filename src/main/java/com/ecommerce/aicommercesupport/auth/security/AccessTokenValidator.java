package com.ecommerce.aicommercesupport.auth.security;

import java.time.Clock;
import java.util.UUID;

import com.ecommerce.aicommercesupport.user.entity.UserRole;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;
    private final Clock clock;

    public AccessTokenValidator(String audience, Clock clock) {
        this.audience = audience;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            var subject = jwt.getSubject();
            if (subject == null || !UUID.fromString(subject).toString().equals(subject)
                    || !(jwt.getClaims().get("role") instanceof String role)
                    || !"access".equals(jwt.getClaims().get("token_use"))
                    || jwt.getAudience() == null || !jwt.getAudience().contains(audience)
                    || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getNotBefore() == null
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30))) {
                return invalid();
            }
            UserRole.valueOf(role);
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException exception) {
            return invalid();
        }
    }

    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token claims", null));
    }
}
