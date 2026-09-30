package com.danzzan.global.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성
    // userId, studentId, role을 클레임에 포함
    public String createAccessToken(Long userId, String studentId, String role, int tokenVersion) {
        return createAccessToken(userId, studentId, role, tokenVersion, List.of());
    }

    public String createAccessToken(Long userId, String studentId, String role, int tokenVersion, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("studentId", studentId)
                .claim("role", role)
                .claim("permissions", permissions == null ? List.of() : List.copyOf(permissions))
                .claim("tokenVersion", tokenVersion)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key)
                .compact();
    }

    // Refresh Token 생성
    // userId만 포함 (최소한의 정보)
    public String createRefreshToken(Long userId, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("tokenVersion", tokenVersion)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key)
                .compact();
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        return getUserId(getValidClaims(token));
    }

    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    // 토큰에서 role 추출
    public String getRole(String token) {
        return getRole(getValidClaims(token));
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    // 토큰에서 studentId 추출
    public String getStudentId(String token) {
        return getStudentId(getValidClaims(token));
    }

    public String getStudentId(Claims claims) {
        return claims.get("studentId", String.class);
    }

    public int getTokenVersion(String token) {
        return getTokenVersion(getValidClaims(token));
    }

    public int getTokenVersion(Claims claims) {
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);
        return tokenVersion == null ? 0 : tokenVersion;
    }

    public List<String> getPermissions(Claims claims) {
        Object value = claims.get("permissions");
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            getValidClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 만료된 토큰에서도 클레임 추출 (토큰 재발급 시 사용)
    public Claims getClaimsFromExpiredToken(String token) {
        try {
            return getValidClaims(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public Claims getValidClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
