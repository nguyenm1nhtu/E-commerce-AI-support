package com.ecommerce.aicommercesupport.common.redis;

import java.time.Duration;

import com.ecommerce.aicommercesupport.common.config.RedisProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RedisKeysTests {

    private final RedisKeys keys = new RedisKeys(new RedisProperties("test-app", Duration.ofMinutes(10)));

    @Test
    void putsRefreshTokenHashesOutsideCacheNamespace() {
        assertThat(keys.refreshToken("a".repeat(64))).isEqualTo("test-app:auth:refresh:" + "a".repeat(64));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"raw-refresh-token", "abcdef", " ", "*"})
    void rejectsRawTokensAndInvalidHashes(String hash) {
        assertThatIllegalArgumentException().isThrownBy(() -> keys.refreshToken(hash));
    }
}
