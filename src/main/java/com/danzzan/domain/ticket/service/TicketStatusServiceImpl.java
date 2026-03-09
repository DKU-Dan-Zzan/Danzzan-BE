package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketStatusServiceImpl implements TicketStatusService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public TicketRequestStatus getStatus(String eventId, String userId) {
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String statusValue = redisTemplate.opsForValue().get(statusKey);

        if (statusValue == null || statusValue.isBlank()) {
            return TicketRequestStatus.NONE;
        }

        try {
            return TicketRequestStatus.valueOf(statusValue);
        } catch (IllegalArgumentException ignored) {
            return TicketRequestStatus.NONE;
        }
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        String queueKey = TicketRedisKeys.queueKey(eventId);
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);
        if (rank == null) {
            return null;
        }
        return rank + 1; // Redis ZRANK는 0-indexed → 1-indexed로 변환
    }
}
