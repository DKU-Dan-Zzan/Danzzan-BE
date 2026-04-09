package com.danzzan.domain.user.controller;

import com.danzzan.domain.user.phoneverification.dto.response.ResponsePhoneVerificationCreateDto;
import com.danzzan.domain.user.phoneverification.dto.response.ResponsePhoneVerificationStatusDto;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationErrorType;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationException;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import com.danzzan.domain.user.phoneverification.service.PhoneVerificationService;
import com.danzzan.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PhoneVerificationService phoneVerificationService;

    @BeforeEach
    void setUp() {
        PhoneVerificationController controller = new PhoneVerificationController(phoneVerificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createSessionSuccess() throws Exception {
        when(phoneVerificationService.createSession(anyString(), anyString()))
                .thenReturn(ResponsePhoneVerificationCreateDto.builder()
                        .sessionId("session-123")
                        .status(PhoneVerificationStatus.PENDING)
                        .octomoReceiveNumber("16663538")
                        .messageBody("123456")
                        .expiresInSec(300)
                        .statusPollHintSec(5)
                        .expiresAt(LocalDateTime.of(2026, 4, 9, 12, 0))
                        .build());

        mockMvc.perform(post("/user/phone-verifications/signup-token-123/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.messageBody").value("123456"));
    }

    @Test
    void verifySessionConflict() throws Exception {
        doThrow(new PhoneVerificationException(PhoneVerificationErrorType.ALREADY_VERIFIED))
                .when(phoneVerificationService).verifySession("session-123", "01012345678");

        mockMvc.perform(post("/user/phone-verifications/sessions/session-123/verify")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"01012345678\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PHONE_VERIFICATION_ALREADY_VERIFIED"));
    }

    @Test
    void getStatusSuccess() throws Exception {
        when(phoneVerificationService.getStatus("session-123"))
                .thenReturn(ResponsePhoneVerificationStatusDto.builder()
                        .sessionId("session-123")
                        .status(PhoneVerificationStatus.VERIFIED)
                        .attemptCount(1)
                        .expiresInSec(120)
                        .expiresAt(LocalDateTime.of(2026, 4, 9, 12, 5))
                        .verifiedAt(LocalDateTime.of(2026, 4, 9, 12, 1))
                        .verifiedPhoneNumberMasked("010****1234")
                        .build());

        mockMvc.perform(get("/user/phone-verifications/sessions/session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedPhoneNumberMasked").value("010****1234"));

        verify(phoneVerificationService).getStatus("session-123");
    }
}
