package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("acquireSlotScript") private final RedisScript<Long> acquireSlotScript;

    @Value("${app.ticketing.max-concurrent-slots:100}") private int maxConcurrentSlots;
    @Value("${app.ticketing.gate-ttl-seconds:300}") private long gateTtlSeconds;

    @Override
    public boolean acquireSlot(String eventId, String userId) {
        String activeKey = TicketRedisKeys.activeKey(eventId);
        long nowMs = System.currentTimeMillis();
        long expiryMs = nowMs + Duration.ofSeconds(gateTtlSeconds).toMillis();

        // ZREMRANGEBYSCORE + ZCARD + ZADD 를 Lua로 원자적으로 실행
        Long result = stringRedisTemplate.execute(
                acquireSlotScript,
                List.of(activeKey),
                String.valueOf(nowMs),
                String.valueOf(maxConcurrentSlots),
                userId,
                String.valueOf(expiryMs)
        );
        return Long.valueOf(1).equals(result);
    }

    @Override
    public void releaseSlot(String eventId, String userId) {
        try {
            stringRedisTemplate.opsForZSet().remove(TicketRedisKeys.activeKey(eventId), userId);
            stringRedisTemplate.delete(TicketRedisKeys.gateUserKey(eventId, userId));
        } catch (Exception e) {
            log.error("releaseSlot failed eventId={} userId={}", eventId, userId, e);
        }
    }

    @Override
    public long activeSlotCount(String eventId) {
        String activeKey = TicketRedisKeys.activeKey(eventId);
        double nowMs = System.currentTimeMillis();
        stringRedisTemplate.opsForZSet().removeRangeByScore(activeKey, 0, nowMs);
        Long count = stringRedisTemplate.opsForZSet().zCard(activeKey);
        return count == null ? 0L : count;
    }
}
