package com.ecommerce.aicommercesupport.common.redis;

import java.util.UUID;

import com.ecommerce.aicommercesupport.common.config.RedisProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class RedisKeys {

    private final String namespace;

    public RedisKeys(RedisProperties properties) {
        this.namespace = properties.namespace();
    }

    public String refreshToken(String tokenHash) {
        Assert.isTrue(tokenHash != null && tokenHash.matches("[a-f0-9]{64}"),
                "Refresh token hash must be a lowercase SHA-256 hex digest");
        return namespace + ":auth:refresh:" + tokenHash;
    }

    public String refreshSession(UUID familyId) {
        Assert.notNull(familyId, "Refresh session ID is required");
        return namespace + ":auth:session:" + familyId;
    }
}
