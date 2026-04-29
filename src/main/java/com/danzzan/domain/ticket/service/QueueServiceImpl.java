package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private static final String FIELD_STATE = "state";
    private static final String FIELD_REQUEST_ID = "requestId";
    private static final String FIELD_ACCEPTED_AT = "acceptedAt";

    private final StringRedisTemplate redisTemplate;

    @Qualifier("enterQueueScript")
    private final RedisScript<List> enterQueueScript;

    /**
     * 대기열 진입 + 상태 조회 + 순번 조회를 Lua 단일 호출로 처리.
     * WAITING→ACTIVE 직행 승격은 스케줄러가 전담하므로 enter path는 최소 Redis 명령만 실행.
     */
    @Override
    public QueueEnterSnapshot enterQueue(String eventId, String userId) {
        PrecheckSnapshot precheck = fetchPrecheckSnapshot(eventId, userId);

        QueueEnterSnapshot earlySnapshot = resolveEarlySnapshot(eventId, userId, precheck);
        if (earlySnapshot != null) {
            return earlySnapshot;
        }

        long nowMs = System.currentTimeMillis();
        List<?> result = redisTemplate.execute(
                enterQueueScript,
                List.of(
                        TicketRedisKeys.dedupKey(eventId, userId),
                        TicketRedisKeys.seqKey(eventId),
                        TicketRedisKeys.queueKey(eventId),
                        TicketRedisKeys.queueUserHashKey(eventId, userId),
                        TicketRedisKeys.stockKey(eventId)
                ),
                userId,
                String.valueOf(nowMs)
        );
        if (result == null || result.size() < 4) {
            return new QueueEnterSnapshot(TicketRequestStatus.NONE, null, null, null);
        }
        TicketRequestStatus status = parseStatus(result.get(0));
        Long queuePosition = parseQueuePosition(result.get(1));
        if (status == TicketRequestStatus.WAITING && queuePosition == null) {
            queuePosition = getQueuePosition(eventId, userId);
        }
        return new QueueEnterSnapshot(
                status,
                queuePosition,
                parseRequestId(result.get(2)),
                parseAcceptedAt(result.get(3))
        );
    }

    @SuppressWarnings("unchecked")
    private PrecheckSnapshot fetchPrecheckSnapshot(String eventId, String userId) {
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String queueUserHashKey = TicketRedisKeys.queueUserHashKey(eventId, userId);
        String eventStatusKey = TicketRedisKeys.eventStatusKey(eventId);

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection redisConnection = (StringRedisConnection) connection;
            redisConnection.get(statusKey);
            redisConnection.hGet(queueUserHashKey, FIELD_STATE);
            redisConnection.get(eventStatusKey);
            return null;
        });

        return new PrecheckSnapshot(
                getListString(results, 0),
                getListString(results, 1),
                getListString(results, 2)
        );
    }

    private QueueEnterSnapshot resolveEarlySnapshot(String eventId, String userId, PrecheckSnapshot precheck) {
        TicketRequestStatus claimStatus = parseStatus(precheck.claimStatus());
        if (claimStatus == TicketRequestStatus.PROCESSING) {
            ProcessingMeta processingMeta = getProcessingMeta(eventId, userId);
            return new QueueEnterSnapshot(
                    TicketRequestStatus.PROCESSING,
                    null,
                    processingMeta.requestId(),
                    processingMeta.acceptedAt()
            );
        }
        if (claimStatus == TicketRequestStatus.SUCCESS
                || claimStatus == TicketRequestStatus.SOLD_OUT
                || claimStatus == TicketRequestStatus.ALREADY) {
            return new QueueEnterSnapshot(claimStatus, null, null, null);
        }
        if (claimStatus == TicketRequestStatus.FAILED) {
            clearClaimFailure(eventId, userId);
        }

        QueueUserState queueState = parseQueueState(precheck.queueState());
        if (queueState == QueueUserState.WAITING) {
            return new QueueEnterSnapshot(
                    TicketRequestStatus.WAITING,
                    getQueuePosition(eventId, userId),
                    null,
                    null
            );
        }
        if (queueState == QueueUserState.READY || queueState == QueueUserState.ACTIVE) {
            return new QueueEnterSnapshot(TicketRequestStatus.ADMITTED, null, null, null);
        }
        if (queueState == QueueUserState.DONE) {
            return new QueueEnterSnapshot(TicketRequestStatus.SUCCESS, null, null, null);
        }

        String eventStatus = precheck.eventStatus();
        if (eventStatus == null || eventStatus.isBlank()) {
            return new QueueEnterSnapshot(TicketRequestStatus.NONE, null, null, null);
        }
        if ("CLOSED".equals(eventStatus)) {
            return new QueueEnterSnapshot(TicketRequestStatus.SOLD_OUT, null, null, null);
        }
        if (!"OPEN".equals(eventStatus)) {
            return new QueueEnterSnapshot(TicketRequestStatus.NONE, null, null, null);
        }
        return null;
    }

    private ProcessingMeta getProcessingMeta(String eventId, String userId) {
        List<Object> values = redisTemplate.opsForHash().multiGet(
                TicketRedisKeys.processingMetaKey(eventId, userId),
                List.of(FIELD_REQUEST_ID, FIELD_ACCEPTED_AT)
        );
        return new ProcessingMeta(
                getListString(values, 0),
                parseAcceptedAt(getListObject(values, 1))
        );
    }

    private void clearClaimFailure(String eventId, String userId) {
        redisTemplate.delete(TicketRedisKeys.statusKey(eventId, userId));
        redisTemplate.delete(TicketRedisKeys.processingMetaKey(eventId, userId));
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

    private QueueUserState parseQueueState(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return QueueUserState.valueOf(raw.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
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

    private String getListString(List<?> values, int index) {
        Object value = getListObject(values, index);
        return value == null ? null : value.toString();
    }

    private Object getListObject(List<?> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private record PrecheckSnapshot(
            String claimStatus,
            String queueState,
            String eventStatus
    ) {
    }

    private record ProcessingMeta(
            String requestId,
            Long acceptedAt
    ) {
    }
}
