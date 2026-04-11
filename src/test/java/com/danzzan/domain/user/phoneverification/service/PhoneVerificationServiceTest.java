package com.danzzan.domain.user.phoneverification.service;

import com.danzzan.domain.auth.service.SignupTokenStore;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.phoneverification.config.PhoneVerificationProperties;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationErrorType;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationException;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationSession;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import com.danzzan.domain.user.phoneverification.repository.PhoneVerificationSessionRepository;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.infra.octomo.OctomoMessageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    @Mock
    private SignupTokenStore signupTokenStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PhoneVerificationSessionRepository phoneVerificationSessionRepository;

    @Mock
    private OctomoMessageClient octomoMessageClient;

    private PhoneVerificationService phoneVerificationService;

    @BeforeEach
    void setUp() {
        PhoneVerificationProperties properties = new PhoneVerificationProperties();
        PhoneVerificationProperties.Octomo octomo = new PhoneVerificationProperties.Octomo();
        octomo.setReceiveNumber("16663538");
        properties.setOctomo(octomo);
        properties.setCodeEncryptionSecret("test-secret-for-phone-verification");

        phoneVerificationService = new PhoneVerificationService(
                signupTokenStore,
                userRepository,
                phoneVerificationSessionRepository,
                properties,
                octomoMessageClient
        );
    }

    @Test
    void createSessionCreatesPendingSession() {
        when(signupTokenStore.getCachedStudentInfo("signup-token-123"))
                .thenReturn(new SignupTokenStore.StudentInfoCache(
                        "32100000",
                        "홍길동",
                        "공과대학",
                        "컴퓨터공학과",
                        AcademicStatus.ENROLLED
                ));
        when(phoneVerificationSessionRepository.countBySignupTokenAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(phoneVerificationSessionRepository.countByRequestIpHashAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(phoneVerificationSessionRepository.findTopBySignupTokenOrderByCreatedAtDesc("signup-token-123"))
                .thenReturn(java.util.Optional.empty());

        phoneVerificationService.createSession("signup-token-123", "127.0.0.1");

        ArgumentCaptor<PhoneVerificationSession> captor = ArgumentCaptor.forClass(PhoneVerificationSession.class);
        verify(phoneVerificationSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PhoneVerificationStatus.PENDING);
        assertThat(captor.getValue().getSignupToken()).isEqualTo("signup-token-123");
        assertThat(captor.getValue().getCodeHash()).isNotBlank();
        assertThat(captor.getValue().getCodeCiphertext()).isNotBlank();
    }

    @Test
    void verifySessionMarksVerifiedWhenOctomoExistsReturnsTrue() {
        when(signupTokenStore.getCachedStudentInfo("signup-token-123"))
                .thenReturn(new SignupTokenStore.StudentInfoCache(
                        "32100000",
                        "홍길동",
                        "공과대학",
                        "컴퓨터공학과",
                        AcademicStatus.ENROLLED
                ));
        when(phoneVerificationSessionRepository.countBySignupTokenAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(phoneVerificationSessionRepository.countByRequestIpHashAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(phoneVerificationSessionRepository.findTopBySignupTokenOrderByCreatedAtDesc("signup-token-123"))
                .thenReturn(java.util.Optional.empty());

        phoneVerificationService.createSession("signup-token-123", "127.0.0.1");

        ArgumentCaptor<PhoneVerificationSession> captor = ArgumentCaptor.forClass(PhoneVerificationSession.class);
        verify(phoneVerificationSessionRepository).save(captor.capture());
        PhoneVerificationSession session = captor.getValue();

        when(phoneVerificationSessionRepository.findBySessionId(session.getSessionId()))
                .thenReturn(java.util.Optional.of(session));
        when(octomoMessageClient.existsRecentMessage(org.mockito.ArgumentMatchers.eq("01012345678"), anyString()))
                .thenReturn(true);
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);

        var response = phoneVerificationService.verifySession(session.getSessionId(), "010-1234-5678");

        assertThat(response.getStatus()).isEqualTo(PhoneVerificationStatus.VERIFIED);
        assertThat(session.getVerifiedPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void consumeVerifiedPhoneNumberRejectsPendingSession() {
        when(signupTokenStore.getCachedStudentInfo("signup-token-123"))
                .thenReturn(new SignupTokenStore.StudentInfoCache(
                        "32100000",
                        "홍길동",
                        "공과대학",
                        "컴퓨터공학과",
                        AcademicStatus.ENROLLED
                ));
        when(phoneVerificationSessionRepository.countBySignupTokenAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(phoneVerificationSessionRepository.countByRequestIpHashAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(phoneVerificationSessionRepository.findTopBySignupTokenOrderByCreatedAtDesc("signup-token-123"))
                .thenReturn(java.util.Optional.empty());

        phoneVerificationService.createSession("signup-token-123", "127.0.0.1");

        ArgumentCaptor<PhoneVerificationSession> captor = ArgumentCaptor.forClass(PhoneVerificationSession.class);
        verify(phoneVerificationSessionRepository).save(captor.capture());
        PhoneVerificationSession session = captor.getValue();
        when(phoneVerificationSessionRepository.findBySessionId(session.getSessionId()))
                .thenReturn(java.util.Optional.of(session));

        assertThatThrownBy(() -> phoneVerificationService.consumeVerifiedPhoneNumber("signup-token-123", session.getSessionId()))
                .isInstanceOf(PhoneVerificationException.class)
                .extracting(ex -> ((PhoneVerificationException) ex).getErrorType())
                .isEqualTo(PhoneVerificationErrorType.NOT_VERIFIED);
    }
}
