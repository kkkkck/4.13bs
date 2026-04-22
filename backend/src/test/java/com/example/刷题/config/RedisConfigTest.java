package com.example.刷题.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedisConfigTest {

    @Test
    void redisValueSerializerSupportsLocalDateTime() {
        RedisConfig redisConfig = new RedisConfig();
        GenericJackson2JsonRedisSerializer serializer = redisConfig.redisValueSerializer();

        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 22, 14, 20);
        Map<String, Object> payload = new HashMap<>();
        payload.put("createdAt", createdAt);

        byte[] serialized = serializer.serialize(payload);
        Object restored = serializer.deserialize(serialized);

        assertNotNull(serialized);
        Map<?, ?> restoredMap = assertInstanceOf(Map.class, restored);
        assertEquals(createdAt, restoredMap.get("createdAt"));
    }
}
