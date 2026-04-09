package com.danzzan.domain.user.phoneverification.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "phone_verification_session",
        indexes = {
                @Index(name = "idx_phone_verification_session_signup_token", columnList = "signup_token"),
                @Index(name = "idx_phone_verification_session_status_expires_at", columnList = "status, expires_at"),
                @Index(name = "idx_phone_verification_session_request_ip_hash_created_at", columnList = "request_ip_hash, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_phone_verification_session_session_id", columnNames = "session_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "signup_token", nullable = false, length = 64)
    private String signupToken;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "code_ciphertext", nullable = false, length = 512)
    private String codeCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhoneVerificationStatus status;

    @Column(name = "verified_phone_number", length = 32)
    private String verifiedPhoneNumber;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "request_ip_hash", length = 128)
    private String requestIpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PhoneVerificationSession(
            String sessionId,
            String signupToken,
            String codeHash,
            String codeCiphertext,
            PhoneVerificationStatus status,
            String verifiedPhoneNumber,
            int attemptCount,
            String requestIpHash,
            LocalDateTime expiresAt,
            LocalDateTime verifiedAt,
            LocalDateTime consumedAt,
            LocalDateTime lastCheckedAt
    ) {
        this.sessionId = sessionId;
        this.signupToken = signupToken;
        this.codeHash = codeHash;
        this.codeCiphertext = codeCiphertext;
        this.status = status;
        this.verifiedPhoneNumber = verifiedPhoneNumber;
        this.attemptCount = attemptCount;
        this.requestIpHash = requestIpHash;
        this.expiresAt = expiresAt;
        this.verifiedAt = verifiedAt;
        this.consumedAt = consumedAt;
        this.lastCheckedAt = lastCheckedAt;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static PhoneVerificationSession pending(
            String sessionId,
            String signupToken,
            String codeHash,
            String codeCiphertext,
            String requestIpHash,
            LocalDateTime expiresAt
    ) {
        return PhoneVerificationSession.builder()
                .sessionId(sessionId)
                .signupToken(signupToken)
                .codeHash(codeHash)
                .codeCiphertext(codeCiphertext)
                .status(PhoneVerificationStatus.PENDING)
                .attemptCount(0)
                .requestIpHash(requestIpHash)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now) || expiresAt.isEqual(now);
    }

    public boolean isTerminalStatus() {
        return status == PhoneVerificationStatus.EXPIRED
                || status == PhoneVerificationStatus.FAILED
                || status == PhoneVerificationStatus.CONSUMED;
    }

    public void markChecked(LocalDateTime checkedAt) {
        this.lastCheckedAt = checkedAt;
    }

    public void incrementAttempt() {
        this.attemptCount += 1;
    }

    public void markVerified(String phoneNumber, LocalDateTime verifiedAt) {
        this.status = PhoneVerificationStatus.VERIFIED;
        this.verifiedPhoneNumber = phoneNumber;
        this.verifiedAt = verifiedAt;
        this.lastCheckedAt = verifiedAt;
    }

    public void markExpired(LocalDateTime expiredAt) {
        this.status = PhoneVerificationStatus.EXPIRED;
        this.lastCheckedAt = expiredAt;
    }

    public void markFailed(LocalDateTime failedAt) {
        this.status = PhoneVerificationStatus.FAILED;
        this.lastCheckedAt = failedAt;
    }

    public void markConsumed(LocalDateTime consumedAt) {
        this.status = PhoneVerificationStatus.CONSUMED;
        this.consumedAt = consumedAt;
    }
}
