package com.danzzan.domain.user.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketIssueRequestStatusCacheService;
import com.danzzan.domain.user.model.dto.request.RequestLoginDto;
import com.danzzan.domain.user.model.dto.response.ResponseLoginDto;
import com.danzzan.domain.user.model.dto.response.ResponseRefreshTokenDto;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInfoService userInfoService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserTicketRepository userTicketRepository;

    @Mock
    private TicketIssueRequestRepository ticketIssueRequestRepository;

    @Mock
    private FestivalEventRepository festivalEventRepository;

    @Mock
    private QueueStateService queueStateService;

    @Mock
    private JwtRevocationService jwtRevocationService;

    @Mock
    private TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                userInfoService,
                passwordEncoder,
                jwtTokenProvider,
                userTicketRepository,
                ticketIssueRequestRepository,
                festivalEventRepository,
                queueStateService,
                jwtRevocationService,
                ticketIssueRequestStatusCacheService
        );
    }

    @Test
    void login_성공시_현재_토큰버전을_캐시한다() {
        User user = user(1L, "32100000", "01012345678");
        ReflectionTestUtils.setField(user, "tokenVersion", 2);

        when(userRepository.findByStudentIdAndDeletedFalse("32100000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "pw")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "32100000", "ROLE_USER", 2)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(1L, 2)).thenReturn("refresh-token");

        ResponseLoginDto response = userService.login(new RequestLoginDto("32100000", "password"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(jwtRevocationService).clearWithdrawnUser(1L);
        verify(jwtRevocationService).cacheUserVersion(1L, 2);
    }

    @Test
    void refreshToken_성공시_현재_토큰버전을_캐시한다() {
        User user = user(1L, "32100000", "01012345678");
        ReflectionTestUtils.setField(user, "tokenVersion", 3);
        Claims claims = mock(Claims.class);

        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getClaimsFromExpiredToken("expired-access-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.getTokenVersion("refresh-token")).thenReturn(3);
        when(jwtTokenProvider.createAccessToken(1L, "32100000", "ROLE_USER", 3)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(1L, 3)).thenReturn("new-refresh-token");

        ResponseRefreshTokenDto response = userService.refreshToken("expired-access-token", "refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(jwtRevocationService).clearWithdrawnUser(1L);
        verify(jwtRevocationService).cacheUserVersion(1L, 3);
    }

    @Test
    void withdraw_CONFIRMED티켓은_권리포기처리하고_이름을_제외한_유저정보를_비식별화한다() {
        User user = user(1L, "32100000", "01012345678");
        FestivalEvent ticketEvent = event(10L, TicketingStatus.OPEN);
        UserTicket ticket = UserTicket.builder()
                .user(user)
                .event(ticketEvent)
                .ticketingOrder(3)
                .seq(30L)
                .build();
        FestivalEvent openEvent = event(10L, TicketingStatus.OPEN);

        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userTicketRepository.findAllByUserIdAndStatusForUpdate(1L, TicketStatus.CONFIRMED))
                .thenReturn(List.of(ticket));
        when(ticketIssueRequestRepository.findAllByUserIdAndStatusForUpdate(1L, TicketIssueRequestStatus.PROCESSING))
                .thenReturn(List.of());
        when(festivalEventRepository.findAllByTicketingStatus(TicketingStatus.OPEN)).thenReturn(List.of(openEvent));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-password");

        userService.withdraw(1L, "access-token");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED_WITHDRAWAL);
        assertThat(ticket.getCancelledAt()).isNotNull();
        assertThat(ticket.getCancelReason()).isEqualTo("USER_WITHDRAWAL");
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getStudentId()).startsWith("withdrawn:1:");
        assertThat(user.getPassword()).isEqualTo("encoded-random-password");
        assertThat(user.getName()).isEqualTo("김단짠");
        assertThat(user.getCollege()).isEqualTo("WITHDRAWN");
        assertThat(user.getMajor()).isEqualTo("WITHDRAWN");
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.isPhoneVerified()).isFalse();
        assertThat(user.getPhoneVerifiedAt()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(queueStateService).leaveQueue("10", "1");
        verify(userInfoService).invalidateUserInfo(1L);
        verify(jwtRevocationService).blacklistAccessToken("access-token");
        verify(jwtRevocationService).markWithdrawnUser(1L);
        verify(jwtRevocationService).cacheUserVersion(1L, 1);
    }

    private User user(Long id, String studentId, String phoneNumber) {
        User user = User.builder()
                .studentId(studentId)
                .password("pw")
                .name("김단짠")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .phoneNumber(phoneNumber)
                .phoneVerified(true)
                .phoneVerifiedAt(LocalDateTime.of(2026, 4, 20, 12, 0))
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private FestivalEvent event(Long id, TicketingStatus status) {
        FestivalEvent event = FestivalEvent.builder()
                .title("dan")
                .eventDate(LocalDate.of(2026, 5, 13))
                .ticketingStartTime(LocalDateTime.of(2026, 5, 13, 18, 0))
                .ticketingStatus(status)
                .totalCapacity(100)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
