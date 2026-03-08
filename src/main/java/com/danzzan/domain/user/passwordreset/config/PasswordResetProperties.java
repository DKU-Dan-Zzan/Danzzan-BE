package com.danzzan.domain.user.passwordreset.config;

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
@ConfigurationProperties(prefix = "password-reset")
public class PasswordResetProperties {

    @Min(60)
    private long codeTtlSec = 300;

    @Min(60)
    private long verifyTokenTtlSec = 600;

    @Min(1)
    private int maxVerifyAttempts = 5;

    @Min(1)
    private long resendCooldownSec = 60;

    @Min(1)
    private int maxRequestPerWindow = 10;

    @Min(60)
    private long requestRateWindowSec = 600;

    @Valid
    private Mail mail = new Mail();

    @Getter
    @Setter
    public static class Mail {
        @NotBlank
        private String subject;

        @NotBlank
        private String from;
    }
}
