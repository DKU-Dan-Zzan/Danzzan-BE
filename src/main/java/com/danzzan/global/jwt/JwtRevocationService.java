package com.danzzan.global.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtRevocationService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:access:";
    private static final String WITHDRAWN_USER_PREFIX = "auth:withdrawn-user:";
    private static final String USER_VERSION_PREFIX = "auth:user-version:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final CommittedUserVersionReader committedUserVersionReader;

    @Value("${jwt.access-token-expiration:3600000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpirationMs;

    public void blacklistAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        long ttlMs = accessTokenTtlMs(accessToken);
        if (ttlMs <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(
                blacklistKey(accessToken),
                "1",
                Duration.ofMillis(ttlMs)
        );
    }

    public boolean isBlacklisted(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(accessToken)));
    }

    public void markWithdrawnUser(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                withdrawnUserKey(userId),
                "1",
                Duration.ofMillis(refreshTokenExpirationMs)
        );
    }

    public boolean isWithdrawnUser(Long userId) {
        if (userId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(withdrawnUserKey(userId)));
    }

    public void clearWithdrawnUser(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(withdrawnUserKey(userId));
    }

    public void cacheUserVersion(Long userId, int tokenVersion) {
        publishUserVersionFromCommittedDatabase(userId, "cache");
    }

    /**
     * Publishes a database-confirmed version only after the surrounding transaction commits.
     * Redis is an optimization, so publication failures never roll back a completed user write.
     */
    public void publishUserVersionAfterCommit(Long userId, int tokenVersion) {
        runAfterCommit(userId, "user-version", () ->
                publishUserVersionFromCommittedDatabase(userId, "user-version"));
    }

    /** Executes external cache side effects only after a user transaction commits. */
    public void runAfterCommit(Long userId, String phase, Runnable action) {
        if (userId == null || action == null) {
            return;
        }
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("jwt side-effect failed userId={} phase={}", userId, phase);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeAction.run();
                }
            });
            return;
        }
        safeAction.run();
    }

    private void publishUserVersionFromCommittedDatabase(Long userId, String phase) {
        if (userId == null) {
            return;
        }
        try {
            Optional<Integer> currentVersion = committedUserVersionReader.findActiveTokenVersion(userId);
            if (currentVersion.isEmpty()) {
                return;
            }
            redisTemplate.execute(monotonicVersionScript(),
                    List.of(userVersionKey(userId)),
                    String.valueOf(currentVersion.get()),
                    String.valueOf(refreshTokenExpirationMs));
        } catch (RuntimeException e) {
            log.warn("jwt user-version publish failed userId={} phase={}", userId, phase);
        }
    }

    public Optional<Integer> getCachedUserVersion(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String raw = redisTemplate.opsForValue().get(userVersionKey(userId));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private long accessTokenTtlMs(String accessToken) {
        try {
            Claims claims = jwtTokenProvider.getClaimsFromExpiredToken(accessToken);
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return accessTokenExpirationMs;
            }
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception ignored) {
            return accessTokenExpirationMs;
        }
    }

    private String blacklistKey(String accessToken) {
        return BLACKLIST_PREFIX + sha256(accessToken);
    }

    private String withdrawnUserKey(Long userId) {
        return WITHDRAWN_USER_PREFIX + userId;
    }

    private String userVersionKey(Long userId) {
        return USER_VERSION_PREFIX + userId;
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private DefaultRedisScript<Long> monotonicVersionScript() {
        return new DefaultRedisScript<>("""
                local current = redis.call('GET', KEYS[1])
                local candidate = tonumber(ARGV[1])
                if (not current) or candidate >= tonumber(current) then
                    redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
                    return 1
                end
                return 0
                """, Long.class);
    }
}
