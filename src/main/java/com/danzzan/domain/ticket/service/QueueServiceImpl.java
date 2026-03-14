package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate redisTemplate;
    private final TicketQueueEntrySyncService ticketQueueEntrySyncService;

    @Qualifier("enterQueueScript")
    private final RedisScript<Long> enterQueueScript;

    /**
     * 대기열 진입 (원자적).
     * dedup SET NX → INCR seq → ZADD queue → HSET state=WAITING 을 Lua로 한 번에 처리.
     *
     * @return 발급된 순번 (>0 = 신규 진입), 0 = 이미 대기열에 있음
     */
    @Override
    public long enterQueue(String eventId, String userId) {
        long nowMs = System.currentTimeMillis();
        Long result = redisTemplate.execute(
                enterQueueScript,
                List.of(
                        TicketRedisKeys.dedupKey(eventId, userId),
                        TicketRedisKeys.seqKey(eventId),
                        TicketRedisKeys.queueKey(eventId),
                        TicketRedisKeys.queueUserHashKey(eventId, userId)
                ),
                userId,
                String.valueOf(nowMs)
        );
        long seq = result == null ? 0L : result;
        if (seq > 0) {
            ticketQueueEntrySyncService.markWaiting(eventId, userId, seq, nowMs);
        }
        return seq;
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        Long rank = redisTemplate.opsForZSet().rank(TicketRedisKeys.queueKey(eventId), userId);
        if (rank == null) {
            return null;
        }
        return rank + 1; // 0-indexed → 1-indexed
    }
}
