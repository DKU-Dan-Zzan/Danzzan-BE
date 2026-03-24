package com.danzzan.domain.user.controller;

import com.danzzan.domain.auth.dto.RequestSignupDto;
import com.danzzan.domain.auth.service.SignupService;
import com.danzzan.domain.user.service.UserService;
import com.danzzan.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

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
                                "password", "MySecure!123",
                                "confirmPassword", "MySecure!123"
                        ))))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestSignupDto> captor = ArgumentCaptor.forClass(RequestSignupDto.class);
        verify(signupService).signup(captor.capture(), eq("signup-token-123"));
        assertThat(captor.getValue().getPassword()).isEqualTo("MySecure!123");
        assertThat(captor.getValue().getConfirmPassword()).isEqualTo("MySecure!123");
    }

    @Test
    void signupPasswordPolicyViolationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/user/signup-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", "password1",
                                "confirmPassword", "password1"
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
                                "password", "MySecure!123",
                                "confirmPassword", "Another!123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("비밀번호 확인이 일치하지 않습니다."));

        verify(signupService, never()).signup(any(), anyString());
    }

    @Test
    void getMyInfoWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getMyInfo(anyLong());
    }
}
