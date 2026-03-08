package com.danzzan.domain.user.passwordreset.service;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetRedisRepository passwordResetRedisRepository;

    @Mock
    private PasswordResetMailService passwordResetMailService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserInfoService userInfoService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setCodeTtlSec(300);
        properties.setVerifyTokenTtlSec(600);
        properties.setMaxVerifyAttempts(5);
        properties.setResendCooldownSec(60);
        properties.setMaxRequestPerWindow(10);
        properties.setRequestRateWindowSec(600);
        PasswordResetProperties.Mail mail = new PasswordResetProperties.Mail();
        mail.setSubject("비밀번호 재설정 인증코드");
        mail.setFrom("noreply@danzzan.com");
        properties.setMail(mail);

        passwordResetService = new PasswordResetService(
                properties,
                passwordResetRedisRepository,
                passwordResetMailService,
                userRepository,
                passwordEncoder,
                userInfoService
        );
    }

    @Test
    void requestResetSuccess() {
        RequestPasswordResetRequestDto dto = new RequestPasswordResetRequestDto("32100000", "32100000@dankook.ac.kr");
        User user = sampleUser();

        when(passwordResetRedisRepository.incrementRequestRate(eq("32100000"), anyString(), eq(600L))).thenReturn(1L);
        when(passwordResetRedisRepository.getResendCooldownSec("32100000")).thenReturn(-2L);
        when(userRepository.findByStudentId("32100000")).thenReturn(Optional.of(user));

        ResponsePasswordResetRequestDto response = passwordResetService.requestReset(dto, "127.0.0.1");

        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getExpiresInSec()).isEqualTo(300L);

        verify(passwordResetRedisRepository).saveRequest(any(PasswordResetRequestState.class), eq(300L));
        verify(passwordResetRedisRepository).setResendCooldown("32100000", 60L);
        verify(passwordResetMailService).sendVerificationCode(
                eq("32100000@dankook.ac.kr"),
                anyString(),
                eq(300L),
                eq(response.getRequestId())
        );
    }

    @Test
    void requestResetResendCooldownExceeded() {
        RequestPasswordResetRequestDto dto = new RequestPasswordResetRequestDto("32100000", null);

        when(passwordResetRedisRepository.incrementRequestRate(eq("32100000"), anyString(), eq(600L))).thenReturn(1L);
        when(passwordResetRedisRepository.getResendCooldownSec("32100000")).thenReturn(30L);

        assertThatThrownBy(() -> passwordResetService.requestReset(dto, "127.0.0.1"))
                .isInstanceOf(PasswordResetException.class)
                .extracting("errorType")
                .isEqualTo(PasswordResetErrorType.RESEND_COOLDOWN);

        verify(userRepository, never()).findByStudentId(anyString());
    }

    @Test
    void verifyCodeMismatch() {
        String requestId = "f91dc016-36a3-44e6-8a23-6bbd0aa17b14";
        PasswordResetRequestState state = new PasswordResetRequestState(
                requestId,
                "32100000",
                hash(requestId, "123456"),
                0,
                false,
                null,
                false,
                true
        );
        RequestPasswordResetVerifyDto dto = new RequestPasswordResetVerifyDto(requestId, "654321");

        when(passwordResetRedisRepository.findRequest(requestId)).thenReturn(Optional.of(state));
        when(passwordResetRedisRepository.getRequestTtlSec(requestId)).thenReturn(250L);
        when(passwordResetRedisRepository.incrementVerifyAttempts(requestId)).thenReturn(1L);

        assertThatThrownBy(() -> passwordResetService.verifyCode(dto))
                .isInstanceOf(PasswordResetException.class)
                .extracting("errorType")
                .isEqualTo(PasswordResetErrorType.CODE_MISMATCH);
    }

    @Test
    void verifyCodeAttemptExceeded() {
        String requestId = "f91dc016-36a3-44e6-8a23-6bbd0aa17b14";
        PasswordResetRequestState state = new PasswordResetRequestState(
                requestId,
                "32100000",
                hash(requestId, "123456"),
                5,
                false,
                null,
                false,
                true
        );
        RequestPasswordResetVerifyDto dto = new RequestPasswordResetVerifyDto(requestId, "123456");

        when(passwordResetRedisRepository.findRequest(requestId)).thenReturn(Optional.of(state));
        when(passwordResetRedisRepository.getRequestTtlSec(requestId)).thenReturn(250L);

        assertThatThrownBy(() -> passwordResetService.verifyCode(dto))
                .isInstanceOf(PasswordResetException.class)
                .extracting("errorType")
                .isEqualTo(PasswordResetErrorType.TOO_MANY_ATTEMPTS);

        verify(passwordResetRedisRepository, never()).markVerified(anyString(), anyString(), anyLong());
    }

    @Test
    void verifyCodeSuccess() {
        String requestId = "f91dc016-36a3-44e6-8a23-6bbd0aa17b14";
        PasswordResetRequestState state = new PasswordResetRequestState(
                requestId,
                "32100000",
                hash(requestId, "123456"),
                0,
                false,
                null,
                false,
                true
        );
        RequestPasswordResetVerifyDto dto = new RequestPasswordResetVerifyDto(requestId, "123456");

        when(passwordResetRedisRepository.findRequest(requestId)).thenReturn(Optional.of(state));
        when(passwordResetRedisRepository.getRequestTtlSec(requestId)).thenReturn(250L);

        ResponsePasswordResetVerifyDto response = passwordResetService.verifyCode(dto);

        assertThat(response.getVerificationToken()).isNotBlank();
        verify(passwordResetRedisRepository).markVerified(eq(requestId), anyString(), eq(600L));
    }

    @Test
    void resetPasswordSuccess() {
        String requestId = "f91dc016-36a3-44e6-8a23-6bbd0aa17b14";
        String verificationToken = "valid-token";
        User user = sampleUser();
        ReflectionTestUtils.setField(user, "id", 1L);

        when(passwordResetRedisRepository.consumeVerifiedToken(eq(requestId), eq(hash(requestId, verificationToken))))
                .thenReturn(PasswordResetConsumeResult.success("32100000", true));
        when(userRepository.findByStudentId("32100000")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass!2026")).thenReturn("encoded-password");

        passwordResetService.resetPassword(new RequestPasswordResetDto(requestId, verificationToken, "NewPass!2026"));

        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userInfoService).invalidateUserInfo(1L);
    }

    @Test
    void resetPasswordTokenAlreadyUsed() {
        String requestId = "f91dc016-36a3-44e6-8a23-6bbd0aa17b14";
        String verificationToken = "used-token";

        when(passwordResetRedisRepository.consumeVerifiedToken(eq(requestId), eq(hash(requestId, verificationToken))))
                .thenReturn(PasswordResetConsumeResult.fail(PasswordResetConsumeResult.ConsumeStatus.TOKEN_ALREADY_CONSUMED));

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                new RequestPasswordResetDto(requestId, verificationToken, "NewPass!2026")))
                .isInstanceOf(PasswordResetException.class)
                .extracting("errorType")
                .isEqualTo(PasswordResetErrorType.TOKEN_ALREADY_CONSUMED);
    }

    private User sampleUser() {
        return User.builder()
                .studentId("32100000")
                .password("before-encoded")
                .name("테스터")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build();
    }

    private String hash(String requestId, String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((requestId + ":" + raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
