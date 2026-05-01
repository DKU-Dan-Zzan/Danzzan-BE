package com.danzzan.domain.admin.service;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtRevocationService jwtRevocationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, jwtRevocationService);
    }

    @Test
    void logoutWithValidAdminRefreshTokenBumpsTokenVersion() {
        String refreshToken = "valid-refresh-token";
        User admin = adminUser();

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(1L);
        when(jwtTokenProvider.getTokenVersion(refreshToken)).thenReturn(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        authService.logout(refreshToken);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getTokenVersion()).isEqualTo(1);
        verify(jwtRevocationService).cacheUserVersion(1L, 1);
    }

    @Test
    void logoutWithInvalidRefreshTokenDoesNotChangeTokenVersion() {
        String refreshToken = "invalid-refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        authService.logout(refreshToken);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void logoutWithNonAdminRefreshTokenDoesNotChangeTokenVersion() {
        String refreshToken = "user-refresh-token";
        User user = normalUser();

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(2L);
        when(jwtTokenProvider.getTokenVersion(refreshToken)).thenReturn(0);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        authService.logout(refreshToken);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void logoutWithVersionMismatchDoesNotChangeTokenVersion() {
        String refreshToken = "stale-refresh-token";
        User admin = adminUser();
        admin.bumpTokenVersion();

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(1L);
        when(jwtTokenProvider.getTokenVersion(refreshToken)).thenReturn(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        authService.logout(refreshToken);

        verify(userRepository, never()).save(any(User.class));
    }

    private User adminUser() {
        User user = User.builder()
                .studentId("32100001")
                .password("encoded-password")
                .name("관리자")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_ADMIN)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private User normalUser() {
        User user = User.builder()
                .studentId("32100002")
                .password("encoded-password")
                .name("일반사용자")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 2L);
        return user;
    }
}
