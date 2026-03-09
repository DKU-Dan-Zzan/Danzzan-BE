package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean enterQueue(String eventId, String userId) {
        String key = TicketRedisKeys.queueKey(eventId);
        double score = System.currentTimeMillis();
        Boolean added = stringRedisTemplate.opsForZSet().addIfAbsent(key, userId, score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        String key = TicketRedisKeys.queueKey(eventId);
        return stringRedisTemplate.opsForZSet().rank(key, userId);
    }
}
