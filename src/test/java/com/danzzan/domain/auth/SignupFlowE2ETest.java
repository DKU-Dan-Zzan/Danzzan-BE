package com.danzzan.domain.auth;

import com.danzzan.domain.auth.service.SignupService;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignupFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignupService signupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void signupCreatesUserFromCachedStudentInfoAndConsumesSignupToken() throws Exception {
        String signupToken = "signup-token-123";
        signupService.cacheStudentInfo(
                signupToken,
                "32100000",
                "테스터",
                "공과대학",
                "컴퓨터공학과",
                AcademicStatus.ENROLLED
        );

        mockMvc.perform(post("/user/" + signupToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", "MySecure!123",
                                "confirmPassword", "MySecure!123"
                        ))))
                .andExpect(status().isOk());

        User user = userRepository.findByStudentId("32100000")
                .orElseThrow(() -> new AssertionError("회원이 생성되지 않았습니다."));

        assertThat(user.getStudentId()).isEqualTo("32100000");
        assertThat(user.getName()).isEqualTo("테스터");
        assertThat(user.getCollege()).isEqualTo("공과대학");
        assertThat(user.getMajor()).isEqualTo("컴퓨터공학과");
        assertThat(user.getAcademicStatus()).isEqualTo(AcademicStatus.ENROLLED);
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(user.getPassword()).isNotEqualTo("MySecure!123");
        assertThat(passwordEncoder.matches("MySecure!123", user.getPassword())).isTrue();

        assertThatThrownBy(() -> signupService.getCachedStudentInfo(signupToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 회원가입 토큰입니다.");
    }
}
