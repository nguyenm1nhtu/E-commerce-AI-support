package com.ecommerce.aicommercesupport.common.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfiguration {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(RedisProperties properties) {
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ecommerce.aicommercesupport.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType(String.class)
                .allowIfSubType(Number.class)
                .allowIfSubType(Boolean.class)
                .build();
        var serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.cacheTtl())
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> properties.namespace() + ":cache:" + cacheName + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            RedisConnectionFactory connectionFactory) {
        return builder -> builder
                .cacheWriter(RedisCacheWriter.create(connectionFactory,
                        configurer -> configurer.batchStrategy(BatchStrategies.scan(1000)).immediateWrites()))
                .transactionAware();
    }
}
