package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TicketStatusServiceImpl implements TicketStatusService {

    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_READY_UNTIL = "readyUntil";
    private static final String FIELD_REQUEST_ID = "requestId";
    private static final String FIELD_ACCEPTED_AT = "acceptedAt";

    private final StringRedisTemplate redisTemplate;
    private final QueueService queueService;
    private final QueueStateService queueStateService;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.gate-ttl-seconds:180}")
    private long readyTtlSeconds;

    @Override
    public QueueStatusSnapshot getQueueStatusSnapshot(String eventId, String userId) {
        List<String> keyValues = redisTemplate.opsForValue().multiGet(
                List.of(
                        TicketRedisKeys.statusKey(eventId, userId),
                        TicketRedisKeys.admittedSeqKey(eventId),
                        TicketRedisKeys.stockKey(eventId)
                )
        );
        List<Object> hashValues = redisTemplate.opsForHash().multiGet(
                TicketRedisKeys.queueUserHashKey(eventId, userId),
                List.of("state", "seq", "readyUntil")
        );

        String claimRaw = getListString(keyValues, 0);
        long admittedSeq = parseLongOrZero(getListString(keyValues, 1));
        String stockRaw = getListString(keyValues, 2);
        String stateRaw = getListString(hashValues, 0);
        Long mySequence = parseLong(getListString(hashValues, 1));
        Long readyUntilRaw = parseLong(getListString(hashValues, 2));

        QueueUserState state = parseQueueState(stateRaw);
        TicketRequestStatus status = resolveStatus(claimRaw, state, stockRaw);

        Long queuePosition = null;
        Long aheadCount = null;
        if (state == QueueUserState.WAITING && mySequence != null) {
            long position = mySequence - admittedSeq;
            if (position > 0) {
                queuePosition = position;
                aheadCount = Math.max(queuePosition - 1L, 0L);
            }
        } else if (state == QueueUserState.READY || state == QueueUserState.ACTIVE) {
            aheadCount = 0L;
        }

        QueueUserState admissionState =
                (state == QueueUserState.READY || state == QueueUserState.ACTIVE) ? state : null;
        Long readyUntil = state == QueueUserState.READY ? readyUntilRaw : null;

        return new QueueStatusSnapshot(
                status,
                queuePosition,
                mySequence,
                aheadCount,
                readyUntil,
                admissionState
        );
    }

    @Override
    public TicketRequestStatus getStatus(String eventId, String userId) {
        // 1. Lua claim 결과 확인 (SUCCESS / SOLD_OUT / ALREADY)
        String claimStatus = redisTemplate.opsForValue().get(TicketRedisKeys.statusKey(eventId, userId));
        if (claimStatus != null && !claimStatus.isBlank()) {
            try {
                TicketRequestStatus s = TicketRequestStatus.valueOf(claimStatus);
                if (s == TicketRequestStatus.SUCCESS
                        || s == TicketRequestStatus.SOLD_OUT
                        || s == TicketRequestStatus.ALREADY
                        || s == TicketRequestStatus.PROCESSING
                        || s == TicketRequestStatus.FAILED) {
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
            // 자발적 이탈은 매진이 아니라 "대기열 미참여"로 보는 편이 UI/재진입 흐름에 자연스럽다.
            case CANCELLED -> TicketRequestStatus.NONE;
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

    @Override
    public void setProcessing(String eventId, String userId, String requestId, long acceptedAt, long ttlSeconds) {
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String metaKey = TicketRedisKeys.processingMetaKey(eventId, userId);
        redisTemplate.opsForValue().set(statusKey, TicketRequestStatus.PROCESSING.name(), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForHash().put(metaKey, FIELD_REQUEST_ID, requestId);
        redisTemplate.opsForHash().put(metaKey, FIELD_ACCEPTED_AT, String.valueOf(acceptedAt));
        redisTemplate.expire(metaKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getProcessingRequestId(String eventId, String userId) {
        Object raw = redisTemplate.opsForHash().get(TicketRedisKeys.processingMetaKey(eventId, userId), FIELD_REQUEST_ID);
        return raw == null ? null : raw.toString();
    }

    @Override
    public Long getProcessingAcceptedAt(String eventId, String userId) {
        return getHashLong(TicketRedisKeys.processingMetaKey(eventId, userId), FIELD_ACCEPTED_AT);
    }

    @Override
    public void clearProcessing(String eventId, String userId) {
        redisTemplate.delete(TicketRedisKeys.statusKey(eventId, userId));
        redisTemplate.delete(TicketRedisKeys.processingMetaKey(eventId, userId));
    }

    @Override
    public void setFailed(String eventId, String userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                TicketRedisKeys.statusKey(eventId, userId),
                TicketRequestStatus.FAILED.name(),
                ttlSeconds,
                TimeUnit.SECONDS
        );
        redisTemplate.delete(TicketRedisKeys.processingMetaKey(eventId, userId));
    }

    private boolean isStockExhausted(String eventId) {
        String stock = redisTemplate.opsForValue().get(TicketRedisKeys.stockKey(eventId));
        return isStockExhaustedValue(stock);
    }

    private boolean isStockExhaustedValue(String stock) {
        if (stock == null) {
            // stock 키 없음 = 미초기화. admit_one_waiting_user.lua도 동일하게 승격 거부하므로
            // WAITING 유저가 영원히 진행 불가 → SOLD_OUT으로 표시해 혼란 방지
            return true;
        }
        try {
            return Long.parseLong(stock) <= 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private TicketRequestStatus resolveStatus(String claimRaw, QueueUserState state, String stockRaw) {
        TicketRequestStatus claimStatus = parseStatus(claimRaw);
        if (claimStatus == TicketRequestStatus.SUCCESS
                || claimStatus == TicketRequestStatus.SOLD_OUT
                || claimStatus == TicketRequestStatus.ALREADY
                || claimStatus == TicketRequestStatus.PROCESSING
                || claimStatus == TicketRequestStatus.FAILED) {
            return claimStatus;
        }
        if (state == null) {
            return TicketRequestStatus.NONE;
        }
        return switch (state) {
            case WAITING -> isStockExhaustedValue(stockRaw)
                    ? TicketRequestStatus.SOLD_OUT
                    : TicketRequestStatus.WAITING;
            case READY, ACTIVE -> TicketRequestStatus.ADMITTED;
            case DONE -> TicketRequestStatus.SUCCESS;
            case EXPIRED, CANCELLED -> TicketRequestStatus.NONE;
        };
    }

    private TicketRequestStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TicketRequestStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private QueueUserState parseQueueState(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return QueueUserState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getListString(List<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        Object value = list.get(index);
        return value == null ? null : value.toString();
    }

    private long parseLongOrZero(String raw) {
        Long parsed = parseLong(raw);
        return parsed == null ? 0L : parsed;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getHashLong(String eventId, String userId, String field) {
        return getHashLong(TicketRedisKeys.queueUserHashKey(eventId, userId), field);
    }

    private Long getHashLong(String key, String field) {
        Object raw = redisTemplate.opsForHash().get(key, field);
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
