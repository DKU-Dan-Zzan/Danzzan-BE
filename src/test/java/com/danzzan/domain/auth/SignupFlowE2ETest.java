package com.danzzan.domain.auth;

import com.danzzan.domain.auth.service.SignupService;
import com.danzzan.domain.auth.service.SignupTokenStore;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        // 이 테스트들은 티켓팅 기능 자체를 검증한다. 가을 축제 기본값은 꺼짐이므로
        // 여기서는 명시적으로 켜서, 기능이 살아있음을 계속 보장한다.
        "app.ticketing.api-enabled=true"
})
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:signup-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "octomo.api-key=test-octomo-api-key",
        "phone-verification.code-encryption-secret=test-secret-for-phone-verification",
        "jwt.secret=test-secret-key-for-jwt-at-least-32-characters-long"
})
class SignupFlowE2ETest {
    private static final String VALID_PASSWORD = String.join("", "My", "Secure", "!", "123");

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

    @Autowired
    private SignupTokenStore signupTokenStore;

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
                "테스트",
                "공과대학",
                "컴퓨터공학과",
                AcademicStatus.ENROLLED
        );
        signupTokenStore.cacheVerifiedPhone(signupToken, "01012345678", LocalDateTime.now());

        mockMvc.perform(post("/user/" + signupToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", VALID_PASSWORD,
                                "confirmPassword", VALID_PASSWORD,
                                "phoneVerificationSessionId", "phone-session-123"
                        ))))
                .andExpect(status().isOk());

        User user = userRepository.findByStudentId("32100000")
                .orElseThrow(() -> new AssertionError("회원이 생성되지 않았습니다."));

        assertThat(user.getStudentId()).isEqualTo("32100000");
        assertThat(user.getName()).isEqualTo("테스트");
        assertThat(user.getCollege()).isEqualTo("공과대학");
        assertThat(user.getMajor()).isEqualTo("컴퓨터공학과");
        assertThat(user.getAcademicStatus()).isEqualTo(AcademicStatus.ENROLLED);
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(user.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.getPhoneVerifiedAt()).isNotNull();
        assertThat(user.getPassword()).isNotEqualTo(VALID_PASSWORD);
        assertThat(passwordEncoder.matches(VALID_PASSWORD, user.getPassword())).isTrue();

        assertThatThrownBy(() -> signupService.getCachedStudentInfo(signupToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 회원가입 토큰입니다.");
    }
}
