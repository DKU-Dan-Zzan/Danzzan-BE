package com.danzzan.domain.user.passwordreset.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class PasswordResetRedisRepository {

    private final StringRedisTemplate redisTemplate;

    @Qualifier("passwordResetConsumeScript")
    private final RedisScript<List> passwordResetConsumeScript;

    public void saveRequest(PasswordResetRequestState state, long ttlSec) {
        String key = PasswordResetRedisKeys.requestKey(state.requestId());
        Map<String, String> hash = new HashMap<>();
        hash.put(PasswordResetRequestState.FIELD_REQUEST_ID, state.requestId());
        hash.put(PasswordResetRequestState.FIELD_STUDENT_ID, state.studentId());
        hash.put(PasswordResetRequestState.FIELD_CODE_HASH, state.codeHash());
        hash.put(PasswordResetRequestState.FIELD_VERIFY_ATTEMPTS, String.valueOf(state.verifyAttempts()));
        hash.put(PasswordResetRequestState.FIELD_VERIFIED, state.verified() ? "1" : "0");
        hash.put(PasswordResetRequestState.FIELD_CONSUMED, state.consumed() ? "1" : "0");
        hash.put(PasswordResetRequestState.FIELD_USER_EXISTS, state.userExists() ? "1" : "0");
        if (state.verificationTokenHash() != null) {
            hash.put(PasswordResetRequestState.FIELD_VERIFICATION_TOKEN_HASH, state.verificationTokenHash());
        }
        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSec));
    }

    public Optional<PasswordResetRequestState> findRequest(String requestId) {
        String key = PasswordResetRedisKeys.requestKey(requestId);
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PasswordResetRequestState.fromHash(raw));
    }

    public long getRequestTtlSec(String requestId) {
        String key = PasswordResetRedisKeys.requestKey(requestId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null) {
            return -2L;
        }
        return ttl;
    }

    public long incrementVerifyAttempts(String requestId) {
        String key = PasswordResetRedisKeys.requestKey(requestId);
        Long updated = redisTemplate.opsForHash()
                .increment(key, PasswordResetRequestState.FIELD_VERIFY_ATTEMPTS, 1L);
        return updated == null ? 0L : updated;
    }

    public void markVerified(String requestId, String verificationTokenHash, long ttlSec) {
        String key = PasswordResetRedisKeys.requestKey(requestId);
        redisTemplate.opsForHash().put(key, PasswordResetRequestState.FIELD_VERIFIED, "1");
        redisTemplate.opsForHash().put(key, PasswordResetRequestState.FIELD_CONSUMED, "0");
        redisTemplate.opsForHash().put(key, PasswordResetRequestState.FIELD_VERIFICATION_TOKEN_HASH, verificationTokenHash);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSec));
    }

    public void setResendCooldown(String studentId, long cooldownSec) {
        String key = PasswordResetRedisKeys.cooldownKey(studentId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(cooldownSec));
    }

    public long getResendCooldownSec(String studentId) {
        String key = PasswordResetRedisKeys.cooldownKey(studentId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null) {
            return -2L;
        }
        return ttl;
    }

    public long incrementRequestRate(String studentId, String clientIp, long rateWindowSec) {
        String key = PasswordResetRedisKeys.requestRateKey(studentId, clientIp);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(rateWindowSec));
        }
        return count == null ? 0L : count;
    }

    public PasswordResetConsumeResult consumeVerifiedToken(String requestId, String verificationTokenHash) {
        String key = PasswordResetRedisKeys.requestKey(requestId);
        List<?> raw = redisTemplate.execute(
                passwordResetConsumeScript,
                List.of(key),
                verificationTokenHash
        );

        if (raw == null || raw.isEmpty()) {
            return PasswordResetConsumeResult.fail(PasswordResetConsumeResult.ConsumeStatus.REQUEST_NOT_FOUND);
        }

        long success = asLong(raw.get(0), "success");
        if (success == 1L) {
            String studentId = asString(raw.size() > 1 ? raw.get(1) : null);
            boolean userExists = "1".equals(asString(raw.size() > 2 ? raw.get(2) : null));
            return PasswordResetConsumeResult.success(studentId, userExists);
        }

        String reason = asString(raw.size() > 1 ? raw.get(1) : null);
        if ("REQUEST_NOT_FOUND".equals(reason)) {
            return PasswordResetConsumeResult.fail(PasswordResetConsumeResult.ConsumeStatus.REQUEST_NOT_FOUND);
        }
        if ("TOKEN_NOT_VERIFIED".equals(reason)) {
            return PasswordResetConsumeResult.fail(PasswordResetConsumeResult.ConsumeStatus.TOKEN_NOT_VERIFIED);
        }
        if ("TOKEN_ALREADY_CONSUMED".equals(reason)) {
            return PasswordResetConsumeResult.fail(PasswordResetConsumeResult.ConsumeStatus.TOKEN_ALREADY_CONSUMED);
        }
        return PasswordResetConsumeResult.fail(PasswordResetConsumeResult.ConsumeStatus.INVALID_TOKEN);
    }

    private long asLong(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        throw new IllegalStateException(fieldName + " must be number-like");
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }
}
