package com.ecommerce.aicommercesupport.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.ecommerce.aicommercesupport.auth.config.RefreshTokenCookieProperties;
import com.ecommerce.aicommercesupport.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenCookieProperties properties;
    private final Clock clock;

    public IssuedToken issue(UUID userId) {
        var token = randomToken();
        var expiresAt = clock.instant().plus(properties.maxAge());
        repository.create(hash(token), userId, properties.maxAge());
        return new IssuedToken(token, userId, expiresAt);
    }

    public IssuedToken rotate(String token) {
        if (!isWellFormed(token)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        var replacement = randomToken();
        var startedAt = clock.instant();
        var rotation = repository.rotate(hash(token), hash(replacement))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return new IssuedToken(replacement, rotation.userId(), startedAt.plus(rotation.remainingLifetime()));
    }

    public void revoke(String token) {
        if (isWellFormed(token)) {
            repository.revoke(hash(token));
        }
    }

    private boolean isWellFormed(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String randomToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedToken(String value, UUID userId, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[value=[REDACTED], userId=" + userId + ", expiresAt=" + expiresAt + "]";
        }
    }
}
