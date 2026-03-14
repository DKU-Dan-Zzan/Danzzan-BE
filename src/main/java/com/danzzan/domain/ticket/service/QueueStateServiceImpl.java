package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

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
    private final TicketQueueEntrySyncService ticketQueueEntrySyncService;

    @Qualifier("readyToActiveScript")
    private final RedisScript<Long> readyToActiveScript;

    @Qualifier("admitOneWaitingUserScript")
    private final RedisScript<String> admitOneWaitingUserScript;

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

    @Override
    public boolean admitNextWaitingUser(String eventId, long readyUntilMs, int maxConcurrent) {
        String promotedUserId = redisTemplate.execute(
                admitOneWaitingUserScript,
                List.of(
                        TicketRedisKeys.queueKey(eventId),
                        TicketRedisKeys.readyKey(eventId),
                        TicketRedisKeys.activeKey(eventId),
                        TicketRedisKeys.stockKey(eventId)
                ),
                TicketRedisKeys.queueUserPrefix(eventId),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(readyUntilMs),
                String.valueOf(maxConcurrent)
        );
        if (promotedUserId == null || promotedUserId.isBlank()) {
            return false;
        }
        ticketQueueEntrySyncService.markReady(eventId, promotedUserId, readyUntilMs);
        return true;
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
        if (normalized > 0) {
            ticketQueueEntrySyncService.markActive(eventId, userId, activeUntilMs);
        } else if (normalized < 0) {
            ticketQueueEntrySyncService.markExpired(eventId, userId);
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
        ticketQueueEntrySyncService.markDone(eventId, userId);
    }

    @Override
    public void releaseActive(String eventId, String userId) {
        try {
            redisTemplate.opsForZSet().remove(TicketRedisKeys.activeKey(eventId), userId);
            backfillFreedSlotsIfOpen(eventId, 1);
        } catch (Exception e) {
            log.error("releaseActive 실패 eventId={} userId={}", eventId, userId, e);
        }
    }

    @Override
    public int expireActiveUsers(String eventId) {
        String nowMs = String.valueOf(System.currentTimeMillis());
        List<Object> expiredIds = redisTemplate.execute(
                expireActiveUsersScript,
                List.of(TicketRedisKeys.activeKey(eventId)),
                TicketRedisKeys.queueUserPrefix(eventId),
                TicketRedisKeys.dedupKeyPrefix(eventId),
                nowMs
        );
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        for (Object raw : expiredIds) {
            String userId = raw.toString();
            ticketQueueEntrySyncService.markExpired(eventId, userId);
            log.debug("ACTIVE 만료 eventId={} userId={}", eventId, userId);
        }
        backfillFreedSlotsIfOpen(eventId, expiredIds.size());
        return expiredIds.size();
    }

    @Override
    public int expireReadyUsers(String eventId) {
        String nowMs = String.valueOf(System.currentTimeMillis());
        List<Object> expiredIds = redisTemplate.execute(
                expireReadyUsersScript,
                List.of(TicketRedisKeys.readyKey(eventId)),
                TicketRedisKeys.queueUserPrefix(eventId),
                TicketRedisKeys.dedupKeyPrefix(eventId),
                nowMs
        );
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        for (Object raw : expiredIds) {
            String userId = raw.toString();
            ticketQueueEntrySyncService.markExpired(eventId, userId);
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
        cancelled += cancelUsers(eventId, redisTemplate.opsForZSet().range(TicketRedisKeys.queueKey(eventId), 0, -1), now);
        cancelled += cancelUsers(eventId, redisTemplate.opsForZSet().range(TicketRedisKeys.readyKey(eventId), 0, -1), now);
        cancelled += cancelUsers(eventId, redisTemplate.opsForZSet().range(TicketRedisKeys.activeKey(eventId), 0, -1), now);

        redisTemplate.delete(TicketRedisKeys.queueKey(eventId));
        redisTemplate.delete(TicketRedisKeys.readyKey(eventId));
        redisTemplate.delete(TicketRedisKeys.activeKey(eventId));

        if (cancelled > 0) {
            log.info("대기열 일괄 취소 eventId={} count={}", eventId, cancelled);
        }
        return cancelled;
    }

    private int cancelUsers(String eventId, Set<String> users, long now) {
        if (users == null || users.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String userId : users) {
            String hashKey = TicketRedisKeys.queueUserHashKey(eventId, userId);
            redisTemplate.opsForHash().put(hashKey, FIELD_STATE, QueueUserState.CANCELLED.name());
            redisTemplate.opsForHash().put(hashKey, FIELD_CANCELLED_AT, String.valueOf(now));
            redisTemplate.delete(TicketRedisKeys.dedupKey(eventId, userId));
            ticketQueueEntrySyncService.markCancelled(eventId, userId);
            count++;
        }
        return count;
    }

    private void backfillFreedSlotsIfOpen(String eventId, int freedSlots) {
        if (freedSlots <= 0 || !isEventOpen(eventId)) {
            return;
        }

        int attempts = Math.min(freedSlots, EVENT_TRIGGER_BATCH_LIMIT);
        for (int i = 0; i < attempts; i++) {
            long readyUntilMs = System.currentTimeMillis() + readyTtlSeconds * 1000L;
            boolean admitted = admitNextWaitingUser(eventId, readyUntilMs, maxConcurrent);
            if (!admitted) {
                return;
            }
        }
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
