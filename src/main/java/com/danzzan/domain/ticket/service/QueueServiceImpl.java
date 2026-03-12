package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean enterQueue(String eventId, String userId) {
        String key = TicketRedisKeys.queueKey(eventId);
        double score = System.currentTimeMillis();
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(key, userId, score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        Long rank = redisTemplate.opsForZSet().rank(TicketRedisKeys.queueKey(eventId), userId);
        if (rank == null) {
            return null;
        }
        return rank + 1; // Redis ZRANK는 0-indexed → 1-indexed
    }
}
