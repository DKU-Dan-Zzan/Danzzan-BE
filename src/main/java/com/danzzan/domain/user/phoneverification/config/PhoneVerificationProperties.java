package com.danzzan.domain.user.phoneverification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "phone-verification")
public class PhoneVerificationProperties {

    @Min(60)
    private long codeTtlSec = 300;

    @Min(1)
    private int maxVerifyAttempts = 5;

    @Min(1)
    private long createCooldownSec = 60;

    @Min(1)
    private int maxCreateRequestsPerWindow = 5;

    @Min(60)
    private long createRateWindowSec = 600;

    @Min(60)
    private long cleanupRetentionSec = 86400;

    @Min(10)
    private long schedulerFixedDelayMs = 60000;

    @Min(1)
    private int messageQueryLimit = 100;

    @Min(60)
    private long messageLookbackSec = 600;

    @Min(1)
    private long statusPollHintSec = 5;

    @NotBlank
    private String messageTemplate = "[DANZZAN] %s";

    private String codeEncryptionSecret;

    @Valid
    private Octomo octomo = new Octomo();

    @Getter
    @Setter
    public static class Octomo {
        @NotBlank
        private String receiveNumber;
    }
}
