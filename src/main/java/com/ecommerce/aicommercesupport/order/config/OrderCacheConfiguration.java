package com.ecommerce.aicommercesupport.order.config;

import java.util.List;

import com.ecommerce.aicommercesupport.order.dto.OrderItemDto;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonObjectWriter;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class OrderCacheConfiguration {

    @Bean
    public RedisCacheManagerBuilderCustomizer orderItemsCacheCustomizer(RedisCacheConfiguration defaults) {
        var mapper = JsonMapper.builder().build();
        var type = mapper.getTypeFactory().constructCollectionType(List.class, OrderItemDto.class);
        var serializer = new JacksonJsonRedisSerializer<List<OrderItemDto>>(mapper, type,
                (jsonMapper, bytes, javaType) -> List.copyOf(jsonMapper.<List<OrderItemDto>>readValue(bytes, javaType)),
                JacksonObjectWriter.create());
        return builder -> builder.withCacheConfiguration("order-items",
                defaults.serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
