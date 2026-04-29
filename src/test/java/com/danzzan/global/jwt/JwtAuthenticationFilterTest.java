package com.danzzan.global.jwt;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtRevocationService jwtRevocationService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, jwtRevocationService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tickets요청은_tokenVersionDB검증을_건너뛴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tickets/1/queue/enter");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.getValidClaims("token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(1L);
        when(jwtTokenProvider.getRole(claims)).thenReturn("ROLE_USER");

        filter.doFilter(request, response, filterChain);

        verify(userRepository, never()).findById(1L);
        verify(jwtTokenProvider, never()).getTokenVersion(claims);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void blacklist된_accessToken은_즉시_401을_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/me");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtRevocationService.isBlacklisted("token")).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(jwtTokenProvider, never()).getValidClaims("token");
        verify(filterChain, never()).doFilter(request, response);
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 탈퇴마커가_있는_유저는_tickets요청에서도_401을_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tickets/1/queue/enter");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.getValidClaims("token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(1L);
        when(jwtTokenProvider.getRole(claims)).thenReturn("ROLE_USER");
        when(jwtRevocationService.isWithdrawnUser(1L)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(userRepository, never()).findById(1L);
        verify(filterChain, never()).doFilter(request, response);
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void tickets외요청은_tokenVersionDB검증을_수행한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/events");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = userWithTokenVersion(3);
        Claims claims = mock(Claims.class);

        when(jwtTokenProvider.getValidClaims("token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(1L);
        when(jwtTokenProvider.getRole(claims)).thenReturn("ROLE_ADMIN");
        when(jwtTokenProvider.getTokenVersion(claims)).thenReturn(3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        verify(jwtTokenProvider).getTokenVersion(claims);
        verify(userRepository).findById(1L);
        verify(filterChain).doFilter(request, response);
    }

    private User userWithTokenVersion(int tokenVersion) {
        User user = User.builder()
                .studentId("321")
                .password("pw")
                .name("name")
                .college("college")
                .major("major")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build();
        ReflectionTestUtils.setField(user, "tokenVersion", tokenVersion);
        return user;
    }
}
