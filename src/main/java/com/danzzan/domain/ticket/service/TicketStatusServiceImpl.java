package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketStatusServiceImpl implements TicketStatusService {

    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_READY_UNTIL = "readyUntil";

    private final StringRedisTemplate redisTemplate;
    private final QueueService queueService;
    private final QueueStateService queueStateService;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.gate-ttl-seconds:180}")
    private long readyTtlSeconds;

    @Override
    public TicketRequestStatus getStatus(String eventId, String userId) {
        // 1. Lua claim 결과 확인 (SUCCESS / SOLD_OUT / ALREADY)
        String claimStatus = redisTemplate.opsForValue().get(TicketRedisKeys.statusKey(eventId, userId));
        if (claimStatus != null && !claimStatus.isBlank()) {
            try {
                TicketRequestStatus s = TicketRequestStatus.valueOf(claimStatus);
                if (s == TicketRequestStatus.SUCCESS
                        || s == TicketRequestStatus.SOLD_OUT
                        || s == TicketRequestStatus.ALREADY) {
                    return s;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 2. 상태 머신 Hash 확인
        QueueUserState state = queueStateService.getState(eventId, userId);
        if (state == null) {
            return TicketRequestStatus.NONE;
        }
        return switch (state) {
            case WAITING -> isStockExhausted(eventId)
                    ? TicketRequestStatus.SOLD_OUT
                    : TicketRequestStatus.WAITING;
            case READY, ACTIVE -> TicketRequestStatus.ADMITTED;
            case DONE -> TicketRequestStatus.SUCCESS;
            case EXPIRED -> TicketRequestStatus.NONE;
            case CANCELLED -> TicketRequestStatus.SOLD_OUT;
        };
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        return queueService.getQueuePosition(eventId, userId);
    }

    @Override
    public Long getMySequence(String eventId, String userId) {
        return getHashLong(eventId, userId, FIELD_SEQ);
    }

    @Override
    public Long getAheadCount(String eventId, String userId) {
        QueueUserState state = queueStateService.getState(eventId, userId);
        if (state == QueueUserState.WAITING) {
            Long queuePosition = getQueuePosition(eventId, userId);
            return queuePosition == null ? null : Math.max(queuePosition - 1L, 0L);
        }
        if (state == QueueUserState.READY || state == QueueUserState.ACTIVE) {
            return 0L;
        }
        return null;
    }

    @Override
    public Long getEstimatedWaitSeconds(String eventId, String userId) {
        Long aheadCount = getAheadCount(eventId, userId);
        return getEstimatedWaitSeconds(aheadCount);
    }

    @Override
    public Long getEstimatedWaitSeconds(Long aheadCount) {
        if (aheadCount == null || maxConcurrent <= 0) {
            return null;
        }
        if (aheadCount == 0L) {
            return 0L;
        }
        long batches = (aheadCount + maxConcurrent - 1L) / maxConcurrent;
        return batches * readyTtlSeconds;
    }

    @Override
    public Long getReadyUntil(String eventId, String userId) {
        QueueUserState admissionState = getAdmissionState(eventId, userId);
        if (admissionState != QueueUserState.READY) {
            return null;
        }
        return getHashLong(eventId, userId, FIELD_READY_UNTIL);
    }

    @Override
    public QueueUserState getAdmissionState(String eventId, String userId) {
        QueueUserState state = queueStateService.getState(eventId, userId);
        if (state == QueueUserState.READY || state == QueueUserState.ACTIVE) {
            return state;
        }
        return null;
    }

    private boolean isStockExhausted(String eventId) {
        String stock = redisTemplate.opsForValue().get(TicketRedisKeys.stockKey(eventId));
        if (stock == null) {
            return false; // 키 없음 = READY 상태(미초기화), 아직 오픈 전
        }
        try {
            return Long.parseLong(stock) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Long getHashLong(String eventId, String userId, String field) {
        Object raw = redisTemplate.opsForHash().get(TicketRedisKeys.queueUserHashKey(eventId, userId), field);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
