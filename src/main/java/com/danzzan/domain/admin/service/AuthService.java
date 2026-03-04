package com.danzzan.domain.admin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.danzzan.domain.admin.entity.AdminEntity;
import com.danzzan.domain.admin.entity.RefreshTokenEntity;
import com.danzzan.domain.admin.repository.AdminRepository;
import com.danzzan.domain.admin.repository.RefreshTokenRepository;
import com.danzzan.global.exception.AuthException;
import com.danzzan.global.jwt.JwtProvider;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository adminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Value("${jwt.refresh-validity-ms}")
    private long refreshValidityMs;

    public String login(String studentNumber, String password, HttpServletResponse response) {
        AdminEntity admin = adminRepository.findByStudentNumber(studentNumber)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "관리자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtProvider.createAccessToken(admin.getId());
        String refreshToken = jwtProvider.createRefreshToken(admin.getId());
        String hashedRefresh = hashRefreshToken(refreshToken);

        LocalDateTime expiryDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis() + refreshValidityMs),
                ZoneId.systemDefault()
        );
        refreshTokenRepository.save(
                new RefreshTokenEntity(admin.getId(), hashedRefresh, expiryDate)
        );

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshValidityMs / 1000)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());

        return accessToken;
    }

    public String reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 없습니다.");
        }
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다.");
        }

        String hashed = hashRefreshToken(refreshToken);
        RefreshTokenEntity stored = refreshTokenRepository.findByToken(hashed)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "저장된 Refresh Token이 없습니다."));

        if (stored.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다.");
        }

        return jwtProvider.createAccessToken(stored.getAdminId());
    }

    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String hashed = hashRefreshToken(refreshToken);
            refreshTokenRepository.findByToken(hashed).ifPresent(refreshTokenRepository::delete);
        }
        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", clearCookie.toString());
    }

    private static String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
