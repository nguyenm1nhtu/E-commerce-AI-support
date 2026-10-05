package com.ecommerce.aicommercesupport.auth.controller;

import java.time.Clock;
import java.time.Duration;

import com.ecommerce.aicommercesupport.auth.dto.AuthResponse;
import com.ecommerce.aicommercesupport.auth.dto.CsrfResponse;
import com.ecommerce.aicommercesupport.auth.dto.LoginRequest;
import com.ecommerce.aicommercesupport.auth.dto.RegisterRequest;
import com.ecommerce.aicommercesupport.auth.service.AuthService;
import com.ecommerce.aicommercesupport.auth.service.RefreshTokenCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@SecurityRequirements
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final RefreshTokenCookieService cookies;
    private final Clock clock;

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token; send its token in headerName with the CSRF cookie on auth POST requests")
    public CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer and issue an access token and HttpOnly refresh cookie")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return writeSession(auth.register(request), response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email/password and issue an access token and HttpOnly refresh cookie")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return writeSession(auth.login(request), response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and issue a new access token; no request body")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return writeSession(auth.refresh(cookies.read(request).orElse(null)), response);
        } catch (AuthenticationException exception) {
            cookies.clear(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current refresh session and clear its cookie; access JWTs expire normally")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(cookies.read(request).orElse(null));
        cookies.clear(response);
    }

    private AuthResponse writeSession(AuthService.AuthSession session, HttpServletResponse response) {
        var remaining = Duration.between(clock.instant(), session.refreshToken().expiresAt());
        if (remaining.getSeconds() < 1) {
            auth.logout(session.refreshToken().value());
            throw new BadCredentialsException("Refresh session expired before the response was issued");
        }
        cookies.write(response, session.refreshToken().value(), remaining);
        return session.response();
    }
}
