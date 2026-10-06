package com.ecommerce.aicommercesupport.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import com.ecommerce.aicommercesupport.auth.dto.AuthResponse;
import com.ecommerce.aicommercesupport.auth.dto.LoginRequest;
import com.ecommerce.aicommercesupport.auth.dto.RegisterRequest;
import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt,
            RefreshTokenService refreshTokens, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.dummyPasswordHash = passwords.encode("dummy-password-for-unknown-user");
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        var email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user;
        try {
            user = users.saveAndFlush(new User(email, passwords.encode(request.password()), UserRole.CUSTOMER,
                    request.firstName().strip(), request.lastName().strip()));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause().getMessage() != null
                    && exception.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT).contains("uq_users_email")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
            }
            throw exception;
        }
        return newSession(user);
    }

    public AuthSession login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid email or password");
        }
        var user = users.findByEmailIgnoreCase(normalizeEmail(request.email()));
        var matches = matchesPassword(request.password(), user.map(User::getPasswordHash).orElse(dummyPasswordHash));
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return newSession(user.get());
    }

    public AuthSession refresh(String token) {
        var rotated = refreshTokens.rotate(token);
        try {
            var user = users.findById(rotated.userId())
                    .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
            return new AuthSession(response(user), rotated);
        } catch (RuntimeException exception) {
            refreshTokens.revoke(rotated.value());
            throw exception;
        }
    }

    public void logout(String token) {
        refreshTokens.revoke(token);
    }

    private AuthSession newSession(User user) {
        var response = response(user);
        return new AuthSession(response, refreshTokens.issue(user.getId()));
    }

    private AuthResponse response(User user) {
        var accessToken = jwt.generateAccessToken(user.getId(), user.getRole());
        return new AuthResponse(accessToken.getTokenValue(), "Bearer",
                Math.max(0, Duration.between(clock.instant(), accessToken.getExpiresAt()).getSeconds()),
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole());
    }

    private boolean matchesPassword(String password, String hash) {
        try {
            return passwords.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            // Legacy/unsupported stored hashes must not expose account details or become a 500 response.
            passwords.matches(password, dummyPasswordHash);
            return false;
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public record AuthSession(AuthResponse response, RefreshTokenService.IssuedToken refreshToken) {
    }
}
