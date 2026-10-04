package com.ecommerce.aicommercesupport.auth.service;

import java.time.Duration;
import java.util.Optional;

import com.ecommerce.aicommercesupport.auth.config.RefreshTokenCookieProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private final RefreshTokenCookieProperties properties;

    public void write(HttpServletResponse response, String token) {
        write(response, token, properties.maxAge());
    }

    public void write(HttpServletResponse response, String token, Duration remainingLifetime) {
        Assert.hasText(token, "Refresh token must not be blank");
        Assert.isTrue(remainingLifetime != null && remainingLifetime.getSeconds() > 0,
                "Refresh token must have at least one second remaining");
        var maxAge = remainingLifetime.compareTo(properties.maxAge()) < 0
                ? remainingLifetime : properties.maxAge();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, maxAge).toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String token = null;
        boolean found = false;
        for (var cookie : cookies) {
            if (properties.name().equals(cookie.getName())) {
                if (found) {
                    return Optional.empty();
                }
                found = true;
                token = cookie.getValue();
            }
        }
        return Optional.ofNullable(token).filter(value -> !value.isBlank());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(RefreshTokenCookieProperties.PATH)
                .maxAge(maxAge)
                .build();
    }
}
