package com.ecommerce.aicommercesupport.auth.service;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.auth.config.RefreshTokenCookieProperties;
import com.ecommerce.aicommercesupport.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
    @Mock private RefreshTokenRepository repository;

    private RefreshTokenService service() {
        return new RefreshTokenService(repository,
                new RefreshTokenCookieProperties("refresh_token", true, "Lax", Duration.ofDays(7)), clock);
    }

    @Test
    void storesOnlyHashWithFiniteTtlAndRedactsRawToken() {
        var userId = UUID.randomUUID();
        var issued = service().issue(userId);
        var hash = ArgumentCaptor.forClass(String.class);
        verify(repository).create(hash.capture(), eq(userId), eq(Duration.ofDays(7)));
        assertThat(issued.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(hash.getValue()).matches("[a-f0-9]{64}").doesNotContain(issued.value());
        assertThat(issued.toString()).doesNotContain(issued.value());
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
    }

    @Test
    void rotatesUsingHashesAndPreservesRemainingSessionLifetime() {
        var userId = UUID.randomUUID();
        when(repository.rotate(anyString(), anyString()))
                .thenReturn(Optional.of(new RefreshTokenRepository.Rotation(userId, Duration.ofMinutes(3))));
        var issued = service().rotate("a".repeat(43));
        assertThat(issued.value()).isNotEqualTo("a".repeat(43));
        assertThat(issued.userId()).isEqualTo(userId);
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(3)));
    }

    @Test
    void rejectsMissingMalformedAndUnknownTokens() {
        assertThatThrownBy(() -> service().rotate(null)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service().rotate("invalid")).isInstanceOf(BadCredentialsException.class);
        service().revoke(null);
        service().revoke("invalid");
        verifyNoInteractions(repository);
        when(repository.rotate(anyString(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().rotate("a".repeat(43))).isInstanceOf(BadCredentialsException.class);
    }
}
