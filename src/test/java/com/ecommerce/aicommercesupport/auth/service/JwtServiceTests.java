package com.ecommerce.aicommercesupport.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.auth.config.JwtConfiguration;
import com.ecommerce.aicommercesupport.auth.config.JwtProperties;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
    private final JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[64]),
            "test-issuer", "test-api", Duration.ofMinutes(15));
    private final JwtConfiguration configuration = new JwtConfiguration();
    private final JwtEncoder encoder = configuration.jwtEncoder(properties);
    private final JwtService service = new JwtService(encoder, properties, clock);

    @Test
    void issuesSignedAccessTokenWithUuidRoleAudienceLifetimeAndUniqueId() {
        var id = UUID.randomUUID();
        var issued = service.generateAccessToken(id, UserRole.CUSTOMER);
        var jwt = configuration.jwtDecoder(properties, clock).decode(issued.getTokenValue());
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getAudience()).containsExactly("test-api");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("access");
        assertThat(jwt.getIssuedAt()).isEqualTo(clock.instant());
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(900));
        assertThat(jwt.getId()).isNotEqualTo(service.generateAccessToken(id, UserRole.CUSTOMER).getId());
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "email", "refreshToken");
        assertThat(configuration.jwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting("authority").contains("ROLE_CUSTOMER").doesNotContain("ROLE_SUPPORT_AGENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "subject", "role", "roleType", "tokenUse", "missingExp",
            "missingIat", "missingNbf", "expired", "futureNbf", "futureIat"})
    void rejectsSignedTokensWithInvalidClaims(String invalidClaim) {
        var claims = validClaims();
        switch (invalidClaim) {
            case "issuer" -> claims.issuer("another-issuer");
            case "audience" -> claims.audience(List.of("another-api"));
            case "subject" -> claims.subject("not-a-uuid");
            case "role" -> claims.claim("role", "ADMIN");
            case "roleType" -> claims.claim("role", List.of("CUSTOMER"));
            case "tokenUse" -> claims.claim("token_use", "refresh");
            case "missingExp" -> claims.claims(map -> map.remove("exp"));
            case "missingIat" -> claims.claims(map -> map.remove("iat"));
            case "missingNbf" -> claims.claims(map -> map.remove("nbf"));
            case "expired" -> claims.issuedAt(clock.instant().minusSeconds(1000))
                    .notBefore(clock.instant().minusSeconds(1000)).expiresAt(clock.instant().minusSeconds(60));
            case "futureNbf" -> claims.notBefore(clock.instant().plusSeconds(120));
            case "futureIat" -> claims.issuedAt(clock.instant().plusSeconds(120));
            default -> throw new IllegalArgumentException(invalidClaim);
        }
        var token = encode(claims.build(), MacAlgorithm.HS256);
        assertThatThrownBy(() -> configuration.jwtDecoder(properties, clock).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongSignatureAndAlgorithm() {
        var otherProperties = new JwtProperties(Base64.getEncoder().encodeToString("different-secret".repeat(4).getBytes()),
                properties.issuer(), properties.audience(), properties.accessTokenTtl());
        var token = new JwtService(configuration.jwtEncoder(otherProperties), otherProperties, clock)
                .generateAccessToken(UUID.randomUUID(), UserRole.CUSTOMER).getTokenValue();
        var decoder = configuration.jwtDecoder(properties, clock);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(encode(validClaims().build(), MacAlgorithm.HS512)))
                .isInstanceOf(JwtException.class);
    }

    private JwtClaimsSet.Builder validClaims() {
        return JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(UUID.randomUUID().toString()).issuedAt(clock.instant()).notBefore(clock.instant())
                .expiresAt(clock.instant().plusSeconds(900)).claim("role", "CUSTOMER").claim("token_use", "access");
    }

    private String encode(JwtClaimsSet claims, MacAlgorithm algorithm) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).type("JWT").build(), claims)).getTokenValue();
    }
}
