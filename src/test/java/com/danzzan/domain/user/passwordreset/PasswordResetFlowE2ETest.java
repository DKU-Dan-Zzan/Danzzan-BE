package com.danzzan.domain.user.passwordreset;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetConsumeResult;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetRedisRepository;
import com.danzzan.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password-reset;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-key-for-jwt-at-least-32-characters-long",
        "octomo.api-key=test-octomo-api-key",
        // 이 테스트들은 티켓팅 기능 자체를 검증한다. 가을 축제 기본값은 꺼짐이므로
        // 여기서는 명시적으로 켜서, 기능이 살아있음을 계속 보장한다.
        "app.ticketing.api-enabled=true"
})
@AutoConfigureMockMvc
class PasswordResetFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PasswordResetRedisRepository passwordResetRedisRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .studentId("32100000")
                .password(passwordEncoder.encode("OldPass!2026"))
                .name("테스터")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build());
    }

    @Test
    void passwordResetThenOldPasswordFailsAndNewPasswordSucceeds() throws Exception {
        when(passwordResetRedisRepository.consumeVerifiedToken(eq("request-123"), anyString()))
                .thenReturn(PasswordResetConsumeResult.success("32100000", true));

        mockMvc.perform(post("/user/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "request-123",
                                "verificationToken", "verified-token",
                                "newPassword", "NewPass!2026",
                                "confirmPassword", "NewPass!2026"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", "32100000",
                                "password", "OldPass!2026"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", "32100000",
                                "password", "NewPass!2026"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.studentId").value("32100000"))
                .andExpect(jsonPath("$.user.name").value("테스터"))
                .andExpect(jsonPath("$.user.college").value("공과대학"))
                .andExpect(jsonPath("$.user.department").value("컴퓨터공학과"));

        MvcResult loginResult = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", "32100000",
                                "password", "NewPass!2026"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value("32100000"))
                .andExpect(jsonPath("$.name").value("테스터"))
                .andExpect(jsonPath("$.college").value("공과대학"))
                .andExpect(jsonPath("$.department").value("컴퓨터공학과"));
    }
}
