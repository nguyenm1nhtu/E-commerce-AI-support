package com.ecommerce.aicommercesupport.order.controller;

import java.util.concurrent.TimeUnit;

import com.ecommerce.aicommercesupport.common.config.RedisProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cache.type=redis",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=${redis.integration.port:6379}",
        "app.redis.namespace=commerce-cache-test-${random.uuid}",
        "app.redis.cache-ttl=30s"
})
@EnabledIfSystemProperty(named = "redis.integration.port", matches = "[0-9]+")
class CommerceRedisCacheTests extends CommerceCacheTests {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisProperties properties;

    @ParameterizedTest
    @ValueSource(strings = {"payment", "shipment", "items", "item"})
    void apiWritesExpiringJsonAndReloadsAfterExpiration(String resource) throws Exception {
        var first = request(resource);
        var key = properties.namespace() + ":cache:" + cacheName(resource) + "::" + cacheKey(resource);
        assertThat(redis.opsForValue().get(key)).contains("orderId");
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 30000L);
        redis.expire(key, java.time.Duration.ZERO);
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(request(resource)).isEqualTo(first);
        assertThat(redis.hasKey(key)).isTrue();
    }
}
