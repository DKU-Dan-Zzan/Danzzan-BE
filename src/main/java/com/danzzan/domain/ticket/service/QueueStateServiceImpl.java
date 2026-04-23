package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueueStateServiceImpl implements QueueStateService {

    private static final String FIELD_STATE = "state";
    private static final String FIELD_READY_AT = "readyAt";
    private static final String FIELD_READY_UNTIL = "readyUntil";
    private static final String FIELD_ACTIVE_AT = "activeAt";
    private static final String FIELD_ACTIVE_UNTIL = "activeUntil";
    private static final String FIELD_EXPIRED_AT = "expiredAt";
    private static final String FIELD_CANCELLED_AT = "cancelledAt";
    private static final int EVENT_TRIGGER_BATCH_LIMIT = 16;

    private final StringRedisTemplate redisTemplate;
    private final FestivalEventRepository eventRepository;

    @Qualifier("readyToActiveScript")
    private final RedisScript<Long> readyToActiveScript;

    @Qualifier("admitOneWaitingUserScript")
    private final RedisScript<Long> admitOneWaitingUserScript;

    @Qualifier("expireReadyUsersScript")
    private final RedisScript<List> expireReadyUsersScript;

    @Qualifier("expireActiveUsersScript")
    private final RedisScript<List> expireActiveUsersScript;

    @Value("${app.ticketing.active-ttl-seconds:600}")
    private long activeTtlSeconds;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.gate-ttl-seconds:180}")
    private long readyTtlSeconds;

    @Value("${app.ticketing.expire.batch-size:200}")
    private int expireBatchSize;

    @Override
    public int admitWaitingUsers(String eventId, long readyUntilMs, int maxConcurrent, int batchLimit) {
        Long promotedCount = redisTemplate.execute(
                admitOneWaitingUserScript,
                List.of(
                        TicketRedisKeys.queueKey(eventId),
                        TicketRedisKeys.readyKey(eventId),
                        TicketRedisKeys.activeKey(eventId),
                        TicketRedisKeys.stockKey(eventId),
                        TicketRedisKeys.admittedSeqKey(eventId)
                ),
                TicketRedisKeys.queueUserPrefix(eventId),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(readyUntilMs),
                String.valueOf(maxConcurrent),
                String.valueOf(Math.max(1, batchLimit))
        );
        return promotedCount == null ? 0 : Math.max(0, promotedCount.intValue());
    }

    @Override
    public long activateIfReady(String eventId, String userId) {
        long nowMs = System.currentTimeMillis();
        long activeUntilMs = nowMs + activeTtlSeconds * 1000L;
        Long result = redisTemplate.execute(
                readyToActiveScript,
                List.of(
                        TicketRedisKeys.queueUserHashKey(eventId, userId),
                        TicketRedisKeys.readyKey(eventId),
                        TicketRedisKeys.activeKey(eventId),
                        TicketRedisKeys.dedupKey(eventId, userId)
                ),
                String.valueOf(nowMs),
                userId,
                String.valueOf(activeUntilMs)
        );
        long normalized = result == null ? 0L : result;
        if (normalized < 0) {
            backfillFreedSlotsIfOpen(eventId, 1);
        }
        return normalized;
    }

    @Override
    public void markDone(String eventId, String userId) {
        redisTemplate.opsForHash().put(
                TicketRedisKeys.queueUserHashKey(eventId, userId),
                FIELD_STATE, QueueUserState.DONE.name());
        redisTemplate.opsForZSet().remove(TicketRedisKeys.activeKey(eventId), userId);
    }

    @Override
    public void releaseActive(String eventId, String userId) {
        try {
            redisTemplate.opsForZSet().remove(TicketRedisKeys.activeKey(eventId), userId);
        } catch (Exception e) {
            log.error("releaseActive 실패 eventId={} userId={}", eventId, userId, e);
        }
    }

    @Override
    public int expireActiveUsers(String eventId) {
        String nowMs = String.valueOf(System.currentTimeMillis());
        String batchLimit = String.valueOf(Math.max(1, expireBatchSize));
        List<Object> expiredIds = redisTemplate.execute(
                expireActiveUsersScript,
                List.of(TicketRedisKeys.activeKey(eventId)),
                TicketRedisKeys.queueUserPrefix(eventId),
                TicketRedisKeys.dedupKeyPrefix(eventId),
                nowMs,
                batchLimit
        );
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        for (Object raw : expiredIds) {
            String userId = raw.toString();
            log.debug("ACTIVE 만료 eventId={} userId={}", eventId, userId);
        }
        backfillFreedSlotsIfOpen(eventId, expiredIds.size());
        return expiredIds.size();
    }

    @Override
    public int expireReadyUsers(String eventId) {
        String nowMs = String.valueOf(System.currentTimeMillis());
        String batchLimit = String.valueOf(Math.max(1, expireBatchSize));
        List<Object> expiredIds = redisTemplate.execute(
                expireReadyUsersScript,
                List.of(TicketRedisKeys.readyKey(eventId)),
                TicketRedisKeys.queueUserPrefix(eventId),
                TicketRedisKeys.dedupKeyPrefix(eventId),
                nowMs,
                batchLimit
        );
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        for (Object raw : expiredIds) {
            String userId = raw.toString();
            log.debug("READY 만료 eventId={} userId={}", eventId, userId);
        }
        backfillFreedSlotsIfOpen(eventId, expiredIds.size());
        return expiredIds.size();
    }

    @Override
    public QueueUserState getState(String eventId, String userId) {
        Object raw = redisTemplate.opsForHash().get(
                TicketRedisKeys.queueUserHashKey(eventId, userId), FIELD_STATE);
        if (raw == null) {
            return null;
        }
        try {
            return QueueUserState.valueOf(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public long readyCount(String eventId) {
        Long count = redisTemplate.opsForZSet().zCard(TicketRedisKeys.readyKey(eventId));
        return count == null ? 0L : count;
    }

    @Override
    public long activeCount(String eventId) {
        Long count = redisTemplate.opsForZSet().zCard(TicketRedisKeys.activeKey(eventId));
        return count == null ? 0L : count;
    }

    @Override
    public int cancelWaitingQueue(String eventId) {
        long now = System.currentTimeMillis();
        int cancelled = 0;
        cancelled += cancelUsersFromZSet(eventId, TicketRedisKeys.queueKey(eventId), now);
        cancelled += cancelUsersFromZSet(eventId, TicketRedisKeys.readyKey(eventId), now);
        cancelled += cancelUsersFromZSet(eventId, TicketRedisKeys.activeKey(eventId), now);

        redisTemplate.delete(TicketRedisKeys.queueKey(eventId));
        redisTemplate.delete(TicketRedisKeys.readyKey(eventId));
        redisTemplate.delete(TicketRedisKeys.activeKey(eventId));

        if (cancelled > 0) {
            log.info("대기열 일괄 취소 eventId={} count={}", eventId, cancelled);
        }
        return cancelled;
    }

    private int cancelUsersFromZSet(String eventId, String zsetKey, long now) {
        int cancelled = 0;
        List<String> batch = new ArrayList<>();
        try (Cursor<ZSetOperations.TypedTuple<String>> cursor =
                     redisTemplate.opsForZSet().scan(zsetKey, ScanOptions.scanOptions().count(500).build())) {
            while (cursor.hasNext()) {
                ZSetOperations.TypedTuple<String> tuple = cursor.next();
                if (tuple == null || tuple.getValue() == null) {
                    continue;
                }
                batch.add(tuple.getValue());
                if (batch.size() >= EVENT_TRIGGER_BATCH_LIMIT) {
                    cancelled += cancelUsers(eventId, batch, now);
                    batch.clear();
                }
            }
        } catch (Exception e) {
            log.warn("대기열 일괄 취소 scan 실패 eventId={} key={}", eventId, zsetKey, e);
        }
        if (!batch.isEmpty()) {
            cancelled += cancelUsers(eventId, batch, now);
        }
        return cancelled;
    }

    private int cancelUsers(String eventId, Collection<String> users, long now) {
        if (users == null || users.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String userId : users) {
            String hashKey = TicketRedisKeys.queueUserHashKey(eventId, userId);
            redisTemplate.opsForHash().put(hashKey, FIELD_STATE, QueueUserState.CANCELLED.name());
            redisTemplate.opsForHash().put(hashKey, FIELD_CANCELLED_AT, String.valueOf(now));
            redisTemplate.delete(TicketRedisKeys.dedupKey(eventId, userId));
            count++;
        }
        return count;
    }

    @Override
    public void leaveQueue(String eventId, String userId) {
        String hashKey = TicketRedisKeys.queueUserHashKey(eventId, userId);
        String dedupKey = TicketRedisKeys.dedupKey(eventId, userId);

        Object rawState = redisTemplate.opsForHash().get(hashKey, FIELD_STATE);
        if (rawState == null) {
            return;
        }

        String state = rawState.toString();
        long now = System.currentTimeMillis();

        switch (state) {
            case "WAITING" -> {
                redisTemplate.opsForZSet().remove(TicketRedisKeys.queueKey(eventId), userId);
                redisTemplate.opsForHash().put(hashKey, FIELD_STATE, QueueUserState.CANCELLED.name());
                redisTemplate.opsForHash().put(hashKey, FIELD_CANCELLED_AT, String.valueOf(now));
                redisTemplate.delete(dedupKey);
            }
            case "READY" -> {
                redisTemplate.opsForZSet().remove(TicketRedisKeys.readyKey(eventId), userId);
                redisTemplate.opsForHash().put(hashKey, FIELD_STATE, QueueUserState.CANCELLED.name());
                redisTemplate.opsForHash().put(hashKey, FIELD_CANCELLED_AT, String.valueOf(now));
                redisTemplate.delete(dedupKey);
                backfillFreedSlotsIfOpen(eventId, 1);
            }
            case "ACTIVE" -> {
                redisTemplate.opsForZSet().remove(TicketRedisKeys.activeKey(eventId), userId);
                redisTemplate.opsForHash().put(hashKey, FIELD_STATE, QueueUserState.CANCELLED.name());
                redisTemplate.opsForHash().put(hashKey, FIELD_CANCELLED_AT, String.valueOf(now));
                redisTemplate.delete(dedupKey);
                backfillFreedSlotsIfOpen(eventId, 1);
            }
            default -> {
                // DONE, CANCELLED, EXPIRED — 이미 처리된 상태, 무시
            }
        }
    }

    private void backfillFreedSlotsIfOpen(String eventId, int freedSlots) {
        if (freedSlots <= 0 || !isEventOpen(eventId)) {
            return;
        }

        int attempts = Math.min(freedSlots, EVENT_TRIGGER_BATCH_LIMIT);
        long readyUntilMs = System.currentTimeMillis() + readyTtlSeconds * 1000L;
        admitWaitingUsers(eventId, readyUntilMs, maxConcurrent, attempts);
    }

    private boolean isEventOpen(String eventId) {
        try {
            Long eventIdLong = Long.valueOf(eventId);
            return eventRepository.findById(eventIdLong)
                    .map(event -> event.getTicketingStatus() == TicketingStatus.OPEN)
                    .orElse(false);
        } catch (NumberFormatException e) {
            log.warn("eventId 파싱 실패로 event-driven 승격 생략 eventId={}", eventId);
            return false;
        }
    }
}
