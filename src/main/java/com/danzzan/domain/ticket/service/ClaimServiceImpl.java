package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.model.ClaimResult;
import com.danzzan.domain.ticket.service.support.ClaimLuaProtocol;
import com.danzzan.domain.ticket.service.support.ClaimOutcomeMetrics;
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
    @Qualifier("claimRollbackScript")
    private final RedisScript<Long> claimRollbackScript;
    private final ClaimOutcomeMetrics claimOutcomeMetrics;

    @Override
    public ClaimResult claim(String eventId, String userId) {
        String userKey = TicketRedisKeys.userKey(eventId, userId);
        String stockKey = TicketRedisKeys.stockKey(eventId);
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String queueUserHashKey = TicketRedisKeys.queueUserHashKey(eventId, userId);
        String activeKey = TicketRedisKeys.activeKey(eventId);
        String dedupKey = TicketRedisKeys.dedupKey(eventId, userId);

        List<?> rawResult = stringRedisTemplate.execute(
                claimV2Script,
                List.of(userKey, stockKey, statusKey, queueUserHashKey, activeKey, dedupKey),
                TicketRequestStatus.ALREADY.name(),
                TicketRequestStatus.SOLD_OUT.name(),
                TicketRequestStatus.SUCCESS.name(),
                ClaimLuaProtocol.USER_CLAIMED_VALUE,
                ClaimLuaProtocol.CODE_ALREADY_ARG,
                ClaimLuaProtocol.CODE_SOLD_OUT_ARG,
                ClaimLuaProtocol.CODE_SUCCESS_ARG,
                String.valueOf(System.currentTimeMillis()),
                ClaimLuaProtocol.CODE_NOT_ACTIVE_ARG,
                ClaimLuaProtocol.CODE_EXPIRED_ACTIVE_ARG,
                userId
        );
        return mapLuaResult(eventId, userId, rawResult);
    }

    private ClaimResult mapLuaResult(String eventId, String userId, List<?> rawResult) {
        if (rawResult == null || rawResult.size() < ClaimLuaProtocol.RESULT_SIZE) {
            throw new IllegalStateException("claim lua result must contain [code, remaining]");
        }

        long code = asLong(rawResult.get(ClaimLuaProtocol.CODE_INDEX), "code");
        if (code == ClaimLuaProtocol.CODE_EXPIRED_ACTIVE) {
            throw new EventNotOpenException("입장 가능 시간이 만료되었습니다. 다시 대기열에 참여해주세요.");
        }
        if (code == ClaimLuaProtocol.CODE_NOT_ACTIVE) {
            throw new EventNotOpenException("유의사항 화면 진입 후 예매 가능합니다.");
        }

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

    @Override
    public void rollback(String eventId, String userId) {
        String stockKey = TicketRedisKeys.stockKey(eventId);
        String userKey = TicketRedisKeys.userKey(eventId, userId);
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        List<String> keys = List.of(stockKey, userKey, statusKey);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                stringRedisTemplate.execute(claimRollbackScript, keys);
                log.info("claim_rollback 성공 eventId={} userId={} attempt={}", eventId, userId, attempt);
                return;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("claim_rollback 재시도 {}/{} eventId={} userId={}", attempt, maxAttempts, eventId, userId, e);
                } else {
                    log.error("claim_rollback 최종 실패 — stock 수동 보정 필요 eventId={} userId={}", eventId, userId, e);
                }
            }
        }
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
