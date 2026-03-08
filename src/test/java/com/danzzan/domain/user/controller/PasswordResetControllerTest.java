package com.danzzan.domain.user.controller;

import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.exception.PasswordResetErrorType;
import com.danzzan.domain.user.passwordreset.exception.PasswordResetException;
import com.danzzan.domain.user.passwordreset.service.PasswordResetService;
import com.danzzan.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        PasswordResetController controller = new PasswordResetController(passwordResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void requestResetSuccess() throws Exception {
        when(passwordResetService.requestReset(any(), anyString()))
                .thenReturn(new ResponsePasswordResetRequestDto("request-123", 300L));

        mockMvc.perform(post("/user/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("studentId", "32100000", "email", "32100000@dankook.ac.kr")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("request-123"))
                .andExpect(jsonPath("$.expiresInSec").value(300));
    }

    @Test
    void verifyCodeSuccess() throws Exception {
        when(passwordResetService.verifyCode(any()))
                .thenReturn(new ResponsePasswordResetVerifyDto("verification-token"));

        mockMvc.perform(post("/user/password/reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("requestId", "request-123", "code", "123456")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").value("verification-token"));
    }

    @Test
    void resetPasswordSuccess() throws Exception {
        mockMvc.perform(post("/user/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "requestId", "request-123",
                                        "verificationToken", "verification-token",
                                        "newPassword", "NewPass!2026"
                                )
                        )))
                .andExpect(status().isOk());
    }

    @Test
    void verifyCodeExpiredError() throws Exception {
        doThrow(new PasswordResetException(PasswordResetErrorType.CODE_EXPIRED))
                .when(passwordResetService).verifyCode(any());

        mockMvc.perform(post("/user/password/reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("requestId", "request-123", "code", "123456")
                        )))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("인증코드가 만료되었습니다."))
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_RESET_CODE_EXPIRED"));
    }
}
