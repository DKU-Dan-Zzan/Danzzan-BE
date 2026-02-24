package com.danzzan.ticketing.domain.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/tickets/redis")
@RequiredArgsConstructor
@Tag(name = "Redis 연결 테스트", description = "Redis 연결 및 set/get 동작 검증 API")
public class RedisTestController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/ping")
    @Operation(summary = "Redis PING", description = "Redis 연결 상태를 확인합니다.")
    public ResponseEntity<Map<String, Object>> ping() {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            throw new IllegalStateException("RedisConnectionFactory가 초기화되지 않았습니다.");
        }

        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return ResponseEntity.ok(Map.of(
                    "connected", true,
                    "response", pong
            ));
        }
    }

    @PostMapping("/set")
    @Operation(summary = "Redis SET", description = "키/값을 Redis에 저장합니다.")
    public ResponseEntity<Map<String, Object>> setValue(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(defaultValue = "300") long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds는 1 이상이어야 합니다.");
        }

        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        Long currentTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("saved", true);
        response.put("key", key);
        response.put("value", value);
        response.put("ttlSeconds", currentTtl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/get")
    @Operation(summary = "Redis GET", description = "Redis에서 키의 값을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getValue(@RequestParam String key) {
        String value = redisTemplate.opsForValue().get(key);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", value != null);
        response.put("key", key);
        response.put("value", value);
        response.put("ttlSeconds", ttl);

        return ResponseEntity.ok(response);
    }
}
