package com.danzzan.domain.user.phoneverification.service;

import com.danzzan.domain.auth.service.SignupTokenStore;
import com.danzzan.domain.user.phoneverification.config.PhoneVerificationProperties;
import com.danzzan.domain.user.phoneverification.dto.response.ResponsePhoneVerificationCreateDto;
import com.danzzan.domain.user.phoneverification.dto.response.ResponsePhoneVerificationStatusDto;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationErrorType;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationException;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationSession;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import com.danzzan.domain.user.phoneverification.repository.PhoneVerificationSessionRepository;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.infra.octomo.OctomoMessageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhoneVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final SignupTokenStore signupTokenStore;
    private final UserRepository userRepository;
    private final PhoneVerificationSessionRepository phoneVerificationSessionRepository;
    private final PhoneVerificationProperties properties;
    private final OctomoMessageClient octomoMessageClient;

    @Transactional
    public ResponsePhoneVerificationCreateDto createSession(String signupToken, String clientIp) {
        signupTokenStore.getCachedStudentInfo(signupToken);

        LocalDateTime now = LocalDateTime.now();
        String normalizedIpHash = sha256(normalizeClientIp(clientIp));
        enforceCreateRateLimit(signupToken, normalizedIpHash, now);
        enforceCreateCooldown(signupToken, now);

        String sessionId = UUID.randomUUID().toString();
        String rawCode = generateVerificationCode();
        LocalDateTime expiresAt = now.plusSeconds(properties.getCodeTtlSec());

        PhoneVerificationSession session = PhoneVerificationSession.pending(
                sessionId,
                signupToken,
                hash(sessionId, rawCode),
                encryptCode(rawCode),
                normalizedIpHash,
                expiresAt
        );
        phoneVerificationSessionRepository.save(session);
        return ResponsePhoneVerificationCreateDto.builder()
                .sessionId(sessionId)
                .status(session.getStatus())
                .octomoReceiveNumber(properties.getOctomo().getReceiveNumber())
                .messageBody(rawCode)
                .expiresInSec(properties.getCodeTtlSec())
                .statusPollHintSec(properties.getStatusPollHintSec())
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public ResponsePhoneVerificationStatusDto verifySession(String sessionId, String phoneNumber) {
        PhoneVerificationSession session = getSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        String rawCode = decryptCode(session.getCodeCiphertext());

        syncExpiredStatus(session, now);
        if (session.getStatus() == PhoneVerificationStatus.EXPIRED) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.SESSION_EXPIRED);
        }
        if (session.getStatus() == PhoneVerificationStatus.CONSUMED) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.SESSION_ALREADY_CONSUMED);
        }
        if (session.getStatus() == PhoneVerificationStatus.VERIFIED) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.ALREADY_VERIFIED);
        }
        if (session.getAttemptCount() >= properties.getMaxVerifyAttempts()) {
            session.markFailed(now);
            throw new PhoneVerificationException(PhoneVerificationErrorType.TOO_MANY_ATTEMPTS);
        }

        boolean verified;
        try {
            verified = octomoMessageClient.existsRecentMessage(normalizedPhoneNumber, rawCode);
        } catch (Exception e) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.OCTOMO_LOOKUP_FAILED);
        }

        session.markChecked(now);
        if (verified) {
            if (userRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
                session.markFailed(now);
                throw new PhoneVerificationException(PhoneVerificationErrorType.PHONE_ALREADY_LINKED);
            }
            session.markVerified(normalizedPhoneNumber, now);
            return toStatusDto(session, now);
        }

        session.incrementAttempt();
        if (session.getAttemptCount() >= properties.getMaxVerifyAttempts()) {
            session.markFailed(now);
            throw new PhoneVerificationException(PhoneVerificationErrorType.TOO_MANY_ATTEMPTS);
        }
        throw new PhoneVerificationException(PhoneVerificationErrorType.MESSAGE_NOT_FOUND);
    }

    @Transactional
    public ResponsePhoneVerificationStatusDto getStatus(String sessionId) {
        PhoneVerificationSession session = getSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        syncExpiredStatus(session, now);
        return toStatusDto(session, now);
    }

    @Transactional
    public String consumeVerifiedPhoneNumber(String signupToken, String sessionId) {
        PhoneVerificationSession session = getSession(sessionId);
        LocalDateTime now = LocalDateTime.now();

        if (!session.getSignupToken().equals(signupToken)) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.SESSION_NOT_FOUND);
        }

        syncExpiredStatus(session, now);
        if (session.getStatus() == PhoneVerificationStatus.EXPIRED) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.SESSION_EXPIRED);
        }
        if (session.getStatus() == PhoneVerificationStatus.CONSUMED) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.SESSION_ALREADY_CONSUMED);
        }
        if (session.getStatus() != PhoneVerificationStatus.VERIFIED) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.NOT_VERIFIED);
        }

        String verifiedPhoneNumber = session.getVerifiedPhoneNumber();
        if (userRepository.existsByPhoneNumber(verifiedPhoneNumber)) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.PHONE_ALREADY_LINKED);
        }

        session.markConsumed(now);
        return verifiedPhoneNumber;
    }

    @Transactional
    public int expireSessions() {
        return phoneVerificationSessionRepository.expireSessions(
                List.of(PhoneVerificationStatus.PENDING, PhoneVerificationStatus.VERIFIED),
                PhoneVerificationStatus.EXPIRED,
                LocalDateTime.now()
        );
    }

    @Transactional
    public int cleanupRetainedSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(properties.getCleanupRetentionSec());
        return phoneVerificationSessionRepository.deleteRetainedSessions(
                List.of(
                        PhoneVerificationStatus.EXPIRED,
                        PhoneVerificationStatus.FAILED,
                        PhoneVerificationStatus.CONSUMED
                ),
                cutoff
        );
    }

    private PhoneVerificationSession getSession(String sessionId) {
        return phoneVerificationSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new PhoneVerificationException(PhoneVerificationErrorType.SESSION_NOT_FOUND));
    }

    private void enforceCreateRateLimit(String signupToken, String requestIpHash, LocalDateTime now) {
        LocalDateTime rateWindowStart = now.minusSeconds(properties.getCreateRateWindowSec());
        long bySignupToken = phoneVerificationSessionRepository.countBySignupTokenAndCreatedAtAfter(signupToken, rateWindowStart);
        long byIp = phoneVerificationSessionRepository.countByRequestIpHashAndCreatedAtAfter(requestIpHash, rateWindowStart);
        if (bySignupToken >= properties.getMaxCreateRequestsPerWindow()
                || byIp >= properties.getMaxCreateRequestsPerWindow()) {
            throw new PhoneVerificationException(PhoneVerificationErrorType.CREATE_RATE_LIMITED);
        }
    }

    private void enforceCreateCooldown(String signupToken, LocalDateTime now) {
        phoneVerificationSessionRepository.findTopBySignupTokenOrderByCreatedAtDesc(signupToken)
                .ifPresent(lastSession -> {
                    long cooldownLeft = properties.getCreateCooldownSec()
                            - Duration.between(lastSession.getCreatedAt(), now).getSeconds();
                    if (cooldownLeft > 0 && !lastSession.isTerminalStatus()) {
                        throw new PhoneVerificationException(
                                PhoneVerificationErrorType.CREATE_COOLDOWN,
                                "새 인증 세션은 " + cooldownLeft + "초 후에 다시 생성할 수 있습니다."
                        );
                    }
                });
    }

    private void syncExpiredStatus(PhoneVerificationSession session, LocalDateTime now) {
        if ((session.getStatus() == PhoneVerificationStatus.PENDING || session.getStatus() == PhoneVerificationStatus.VERIFIED)
                && session.isExpired(now)) {
            session.markExpired(now);
        }
    }

    private ResponsePhoneVerificationStatusDto toStatusDto(PhoneVerificationSession session, LocalDateTime now) {
        long expiresInSec = Math.max(0, Duration.between(now, session.getExpiresAt()).getSeconds());
        return ResponsePhoneVerificationStatusDto.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .attemptCount(session.getAttemptCount())
                .expiresInSec(expiresInSec)
                .expiresAt(session.getExpiresAt())
                .verifiedAt(session.getVerifiedAt())
                .verifiedPhoneNumberMasked(maskPhoneNumber(session.getVerifiedPhoneNumber()))
                .build();
    }

    private String generateVerificationCode() {
        return "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return phoneNumber;
        }
        return phoneNumber.replaceAll("[^0-9+]", "");
    }

    private String hash(String sessionId, String rawValue) {
        return sha256(sessionId + ":" + rawValue);
    }

    private String encryptCode(String rawCode) {
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(rawCode.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt verification code", e);
        }
    }

    private String decryptCode(String codeCiphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(codeCiphertext);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, 12, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt verification code", e);
        }
    }

    private SecretKeySpec secretKey() {
        if (!StringUtils.hasText(properties.getCodeEncryptionSecret())) {
            throw new IllegalStateException("PHONE_VERIFICATION_CODE_ENCRYPTION_SECRET is required");
        }
        byte[] key = sha256Bytes(properties.getCodeEncryptionSecret());
        return new SecretKeySpec(key, "AES");
    }

    private String sha256(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private byte[] sha256Bytes(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
