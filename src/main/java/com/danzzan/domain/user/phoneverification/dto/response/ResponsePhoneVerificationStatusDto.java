package com.danzzan.domain.user.phoneverification.dto.response;

import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ResponsePhoneVerificationStatusDto {
    private final String sessionId;
    private final PhoneVerificationStatus status;
    private final int attemptCount;
    private final long expiresInSec;
    private final LocalDateTime expiresAt;
    private final LocalDateTime verifiedAt;
    private final String verifiedPhoneNumberMasked;
}
