package com.danzzan.domain.user.passwordreset.service;

import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.passwordreset.config.PasswordResetProperties;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetDto;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.exception.PasswordResetErrorType;
import com.danzzan.domain.user.passwordreset.exception.PasswordResetException;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetConsumeResult;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetRedisRepository;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetRequestState;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.domain.user.service.UserInfoService;
import com.danzzan.global.jwt.JwtRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_SCHOOL_EMAIL_DOMAIN = "@dankook.ac.kr";

    private final PasswordResetProperties properties;
    private final PasswordResetRedisRepository passwordResetRedisRepository;
    private final PasswordResetMailService passwordResetMailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserInfoService userInfoService;
    private final JwtRevocationService jwtRevocationService;

    @Transactional
    public ResponsePasswordResetRequestDto requestReset(RequestPasswordResetRequestDto dto, String clientIp) {
        String studentId = dto.getStudentId().trim();
        String normalizedIp = normalizeClientIp(clientIp);
        enforceRequestRateLimit(studentId, normalizedIp);
        enforceResendCooldown(studentId);

        Optional<User> userOptional = userRepository.findByStudentId(studentId);
        if (userOptional.isEmpty()) {
            throw new PasswordResetException(PasswordResetErrorType.USER_NOT_FOUND);
        }

        String requestId = UUID.randomUUID().toString();
        String code = generateVerificationCode();

        PasswordResetRequestState state = new PasswordResetRequestState(
                requestId,
                studentId,
                hash(requestId, code),
                0,
                false,
                null,
                false,
                true
        );
        passwordResetRedisRepository.saveRequest(state, properties.getCodeTtlSec());
        passwordResetRedisRepository.setResendCooldown(studentId, properties.getResendCooldownSec());

        String recipientEmail = resolveRecipientEmail(studentId, dto.getEmail(), requestId);
        if (recipientEmail != null) {
            sendMailSafely(recipientEmail, code, requestId);
        }

        log.info("password_reset request created requestId={} userExists={} ipHash={}",
                requestId, userOptional.isPresent(), hashWithoutRequest(normalizedIp));
        return new ResponsePasswordResetRequestDto(requestId, properties.getCodeTtlSec());
    }

    @Transactional
    public ResponsePasswordResetVerifyDto verifyCode(RequestPasswordResetVerifyDto dto) {
        String requestId = dto.getRequestId().trim();
        PasswordResetRequestState state = passwordResetRedisRepository.findRequest(requestId)
                .orElseThrow(() -> new PasswordResetException(PasswordResetErrorType.REQUEST_NOT_FOUND));

        if (passwordResetRedisRepository.getRequestTtlSec(requestId) <= 0) {
            throw new PasswordResetException(PasswordResetErrorType.CODE_EXPIRED);
        }

        if (state.verifyAttempts() >= properties.getMaxVerifyAttempts()) {
            throw new PasswordResetException(PasswordResetErrorType.TOO_MANY_ATTEMPTS);
        }

        String expectedCodeHash = hash(requestId, dto.getCode().trim());
        if (!expectedCodeHash.equals(state.codeHash())) {
            long updatedAttempts = passwordResetRedisRepository.incrementVerifyAttempts(requestId);
            if (updatedAttempts >= properties.getMaxVerifyAttempts()) {
                throw new PasswordResetException(PasswordResetErrorType.TOO_MANY_ATTEMPTS);
            }
            throw new PasswordResetException(PasswordResetErrorType.CODE_MISMATCH);
        }

        String verificationToken = generateVerificationToken();
        passwordResetRedisRepository.markVerified(
                requestId,
                hash(requestId, verificationToken),
                properties.getVerifyTokenTtlSec()
        );

        log.info("password_reset code verified requestId={}", requestId);
        return new ResponsePasswordResetVerifyDto(verificationToken);
    }

    @Transactional
    public void resetPassword(RequestPasswordResetDto dto) {
        String requestId = dto.getRequestId().trim();
        PasswordResetConsumeResult consumeResult = passwordResetRedisRepository.consumeVerifiedToken(
                requestId,
                hash(requestId, dto.getVerificationToken().trim())
        );

        switch (consumeResult.status()) {
            case REQUEST_NOT_FOUND -> throw new PasswordResetException(PasswordResetErrorType.REQUEST_NOT_FOUND);
            case TOKEN_NOT_VERIFIED -> throw new PasswordResetException(PasswordResetErrorType.TOKEN_NOT_VERIFIED);
            case INVALID_TOKEN -> throw new PasswordResetException(PasswordResetErrorType.INVALID_VERIFICATION_TOKEN);
            case TOKEN_ALREADY_CONSUMED ->
                    throw new PasswordResetException(PasswordResetErrorType.TOKEN_ALREADY_CONSUMED);
            case SUCCESS -> updatePassword(consumeResult.studentId(), consumeResult.userExists(), dto.getNewPassword());
        }

        log.info("password_reset completed requestId={}", requestId);
    }

    private void updatePassword(String studentId, boolean userExists, String rawNewPassword) {
        if (!userExists) {
            throw new PasswordResetException(PasswordResetErrorType.USER_NOT_FOUND);
        }

        User user = userRepository.findByStudentIdAndDeletedFalseForUpdate(studentId)
                .orElseThrow(() -> new PasswordResetException(PasswordResetErrorType.USER_NOT_FOUND));

        user.changePassword(passwordEncoder.encode(rawNewPassword));
        user.bumpTokenVersion();
        Long userId = user.getId();
        int tokenVersion = user.getTokenVersion();
        jwtRevocationService.runAfterCommit(userId, "password-reset", () -> {
            userInfoService.invalidateUserInfo(userId);
            jwtRevocationService.cacheUserVersion(userId, tokenVersion);
        });
    }

    private void enforceRequestRateLimit(String studentId, String clientIp) {
        long count = passwordResetRedisRepository.incrementRequestRate(
                studentId,
                clientIp,
                properties.getRequestRateWindowSec()
        );
        if (count > properties.getMaxRequestPerWindow()) {
            throw new PasswordResetException(PasswordResetErrorType.REQUEST_RATE_LIMITED);
        }
    }

    private void enforceResendCooldown(String studentId) {
        long cooldownLeftSec = passwordResetRedisRepository.getResendCooldownSec(studentId);
        if (cooldownLeftSec > 0) {
            throw new PasswordResetException(
                    PasswordResetErrorType.RESEND_COOLDOWN,
                    "인증코드 재요청 쿨다운 중입니다. " + cooldownLeftSec + "초 후 다시 시도해주세요."
            );
        }
    }

    private String resolveRecipientEmail(String studentId, String email, String requestId) {
        String expectedEmail = studentId + DEFAULT_SCHOOL_EMAIL_DOMAIN;
        if (!StringUtils.hasText(email)) {
            return expectedEmail;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (expectedEmail.equalsIgnoreCase(normalized)) {
            return normalized;
        }

        // 요청 응답은 동일하게 유지하고 메일만 발송하지 않는다.
        log.info("password_reset request email mismatch requestId={}", requestId);
        return null;
    }

    private void sendMailSafely(String recipientEmail, String code, String requestId) {
        try {
            passwordResetMailService.sendVerificationCode(recipientEmail, code, properties.getCodeTtlSec(), requestId);
        } catch (Exception e) {
            // 계정 존재 여부 노출을 피하기 위해 메일 발송 실패도 동일한 API 응답을 유지한다.
            log.warn("password_reset mail send failed requestId={} reason={}", requestId, e.getMessage());
        }
    }

    private String generateVerificationCode() {
        return "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String requestId, String raw) {
        return sha256(requestId + ":" + raw);
    }

    private String hashWithoutRequest(String raw) {
        return sha256(raw);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String normalizeClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        return clientIp.trim();
    }
}
