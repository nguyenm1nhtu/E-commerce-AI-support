package com.ecommerce.aicommercesupport.auth.config;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

@ConfigurationProperties("app.auth.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("ai-commerce-support") String issuer,
        @DefaultValue("ai-commerce-support-api") String audience,
        @DefaultValue("15m") Duration accessTokenTtl
) {

    public JwtProperties {
        Assert.hasText(secret, "JWT_SECRET is required");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64");
        }
        Assert.isTrue(decoded.length >= 32, "JWT_SECRET must decode to at least 32 bytes");
        Assert.hasText(issuer, "JWT issuer must not be blank");
        Assert.hasText(audience, "JWT audience must not be blank");
        Assert.isTrue(accessTokenTtl != null && accessTokenTtl.getSeconds() > 0
                        && accessTokenTtl.getNano() == 0,
                "JWT access-token-ttl must be a positive whole number of seconds");
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=[REDACTED], issuer=" + issuer + ", audience=" + audience
                + ", accessTokenTtl=" + accessTokenTtl + "]";
    }
}
