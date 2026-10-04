package com.ecommerce.aicommercesupport.auth.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;

@ConfigurationProperties("app.auth.refresh-token-cookie")
public record RefreshTokenCookieProperties(
        @DefaultValue("refresh_token") String name,
        @DefaultValue("true") boolean secure,
        @DefaultValue("Lax") String sameSite,
        @DefaultValue("7d") Duration maxAge
) {

    public static final String PATH = "/api/auth";

    public RefreshTokenCookieProperties {
        Assert.hasText(name, "Refresh cookie name must not be blank");
        Assert.isTrue(Set.of("Lax", "Strict", "None").contains(sameSite),
                "Refresh cookie SameSite must be Lax, Strict or None");
        Assert.isTrue(!"None".equals(sameSite) || secure,
                "SameSite=None requires a Secure refresh cookie");
        Assert.isTrue(maxAge != null && maxAge.getSeconds() > 0 && maxAge.getNano() == 0,
                "Refresh cookie max-age must be a positive whole number of seconds");
        ResponseCookie.from(name, "").build();
    }
}
