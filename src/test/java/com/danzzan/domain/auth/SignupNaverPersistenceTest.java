package com.danzzan.domain.auth;

import com.danzzan.domain.auth.controller.DKUController;
import com.danzzan.domain.auth.service.DKUAuthService;
import com.danzzan.domain.auth.service.SignupService;
import com.danzzan.domain.auth.service.SignupTokenStore;
import com.danzzan.domain.user.controller.PhoneVerificationController;
import com.danzzan.domain.user.controller.UserController;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.phoneverification.config.PhoneVerificationProperties;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import com.danzzan.domain.user.phoneverification.repository.PhoneVerificationSessionRepository;
import com.danzzan.domain.user.phoneverification.service.PhoneVerificationService;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.domain.user.service.UserService;
import com.danzzan.global.exception.GlobalExceptionHandler;
import com.danzzan.infra.dku.DkuAuthenticationService;
import com.danzzan.infra.dku.DkuStudentService;
import com.danzzan.infra.dku.model.DkuAuth;
import com.danzzan.infra.dku.model.StudentInfo;
import com.danzzan.infra.octomo.OctomoMessageClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exercise the three real signup controllers/services against an isolated H2 database.
// DKU and SMS clients are mocks; no running server, real credentials, or external calls.
@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:signup-naver;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "phone-verification.code-encryption-secret=signup-test-only-secret",
        "phone-verification.octomo.receive-number=00000000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SignupService.class, SignupTokenStore.class, DKUAuthService.class,
        PhoneVerificationService.class, PhoneVerificationProperties.class, BCryptPasswordEncoder.class})
class SignupNaverPersistenceTest {
    private static final String STUDENT_ID = "32999999";
    private static final String PHONE = "01000000000";
    private static final String PASSWORD = "Signup-test!123";

    @Autowired private SignupService signupService;
    @Autowired private SignupTokenStore signupTokenStore;
    @Autowired private DKUAuthService dkuAuthService;
    @Autowired private PhoneVerificationService phoneVerificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private PhoneVerificationSessionRepository phoneSessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    @MockitoBean private DkuAuthenticationService dkuAuthenticationService;
    @MockitoBean private DkuStudentService dkuStudentService;
    @MockitoBean private OctomoMessageClient octomoMessageClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final List<String> signupTokens = new ArrayList<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DKUController(dkuAuthService),
                        new PhoneVerificationController(phoneVerificationService),
                        new UserController(mock(UserService.class), signupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void clearTestTokens() {
        signupTokens.forEach(signupTokenStore::remove);
    }

    @Test
    void allThreeStepsPersistTrimmedNaverIdAfterClearingPersistenceContext() throws Exception {
        VerifiedSignup verified = verifyStudentAndPhone();
        completeSignup(verified, " signup_test_id ", "signup_test_id");

        User user = reloadUser();
        assertThat(user.getNaverId()).isEqualTo("signup_test_id");
        assertThat(storedNaverId(user.getId())).isEqualTo("signup_test_id");
        assertThat(user.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.getPhoneVerifiedAt()).isNotNull();
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPassword())).isTrue();
        assertThat(phoneSessionRepository.findBySessionId(verified.sessionId()).orElseThrow().getStatus())
                .isEqualTo(PhoneVerificationStatus.CONSUMED);
        assertThatThrownBy(() -> signupTokenStore.getCachedStudentInfo(verified.token()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(signupTokenStore.getVerifiedPhone(verified.token())).isEmpty();
    }

    @Test
    void mismatchedIdsDoNotSaveOrConsumeVerificationAndCanBeCorrected() throws Exception {
        VerifiedSignup verified = verifyStudentAndPhone();
        mockMvc.perform(post("/user/" + verified.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(verified.sessionId(), "signup_test_id", "other_test_id")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("네이버 아이디가 서로 일치하지 않습니다."));
        assertThat(userRepository.findByStudentId(STUDENT_ID)).isEmpty();
        assertThat(signupTokenStore.getVerifiedPhone(verified.token())).isPresent();

        completeSignup(verified, "signup_test_id", "signup_test_id");
        User user = reloadUser();
        assertThat(storedNaverId(user.getId())).isEqualTo("signup_test_id");
    }

    @Test
    void naverIdCannotBypassPhoneVerification() throws Exception {
        String token = verifyStudent();
        mockMvc.perform(post("/user/" + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("unverified-session", "signup_test_id", "signup_test_id")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PHONE_VERIFICATION_NOT_VERIFIED"));
        assertThat(userRepository.findByStudentId(STUDENT_ID)).isEmpty();
    }

    @Test
    void withdrawalClearsPersistedNaverIdAndKeepsCurrentDeletionBehavior() throws Exception {
        VerifiedSignup verified = verifyStudentAndPhone();
        completeSignup(verified, "signup_test_id", "signup_test_id");
        User user = reloadUser();
        assertThat(user.getNaverId()).isEqualTo("signup_test_id");
        Long userId = user.getId();

        user.withdraw("withdrawn-signup-test", passwordEncoder.encode("Discarded-test!123"));
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User withdrawn = userRepository.findById(userId).orElseThrow();
        assertThat(storedNaverId(userId)).isNull();
        assertThat(withdrawn.isDeleted()).isTrue();
        assertThat(withdrawn.getName()).isEqualTo("가입테스트");
        assertThat(withdrawn.getStudentId()).isEqualTo("withdrawn-signup-test");
        assertThat(withdrawn.getPhoneNumber()).isNull();
        assertThat(withdrawn.isPhoneVerified()).isFalse();
        assertThat(withdrawn.getTokenVersion()).isEqualTo(1);
    }

    private String verifyStudent() throws Exception {
        DkuAuth auth = new DkuAuth();
        when(dkuAuthenticationService.login(STUDENT_ID, "local-test-portal-password")).thenReturn(auth);
        when(dkuStudentService.crawlStudentInfo(auth)).thenReturn(new StudentInfo(
                "가입테스트", STUDENT_ID, "공과대학", "컴퓨터공학과", "재학", 2026, "20260301"));
        String response = mockMvc.perform(post("/user/dku/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dkuStudentId", STUDENT_ID, "dkuPassword", "local-test-portal-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).path("signupToken").asText();
        assertThat(token).isNotBlank();
        signupTokens.add(token);
        return token;
    }

    private VerifiedSignup verifyStudentAndPhone() throws Exception {
        String token = verifyStudent();
        String created = mockMvc.perform(post("/user/phone-verifications/" + token + "/sessions"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode session = objectMapper.readTree(created);
        String sessionId = session.path("sessionId").asText();
        String message = session.path("messageBody").asText();
        when(octomoMessageClient.existsRecentMessage(PHONE, message)).thenReturn(true);
        mockMvc.perform(post("/user/phone-verifications/sessions/" + sessionId + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", PHONE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        verify(octomoMessageClient).existsRecentMessage(PHONE, message);
        return new VerifiedSignup(token, sessionId);
    }

    private void completeSignup(VerifiedSignup verified, String naverId, String confirmation) throws Exception {
        mockMvc.perform(post("/user/" + verified.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(verified.sessionId(), naverId, confirmation)))
                .andExpect(status().isOk());
    }

    private String signupBody(String sessionId, String naverId, String confirmation) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "password", PASSWORD, "confirmPassword", PASSWORD,
                "naverId", naverId, "confirmNaverId", confirmation,
                "phoneVerificationSessionId", sessionId));
    }

    private User reloadUser() {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findByStudentId(STUDENT_ID).orElseThrow();
    }

    private Object storedNaverId(Long userId) {
        return entityManager.createNativeQuery("select naver_id from users where id = :id")
                .setParameter("id", userId).getSingleResult();
    }

    private record VerifiedSignup(String token, String sessionId) {}
}
