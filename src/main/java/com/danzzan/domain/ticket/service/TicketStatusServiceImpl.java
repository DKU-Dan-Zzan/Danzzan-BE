package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TicketStatusServiceImpl implements TicketStatusService {

    private static final String FIELD_REQUEST_ID = "requestId";
    private static final String FIELD_ACCEPTED_AT = "acceptedAt";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("queueStatusSnapshotScript")
    private final RedisScript<List> queueStatusSnapshotScript;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.direct-admission.active-ttl-seconds:${app.ticketing.active-ttl-seconds:600}}")
    private long admissionSlotTtlSeconds;

    @Override
    public QueueStatusSnapshot getQueueStatusSnapshot(String eventId, String userId) {
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String admittedSeqKey = TicketRedisKeys.admittedSeqKey(eventId);
        String stockKey = TicketRedisKeys.stockKey(eventId);
        String queueUserKey = TicketRedisKeys.queueUserHashKey(eventId, userId);

        List<?> snapshotValues = redisTemplate.execute(
                queueStatusSnapshotScript,
                List.of(statusKey, queueUserKey, admittedSeqKey, stockKey)
        );

        String claimRaw = getListString(snapshotValues, 0);
        String stateRaw = getListString(snapshotValues, 1);
        Long mySequence = parseLong(getListString(snapshotValues, 2));
        Long readyUntilRaw = parseLong(getListString(snapshotValues, 3));
        Long activeUntilRaw = parseLong(getListString(snapshotValues, 4));
        long admittedSeq = parseLongOrZero(getListString(snapshotValues, 5));
        String stockRaw = getListString(snapshotValues, 6);

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
        Long readyUntil = switch (state) {
            case READY -> readyUntilRaw;
            case ACTIVE -> activeUntilRaw;
            default -> null;
        };

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
        return getQueueStatusSnapshot(eventId, userId).status();
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        return getQueueStatusSnapshot(eventId, userId).queuePosition();
    }

    @Override
    public Long getMySequence(String eventId, String userId) {
        return getQueueStatusSnapshot(eventId, userId).mySequence();
    }

    @Override
    public Long getAheadCount(String eventId, String userId) {
        return getQueueStatusSnapshot(eventId, userId).aheadCount();
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
        return batches * admissionSlotTtlSeconds;
    }

    @Override
    public Long getReadyUntil(String eventId, String userId) {
        QueueStatusSnapshot snapshot = getQueueStatusSnapshot(eventId, userId);
        QueueUserState admissionState = snapshot.admissionState();
        if (admissionState != QueueUserState.READY) {
            return null;
        }
        return snapshot.readyUntil();
    }

    @Override
    public QueueUserState getAdmissionState(String eventId, String userId) {
        return getQueueStatusSnapshot(eventId, userId).admissionState();
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
