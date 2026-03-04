package com.danzzan.ticketing.domain.ticket.service;

import com.danzzan.ticketing.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.ticketing.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.ticketing.domain.ticket.service.model.ClaimResult;
import com.danzzan.ticketing.domain.ticket.service.support.ClaimLuaProtocol;
import com.danzzan.ticketing.domain.ticket.service.support.ClaimOutcomeMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimServiceImpl implements ClaimService {

    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("claimV2Script")
    private final RedisScript<List> claimV2Script;
    private final ClaimOutcomeMetrics claimOutcomeMetrics;

    @Override
    public ClaimResult claim(String eventId, String userId) {
        String userKey = TicketRedisKeys.userKey(eventId, userId);
        String stockKey = TicketRedisKeys.stockKey(eventId);
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);

        List<?> rawResult = stringRedisTemplate.execute(
                claimV2Script,
                List.of(userKey, stockKey, statusKey),
                TicketRequestStatus.ALREADY.name(),
                TicketRequestStatus.SOLD_OUT.name(),
                TicketRequestStatus.SUCCESS.name(),
                ClaimLuaProtocol.USER_CLAIMED_VALUE,
                ClaimLuaProtocol.CODE_ALREADY_ARG,
                ClaimLuaProtocol.CODE_SOLD_OUT_ARG,
                ClaimLuaProtocol.CODE_SUCCESS_ARG
        );
        return mapLuaResult(eventId, userId, rawResult);
    }

    private ClaimResult mapLuaResult(String eventId, String userId, List<?> rawResult) {
        if (rawResult == null || rawResult.size() < ClaimLuaProtocol.RESULT_SIZE) {
            throw new IllegalStateException("claim lua result must contain [code, remaining]");
        }

        long code = asLong(rawResult.get(ClaimLuaProtocol.CODE_INDEX), "code");
        Long remaining = asNullableLong(rawResult.get(ClaimLuaProtocol.REMAINING_INDEX), "remaining");
        TicketRequestStatus status = ClaimLuaProtocol.resolveStatus(code);

        if (status == TicketRequestStatus.SUCCESS) {
            if (remaining == null) {
                throw new IllegalStateException("claim lua success code requires remaining value");
            }
            return recordOutcome(eventId, userId, ClaimResult.success(remaining));
        }

        if (status == TicketRequestStatus.SOLD_OUT) {
            return recordOutcome(eventId, userId, ClaimResult.soldOut());
        }

        if (status == TicketRequestStatus.ALREADY) {
            return recordOutcome(eventId, userId, ClaimResult.already());
        }

        throw new IllegalStateException("unexpected claim lua status: " + status);
    }

    private ClaimResult recordOutcome(String eventId, String userId, ClaimResult result) {
        long count = claimOutcomeMetrics.increment(result.status());
        log.info(
                "claim_v2 outcome eventId={} userId={} status={} remaining={} total={}",
                eventId,
                userId,
                result.status(),
                result.remaining(),
                count
        );
        return result;
    }

    private Long asNullableLong(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        return asLong(value, fieldName);
    }

    private long asLong(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(fieldName + " must be parseable as long", e);
            }
        }
        throw new IllegalStateException(fieldName + " must be number-like");
    }
}
