package com.ecommerce.aicommercesupport.auth.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ecommerce.aicommercesupport.common.config.RedisProperties;
import com.ecommerce.aicommercesupport.common.redis.RedisKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=${redis.integration.port:6379}",
        "app.redis.namespace=auth-repository-test-${random.uuid}"
})
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "redis.integration.port", matches = "[0-9]+")
class RefreshTokenRepositoryTests {

    @Autowired private RefreshTokenRepository repository;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RedisKeys keys;
    @Autowired private RedisProperties properties;

    @AfterEach
    void cleanOnlyThisTestNamespace() {
        var testKeys = new ArrayList<String>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(properties.namespace() + ":auth:*").build())) {
            cursor.forEachRemaining(testKeys::add);
        }
        if (!testKeys.isEmpty()) {
            redis.delete(testKeys);
        }
    }

    @Test
    void rotatesAtomicallyWithAbsoluteExpiryAndRevokesFamilyOnReplay() {
        var user = UUID.randomUUID();
        var original = "a".repeat(64);
        var replacement = "b".repeat(64);
        repository.create(original, user, Duration.ofMinutes(5));
        var family = keys.refreshSession(UUID.fromString(redis.opsForValue().get(keys.refreshToken(original))));
        assertThat(redis.opsForHash().entries(family)).containsEntry("userId", user.toString())
                .containsEntry("currentHash", original);
        assertThat(redis.getExpire(keys.refreshToken(original), TimeUnit.MILLISECONDS)).isBetween(1L, 300000L);
        redis.expire(family, Duration.ofSeconds(45));
        var rotated = repository.rotate(original, replacement).orElseThrow();
        assertThat(rotated.userId()).isEqualTo(user);
        assertThat(rotated.remainingLifetime()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(45));
        assertThat(redis.getExpire(keys.refreshToken(replacement), TimeUnit.MILLISECONDS)).isBetween(1L, 45000L);
        assertThat(redis.hasKey(keys.refreshToken(original))).isTrue();
        assertThat(repository.rotate(original, "c".repeat(64))).isEmpty();
        assertThat(redis.hasKey(family)).isFalse();
        assertThat(repository.rotate(replacement, "d".repeat(64))).isEmpty();
    }

    @Test
    void logoutUsingSpentTokenRevokesItsFamilyButPreservesOtherSessionsAndCache() {
        var user = UUID.randomUUID();
        repository.create("a".repeat(64), user, Duration.ofMinutes(1));
        repository.create("c".repeat(64), user, Duration.ofMinutes(1));
        var cacheKey = properties.namespace() + ":cache:example::1";
        redis.opsForValue().set(cacheKey, "unrelated", Duration.ofMinutes(1));
        try {
            assertThat(repository.rotate("a".repeat(64), "b".repeat(64))).isPresent();
            repository.revoke("a".repeat(64));
            repository.revoke("a".repeat(64));
            assertThat(repository.rotate("b".repeat(64), "d".repeat(64))).isEmpty();
            assertThat(repository.rotate("c".repeat(64), "e".repeat(64))).isPresent();
            assertThat(redis.opsForValue().get(cacheKey)).isEqualTo("unrelated");
        } finally {
            redis.delete(cacheKey);
        }
    }

    @Test
    void expiredOrMissingSessionFailsClosed() {
        repository.create("a".repeat(64), UUID.randomUUID(), Duration.ofMinutes(1));
        var family = UUID.fromString(redis.opsForValue().get(keys.refreshToken("a".repeat(64))));
        redis.expire(keys.refreshSession(family), Duration.ZERO);
        assertThat(repository.rotate("a".repeat(64), "b".repeat(64))).isEmpty();
        redis.expire(keys.refreshToken("a".repeat(64)), Duration.ZERO);
        assertThat(repository.rotate("a".repeat(64), "b".repeat(64))).isEmpty();
    }

    @Test
    void concurrentRefreshCanSucceedOnlyOnceAndReplayRevokesTheWinner() throws Exception {
        repository.create("a".repeat(64), UUID.randomUUID(), Duration.ofMinutes(1));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return repository.rotate("a".repeat(64), "b".repeat(64)); });
            var second = executor.submit(() -> { start.await(); return repository.rotate("a".repeat(64), "c".repeat(64)); });
            start.countDown();
            var successes = (first.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0);
            assertThat(successes).isEqualTo(1);
        }
        assertThat(repository.rotate("b".repeat(64), "d".repeat(64))).isEmpty();
        assertThat(repository.rotate("c".repeat(64), "e".repeat(64))).isEmpty();
    }
}
