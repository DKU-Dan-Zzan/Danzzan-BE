package com.danzzan.domain.user.phoneverification.dto.response;

import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ResponsePhoneVerificationCreateDto {
    private final String sessionId;
    private final PhoneVerificationStatus status;
    private final String octomoReceiveNumber;
    private final String messageBody;
    private final long expiresInSec;
    private final long statusPollHintSec;
    private final LocalDateTime expiresAt;
}
