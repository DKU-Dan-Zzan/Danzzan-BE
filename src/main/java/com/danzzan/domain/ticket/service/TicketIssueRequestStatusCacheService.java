package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TicketIssueRequestStatusCacheService {

    private static final String FIELD_EVENT_ID = "eventId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ERROR_CODE = "errorCode";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.ticketing.async.reserve.request-status-ttl-seconds:1800}")
    private long requestStatusTtlSeconds;

    public void setProcessing(Long eventId, Long userId, String requestId, long updatedAt) {
        write(eventId, userId, requestId, TicketIssueRequestStatus.PROCESSING, null, updatedAt);
    }

    public void setSuccess(Long eventId, Long userId, String requestId, long updatedAt) {
        write(eventId, userId, requestId, TicketIssueRequestStatus.SUCCESS, null, updatedAt);
    }

    public void setFailed(Long eventId, Long userId, String requestId, String errorCode, long updatedAt) {
        write(eventId, userId, requestId, TicketIssueRequestStatus.FAILED, errorCode, updatedAt);
    }

    public Optional<CachedRequestStatus> get(Long eventId, Long userId, String requestId) {
        String key = TicketRedisKeys.requestStatusKey(String.valueOf(eventId), requestId);
        List<Object> values = redisTemplate.opsForHash().multiGet(key, List.of(
                FIELD_EVENT_ID,
                FIELD_USER_ID,
                FIELD_STATUS,
                FIELD_ERROR_CODE,
                FIELD_UPDATED_AT
        ));
        if (values == null || values.size() < 5 || values.get(2) == null || values.get(4) == null) {
            return Optional.empty();
        }

        Long cachedEventId = parseLong(values.get(0));
        Long cachedUserId = parseLong(values.get(1));
        if (cachedEventId == null || cachedUserId == null) {
            return Optional.empty();
        }
        if (!eventId.equals(cachedEventId) || !userId.equals(cachedUserId)) {
            return Optional.empty();
        }

        TicketIssueRequestStatus status = parseStatus(values.get(2));
        Long updatedAt = parseLong(values.get(4));
        if (status == null || updatedAt == null) {
            return Optional.empty();
        }

        String errorCode = values.get(3) == null ? null : values.get(3).toString();
        if (errorCode != null && errorCode.isBlank()) {
            errorCode = null;
        }

        return Optional.of(new CachedRequestStatus(status, errorCode, updatedAt));
    }

    private void write(
            Long eventId,
            Long userId,
            String requestId,
            TicketIssueRequestStatus status,
            String errorCode,
            long updatedAt
    ) {
        String key = TicketRedisKeys.requestStatusKey(String.valueOf(eventId), requestId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_EVENT_ID, String.valueOf(eventId),
                FIELD_USER_ID, String.valueOf(userId),
                FIELD_STATUS, status.name(),
                FIELD_ERROR_CODE, errorCode == null ? "" : errorCode,
                FIELD_UPDATED_AT, String.valueOf(updatedAt)
        ));
        redisTemplate.expire(key, requestStatusTtlSeconds, TimeUnit.SECONDS);
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

    private TicketIssueRequestStatus parseStatus(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TicketIssueRequestStatus.valueOf(raw.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record CachedRequestStatus(
            TicketIssueRequestStatus status,
            String errorCode,
            Long updatedAt
    ) {
    }
}
