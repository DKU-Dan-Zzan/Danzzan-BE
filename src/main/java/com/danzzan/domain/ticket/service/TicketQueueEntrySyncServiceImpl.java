package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.exception.EventNotFoundException;
import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.QueueEntryStatus;
import com.danzzan.domain.ticket.model.entity.TicketQueueEntry;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.repository.TicketQueueEntryRepository;
import com.danzzan.domain.user.exception.UserNotFoundException;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TicketQueueEntrySyncServiceImpl implements TicketQueueEntrySyncService {

    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_ENTERED_AT = "enteredAt";
    private static final String FIELD_READY_UNTIL = "readyUntil";
    private static final String FIELD_ACTIVE_UNTIL = "activeUntil";

    private final TicketQueueEntryRepository queueEntryRepository;
    private final FestivalEventRepository eventRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void markWaiting(String eventId, String userId, long seq, long enteredAtMs) {
        TicketQueueEntry entry = getOrCreateEntry(eventId, userId, seq, enteredAtMs);
        entry.markWaiting(seq, toLocalDateTime(enteredAtMs));
    }

    @Override
    public void markReady(String eventId, String userId, long readyUntilMs) {
        withEntry(eventId, userId, entry -> entry.markReady(toLocalDateTime(readyUntilMs)));
    }

    @Override
    public void markActive(String eventId, String userId, long leaseUntilMs) {
        withEntry(eventId, userId, entry -> entry.markActive(toLocalDateTime(leaseUntilMs)));
    }

    @Override
    public void markDone(String eventId, String userId) {
        withEntry(eventId, userId, TicketQueueEntry::markDone);
    }

    @Override
    public void markFailed(String eventId, String userId) {
        withEntry(eventId, userId, TicketQueueEntry::markFailed);
    }

    @Override
    public void markExpired(String eventId, String userId) {
        withEntry(eventId, userId, TicketQueueEntry::markExpired);
    }

    @Override
    public void markCancelled(String eventId, String userId) {
        withEntry(eventId, userId, TicketQueueEntry::markCancelled);
    }

    private TicketQueueEntry getOrCreateEntry(String eventId, String userId, long seq, long enteredAtMs) {
        return queueEntryRepository.findByEventIdAndUserId(toLong(eventId), toLong(userId))
                .orElseGet(() -> queueEntryRepository.save(TicketQueueEntry.builder()
                        .event(getEvent(eventId))
                        .user(getUser(userId))
                        .status(QueueEntryStatus.WAITING)
                        .seq(seq)
                        .enteredAt(toLocalDateTime(enteredAtMs))
                        .build()));
    }

    private void withEntry(String eventId, String userId, Consumer<TicketQueueEntry> updater) {
        TicketQueueEntry entry = queueEntryRepository.findByEventIdAndUserId(toLong(eventId), toLong(userId))
                .orElseGet(() -> recreateFromRedisSnapshot(eventId, userId));
        if (entry == null) {
            log.warn("queue entry sync skip eventId={} userId={} reason=missing_entry_and_snapshot", eventId, userId);
            return;
        }
        updater.accept(entry);
    }

    private TicketQueueEntry recreateFromRedisSnapshot(String eventId, String userId) {
        String hashKey = TicketRedisKeys.queueUserHashKey(eventId, userId);
        Long seq = getHashLong(hashKey, FIELD_SEQ);
        Long enteredAtMs = getHashLong(hashKey, FIELD_ENTERED_AT);
        if (seq == null || enteredAtMs == null) {
            return null;
        }

        TicketQueueEntry recreated = TicketQueueEntry.builder()
                .event(getEvent(eventId))
                .user(getUser(userId))
                .status(QueueEntryStatus.WAITING)
                .seq(seq)
                .enteredAt(toLocalDateTime(enteredAtMs))
                .readyUntil(toNullableLocalDateTime(getHashLong(hashKey, FIELD_READY_UNTIL)))
                .leaseUntil(toNullableLocalDateTime(getHashLong(hashKey, FIELD_ACTIVE_UNTIL)))
                .build();
        log.warn("queue entry recreated from redis snapshot eventId={} userId={} seq={}", eventId, userId, seq);
        return queueEntryRepository.save(recreated);
    }

    private FestivalEvent getEvent(String eventId) {
        return eventRepository.findById(toLong(eventId))
                .orElseThrow(EventNotFoundException::new);
    }

    private User getUser(String userId) {
        return userRepository.findById(toLong(userId))
                .orElseThrow(UserNotFoundException::new);
    }

    private LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    private LocalDateTime toNullableLocalDateTime(Long epochMs) {
        if (epochMs == null) {
            return null;
        }
        return toLocalDateTime(epochMs);
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

    private Long toLong(String raw) {
        return Long.valueOf(raw);
    }
}
