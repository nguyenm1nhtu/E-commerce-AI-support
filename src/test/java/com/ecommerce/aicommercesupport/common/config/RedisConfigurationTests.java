package com.ecommerce.aicommercesupport.common.config;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.dto.OrderDto;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class, CacheAutoConfiguration.class))
            .withUserConfiguration(RedisConfiguration.class);

    @Test
    void autoConfiguresRedisCacheWithBoundTtlAndNamespace() {
        runner.withPropertyValues("spring.cache.type=redis", "app.redis.namespace=test-app", "app.redis.cache-ttl=3m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                    var manager = context.getBean(RedisCacheManager.class);
                    assertThat(manager.isTransactionAware()).isTrue();
                    var cache = (TransactionAwareCacheDecorator) manager.getCache("orders");
                    var config = ((RedisCache) cache.getTargetCache()).getCacheConfiguration();
                    assertThat(config.getTtlFunction().getTimeToLive("key", "value")).isEqualTo(Duration.ofMinutes(3));
                    assertThat(config.getKeyPrefixFor("orders")).isEqualTo("test-app:cache:orders::");
                    assertThat(config.getAllowCacheNullValues()).isFalse();
                });
    }

    @Test
    void allowsTestsToDisableCachingWithoutRedis() {
        runner.withPropertyValues("spring.cache.type=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class);
        });
    }

    @Test
    void roundTripsDtoWithUuidInstantEnumAndMoneyAsJson() {
        var config = new RedisConfiguration().redisCacheConfiguration(
                new RedisProperties("test", Duration.ofMinutes(10)));
        var dto = new OrderDto(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.values()[0],
                Instant.parse("2026-10-04T00:00:00Z"), new BigDecimal("123.45"));
        var serializer = config.getValueSerializationPair();
        var encoded = serializer.write(dto);

        assertThat(StandardCharsets.UTF_8.decode(encoded.asReadOnlyBuffer()).toString()).contains("123.45", "orderedAt");
        assertThat(serializer.read(encoded)).isEqualTo(dto);
    }

    @Test
    void rejectsTypeMetadataOutsideAllowedPackages() {
        var config = new RedisConfiguration().redisCacheConfiguration(
                new RedisProperties("test", Duration.ofMinutes(10)));
        var input = ByteBuffer.wrap("[\"java.io.File\",\"/tmp/untrusted\"]".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> config.getValueSerializationPair().read(input))
                .isInstanceOf(org.springframework.data.redis.serializer.SerializationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"namespace=", "namespace=invalid*", "namespace=invalid:", "cache-ttl=0s", "cache-ttl=-1s"})
    void rejectsUnsafeNamespaceOrNonExpiringCacheConfiguration(String property) {
        runner.withPropertyValues("app.redis." + property).run(context -> assertThat(context).hasFailed());
    }
}
