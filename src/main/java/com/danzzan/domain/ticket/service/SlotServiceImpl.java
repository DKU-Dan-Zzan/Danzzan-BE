package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrentSlots;

    @Value("${app.ticketing.gate-ttl-seconds:300}")
    private long gateTtlSeconds;

    @Override
    public boolean acquireSlot(String eventId, String userId) {
        String activeKey = TicketRedisKeys.activeKey(eventId);
        double nowMs = System.currentTimeMillis();

        // 만료된 슬롯 먼저 정리
        redisTemplate.opsForZSet().removeRangeByScore(activeKey, 0, nowMs);

        Long current = redisTemplate.opsForZSet().zCard(activeKey);
        if (current != null && current >= maxConcurrentSlots) {
            return false;
        }

        double expiryMs = nowMs + Duration.ofSeconds(gateTtlSeconds).toMillis();
        redisTemplate.opsForZSet().add(activeKey, userId, expiryMs);
        return true;
    }

    @Override
    public void releaseSlot(String eventId, String userId) {
        try {
            redisTemplate.opsForZSet().remove(TicketRedisKeys.activeKey(eventId), userId);
            redisTemplate.delete(TicketRedisKeys.gateUserKey(eventId, userId));
        } catch (Exception e) {
            log.error("releaseSlot 실패 eventId={} userId={}", eventId, userId, e);
        }
    }

    @Override
    public long activeSlotCount(String eventId) {
        String activeKey = TicketRedisKeys.activeKey(eventId);
        double nowMs = System.currentTimeMillis();

        redisTemplate.opsForZSet().removeRangeByScore(activeKey, 0, nowMs);

        Long count = redisTemplate.opsForZSet().zCard(activeKey);
        return count == null ? 0L : count;
    }
}
