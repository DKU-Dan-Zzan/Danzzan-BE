package com.danzzan.domain.user.controller;

import com.danzzan.domain.auth.dto.RequestSignupDto;
import com.danzzan.domain.auth.service.SignupService;
import com.danzzan.domain.user.service.UserService;
import com.danzzan.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    private static final String VALID_PASSWORD = String.join("", "My", "Secure", "!", "123");
    private static final String WEAK_PASSWORD = "password" + "1";
    private static final String DIFFERENT_PASSWORD = String.join("", "Another", "!", "123");

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @Mock
    private SignupService signupService;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService, signupService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupSuccess() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "naverId", "signup_test_id",
                                "confirmNaverId", "signup_test_id",
                                "password", VALID_PASSWORD,
                                "confirmPassword", VALID_PASSWORD,
                                "phoneVerificationSessionId", "phone-session-123"
                        ))))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestSignupDto> captor = ArgumentCaptor.forClass(RequestSignupDto.class);
        verify(signupService).signup(captor.capture(), eq("signup-token-123"));
        assertThat(captor.getValue().getPassword()).isEqualTo(VALID_PASSWORD);
        assertThat(captor.getValue().getConfirmPassword()).isEqualTo(VALID_PASSWORD);
        assertThat(captor.getValue().getPhoneVerificationSessionId()).isEqualTo("phone-session-123");
        assertThat(captor.getValue().getNaverId()).isEqualTo("signup_test_id");
        assertThat(captor.getValue().getConfirmNaverId()).isEqualTo("signup_test_id");
    }

    @Test
    void signupRequiresPhoneVerificationSessionId() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "naverId", "signup_test_id",
                                "confirmNaverId", "signup_test_id",
                                "password", VALID_PASSWORD,
                                "confirmPassword", VALID_PASSWORD
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("전화번호 인증 세션 ID는 필수입니다."));

        verify(signupService, never()).signup(any(), anyString());
    }

    @Test
    void signupPasswordPolicyViolationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "naverId", "signup_test_id",
                                "confirmNaverId", "signup_test_id",
                                "password", WEAK_PASSWORD,
                                "confirmPassword", WEAK_PASSWORD,
                                "phoneVerificationSessionId", "phone-session-123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("비밀번호는 8자 이상이며 특수문자를 1자 이상 포함해야 합니다."));

        verify(signupService, never()).signup(any(), anyString());
    }

    @Test
    void signupConfirmMismatchReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "naverId", "signup_test_id",
                                "confirmNaverId", "signup_test_id",
                                "password", VALID_PASSWORD,
                                "confirmPassword", DIFFERENT_PASSWORD,
                                "phoneVerificationSessionId", "phone-session-123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("비밀번호 확인이 일치하지 않습니다."));

        verify(signupService, never()).signup(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"naverId", "confirmNaverId"})
    void signupRequiresBothNaverIdFields(String field) throws Exception {
        Map<String, String> request = signupRequest("signup_test_id", "signup_test_id");
        request.remove(field);
        assertInvalidSignup(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void signupRejectsBlankNaverIds(String blank) throws Exception {
        assertInvalidSignup(signupRequest(blank, blank));
    }

    @Test
    void signupRejectsMismatchedNaverIds() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest("signup_test_id", "different_id"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("네이버 아이디가 서로 일치하지 않습니다."));
        verify(signupService, never()).signup(any(), anyString());
    }

    @Test
    void signupComparesNaverIdsAfterTrimming() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(" signup_test_id ", "signup_test_id"))))
                .andExpect(status().isOk());
        verify(signupService).signup(any(), eq("signup-token-123"));
    }

    @Test
    void signupRejectsNaverIdLongerThanStoredColumn() throws Exception {
        String tooLong = "a".repeat(101);
        assertInvalidSignup(signupRequest(tooLong, tooLong));
    }

    @Test
    void signupAcceptsNaverIdAtStoredColumnLimit() throws Exception {
        String longest = "a".repeat(100);
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(longest, longest))))
                .andExpect(status().isOk());
    }

    private Map<String, String> signupRequest(String naverId, String confirmation) {
        return new HashMap<>(Map.of(
                "password", VALID_PASSWORD,
                "confirmPassword", VALID_PASSWORD,
                "naverId", naverId,
                "confirmNaverId", confirmation,
                "phoneVerificationSessionId", "phone-session-123"
        ));
    }

    private void assertInvalidSignup(Map<String, String> request) throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(signupService, never()).signup(any(), anyString());
    }

    @Test
    void getMyInfoWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getMyInfo(anyLong());
    }
}
