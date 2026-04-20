package com.danzzan.global.jwt;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
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

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
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
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("token")).thenReturn(1L);
        when(jwtTokenProvider.getRole("token")).thenReturn("ROLE_USER");

        filter.doFilter(request, response, filterChain);

        verify(userRepository, never()).findById(1L);
        verify(jwtTokenProvider, never()).getTokenVersion("token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tickets외요청은_tokenVersionDB검증을_수행한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/events");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = userWithTokenVersion(3);

        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("token")).thenReturn(1L);
        when(jwtTokenProvider.getRole("token")).thenReturn("ROLE_ADMIN");
        when(jwtTokenProvider.getTokenVersion("token")).thenReturn(3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        verify(jwtTokenProvider).getTokenVersion("token");
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
