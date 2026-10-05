package com.ecommerce.aicommercesupport.auth.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.common.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Stores only token hashes and session metadata, independently of Spring Cache. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final DefaultRedisScript<Long> CREATE = script("create-session.lua", Long.class);
    private static final DefaultRedisScript<String> ROTATE = script("rotate-session.lua", String.class);

    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    private static <T> DefaultRedisScript<T> script(String name, Class<T> resultType) {
        var script = new DefaultRedisScript<T>();
        script.setLocation(new ClassPathResource("redis/auth/" + name));
        script.setResultType(resultType);
        return script;
    }

    public void create(String tokenHash, UUID userId, Duration lifetime) {
        var family = UUID.randomUUID();
        var result = redis.execute(CREATE, List.of(keys.refreshToken(tokenHash), keys.refreshSession(family)),
                family.toString(), userId.toString(), tokenHash, Long.toString(lifetime.toMillis()));
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Could not create refresh session");
        }
    }

    public Optional<Rotation> rotate(String oldHash, String newHash) {
        var family = redis.opsForValue().get(keys.refreshToken(oldHash));
        if (family == null) {
            return Optional.empty();
        }
        var result = redis.execute(ROTATE, List.of(keys.refreshToken(oldHash), keys.refreshToken(newHash),
                keys.refreshSession(UUID.fromString(family))), family, oldHash, newHash);
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        var parts = result.split("\\|", 2);
        return Optional.of(new Rotation(UUID.fromString(parts[0]), Duration.ofMillis(Long.parseLong(parts[1]))));
    }

    public void revoke(String tokenHash) {
        var family = redis.opsForValue().get(keys.refreshToken(tokenHash));
        if (family != null) {
            // Deleting the family revokes every token, including a concurrently rotated token.
            redis.delete(keys.refreshSession(UUID.fromString(family)));
        }
    }

    public record Rotation(UUID userId, Duration remainingLifetime) {
    }
}
