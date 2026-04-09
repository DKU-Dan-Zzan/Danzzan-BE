package com.danzzan.domain.user.phoneverification.scheduler;

import com.danzzan.domain.user.phoneverification.service.PhoneVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneVerificationCleanupScheduler {

    private final PhoneVerificationService phoneVerificationService;

    @Scheduled(fixedDelayString = "${phone-verification.scheduler-fixed-delay-ms:60000}")
    public void cleanupExpiredSessions() {
        int expiredCount = phoneVerificationService.expireSessions();
        int deletedCount = phoneVerificationService.cleanupRetainedSessions();

        if (expiredCount > 0 || deletedCount > 0) {
            log.info("phone verification cleanup finished expired={} deleted={}", expiredCount, deletedCount);
        }
    }
}
