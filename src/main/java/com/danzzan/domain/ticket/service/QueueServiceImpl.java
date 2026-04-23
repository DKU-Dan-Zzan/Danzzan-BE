package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate redisTemplate;

    @Qualifier("enterQueueScript")
    private final RedisScript<List> enterQueueScript;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.gate-ttl-seconds:180}")
    private long readyTtlSeconds;

    /**
     * 대기열 진입 + 상태 조회 + 순번 조회를 Lua 단일 호출로 처리.
     */
    @Override
    public QueueEnterSnapshot enterQueue(String eventId, String userId) {
        long nowMs = System.currentTimeMillis();
        long readyUntilMs = nowMs + readyTtlSeconds * 1000L;
        List<?> result = redisTemplate.execute(
                enterQueueScript,
                List.of(
                        TicketRedisKeys.dedupKey(eventId, userId),
                        TicketRedisKeys.seqKey(eventId),
                        TicketRedisKeys.queueKey(eventId),
                        TicketRedisKeys.queueUserHashKey(eventId, userId),
                        TicketRedisKeys.stockKey(eventId),
                        TicketRedisKeys.statusKey(eventId, userId),
                        TicketRedisKeys.processingMetaKey(eventId, userId),
                        TicketRedisKeys.userKey(eventId, userId),
                        TicketRedisKeys.eventStatusKey(eventId),
                        TicketRedisKeys.readyKey(eventId),
                        TicketRedisKeys.activeKey(eventId),
                        TicketRedisKeys.admittedSeqKey(eventId)
                ),
                userId,
                String.valueOf(nowMs),
                String.valueOf(readyUntilMs),
                String.valueOf(maxConcurrent)
        );
        if (result == null || result.size() < 4) {
            return new QueueEnterSnapshot(TicketRequestStatus.NONE, null, null, null);
        }
        return new QueueEnterSnapshot(
                parseStatus(result.get(0)),
                parseQueuePosition(result.get(1)),
                parseRequestId(result.get(2)),
                parseAcceptedAt(result.get(3))
        );
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        Object seqRaw = redisTemplate.opsForHash().get(TicketRedisKeys.queueUserHashKey(eventId, userId), "seq");
        Long mySeq = parseLong(seqRaw);
        if (mySeq == null) {
            return null;
        }
        Long admittedSeq = parseLong(redisTemplate.opsForValue().get(TicketRedisKeys.admittedSeqKey(eventId)));
        long admitted = admittedSeq == null ? 0L : admittedSeq;
        long position = mySeq - admitted;
        return position > 0 ? position : null;
    }

    private TicketRequestStatus parseStatus(Object raw) {
        if (raw == null) {
            return TicketRequestStatus.NONE;
        }
        try {
            return TicketRequestStatus.valueOf(raw.toString());
        } catch (IllegalArgumentException ignored) {
            return TicketRequestStatus.NONE;
        }
    }

    private Long parseQueuePosition(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.toString());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String parseRequestId(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString();
        return value.isBlank() ? null : value;
    }

    private Long parseAcceptedAt(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.toString());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
