package com.danzzan.global.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JwtRevocationService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:access:";
    private static final String WITHDRAWN_USER_PREFIX = "auth:withdrawn-user:";
    private static final String USER_VERSION_PREFIX = "auth:user-version:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

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

    public void cacheUserVersion(Long userId, int tokenVersion) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                userVersionKey(userId),
                String.valueOf(tokenVersion),
                Duration.ofMillis(refreshTokenExpirationMs)
        );
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
}
