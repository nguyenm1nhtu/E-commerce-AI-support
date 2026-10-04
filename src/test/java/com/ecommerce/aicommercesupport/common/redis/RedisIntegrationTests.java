package com.ecommerce.aicommercesupport.common.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.ecommerce.aicommercesupport.common.config.RedisConfiguration;
import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Run against a dedicated Redis instance using -Dredis.integration.port=<port>. */
@EnabledIfSystemProperty(named = "redis.integration.port", matches = "[0-9]+")
class RedisIntegrationTests {

    @Test
    void storesExpiringStringsAndJsonCacheWithoutClearingAuthKeys() {
        var namespace = "redis-test-" + UUID.randomUUID();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class, CacheAutoConfiguration.class))
                .withUserConfiguration(RedisConfiguration.class, RedisKeys.class)
                .withPropertyValues("spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + System.getProperty("redis.integration.port"),
                        "spring.cache.type=redis", "app.redis.namespace=" + namespace, "app.redis.cache-ttl=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var redis = context.getBean(StringRedisTemplate.class);
                    var authKey = context.getBean(RedisKeys.class).refreshToken("b".repeat(64));
                    var cache = context.getBean(CacheManager.class).getCache("orders");
                    var cacheKey = namespace + ":cache:orders::example";
                    var dto = new OrderDto(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.values()[0],
                            Instant.parse("2026-10-04T00:00:00Z"), new BigDecimal("123.45"));
                    try {
                        redis.opsForValue().set(authKey, "test-session-metadata", Duration.ofMinutes(1));
                        assertThat(redis.opsForValue().get(authKey)).isEqualTo("test-session-metadata");
                        assertThat(redis.getExpire(authKey, TimeUnit.MILLISECONDS)).isBetween(1L, 60000L);
                        cache.put("example", dto);
                        assertThat(cache.get("example", OrderDto.class)).isEqualTo(dto);
                        assertThat(redis.opsForValue().get(cacheKey)).contains("orderedAt", "123.45");
                        assertThat(redis.getExpire(cacheKey, TimeUnit.MILLISECONDS)).isBetween(1L, 30000L);
                        cache.clear();
                        assertThat(redis.hasKey(cacheKey)).isFalse();
                        assertThat(redis.hasKey(authKey)).isTrue();
                        assertThat(redis.opsForValue().getAndDelete(authKey)).isEqualTo("test-session-metadata");
                        assertThat(redis.opsForValue().getAndDelete(authKey)).isNull();
                    } finally {
                        redis.delete(authKey);
                        redis.delete(cacheKey);
                    }
                });
    }
}
