package com.danzzan.domain.user.passwordreset.service;

import com.danzzan.domain.user.passwordreset.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    public void sendVerificationCode(String recipientEmail, String code, long expiresInSec, String requestId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(recipientEmail);
        message.setSubject(properties.getMail().getSubject());
        message.setText(buildBody(code, expiresInSec, requestId));

        mailSender.send(message);
        log.info("password_reset mail sent requestId={}", requestId);
    }

    private String buildBody(String code, long expiresInSec, String requestId) {
        long expiresInMin = Math.max(1L, expiresInSec / 60L);
        return """
                안녕하세요. Danzzan 비밀번호 재설정 인증코드 안내드립니다.

                인증코드: %s
                유효시간: %d분
                요청 ID: %s

                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(code, expiresInMin, requestId);
    }
}
