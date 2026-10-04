package com.ecommerce.aicommercesupport.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

@ConfigurationProperties("app.redis")
public record RedisProperties(
        @DefaultValue("ai-commerce-support") String namespace,
        @DefaultValue("10m") Duration cacheTtl
) {

    public RedisProperties {
        Assert.isTrue(namespace != null && namespace.matches("[a-zA-Z0-9_-]+"),
                "Redis namespace must contain only letters, digits, underscores and hyphens");
        Assert.isTrue(cacheTtl != null && cacheTtl.toMillis() > 0,
                "Redis cache TTL must be at least one millisecond");
    }
}
