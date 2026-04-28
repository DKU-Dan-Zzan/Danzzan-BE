package com.danzzan.domain.user.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketIssueRequestStatusCacheService;
import com.danzzan.domain.user.exception.UserNotFoundException;
import com.danzzan.domain.user.exception.WrongPasswordException;
import com.danzzan.domain.user.model.dto.request.RequestLoginDto;
import com.danzzan.domain.user.model.dto.response.ResponseLoginDto;
import com.danzzan.domain.user.model.dto.response.ResponseRefreshTokenDto;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private static final String USER_WITHDRAWN_ERROR_CODE = "USER_WITHDRAWN";
    private static final List<TicketStatus> CONSUMED_TICKET_STATUSES = List.of(
            TicketStatus.CONFIRMED,
            TicketStatus.ISSUED,
            TicketStatus.CANCELLED_WITHDRAWAL
    );

    private final UserRepository userRepository;
    private final UserInfoService userInfoService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserTicketRepository userTicketRepository;
    private final TicketIssueRequestRepository ticketIssueRequestRepository;
    private final FestivalEventRepository festivalEventRepository;
    private final QueueStateService queueStateService;
    private final JwtRevocationService jwtRevocationService;
    private final TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;

    // 로그인 처리
    // 학번으로 사용자 조회 후 비밀번호 검증, JWT 토큰 발급
    public ResponseLoginDto login(RequestLoginDto dto) {
        User user = userRepository.findByStudentIdAndDeletedFalse(dto.getStudentId())
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new WrongPasswordException();
        }

        // 로그인 성공 시 사용자 정보 캐시에 저장
        userInfoService.cacheUserInfo(user.getId(), user);

        // JWT 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getStudentId(), user.getRole().name(), user.getTokenVersion());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getTokenVersion());

        ResponseLoginDto.UserInfo userInfo = toUserInfo(user);
        return new ResponseLoginDto(accessToken, refreshToken, userInfo);
    }

    public ResponseLoginDto.UserInfo getMyInfo(Long userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);
        return toUserInfo(user);
    }

    // 로그아웃 처리
    // tokenVersion을 올려 기존 토큰 전체 무효화
    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.bumpTokenVersion();
        jwtRevocationService.cacheUserVersion(user.getId(), user.getTokenVersion());
    }

    @Transactional
    public void withdraw(Long userId, String accessToken) {
        User user = userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        cancelConfirmedTickets(userId, now);
        failProcessingIssueRequests(user, now);
        leaveOpenQueues(userId);

        String maskedStudentId = "withdrawn:" + userId + ":" + UUID.randomUUID();
        String encodedRandomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
        user.withdraw(maskedStudentId, encodedRandomPassword);
        userInfoService.invalidateUserInfo(userId);

        jwtRevocationService.blacklistAccessToken(accessToken);
        jwtRevocationService.markWithdrawnUser(userId);
        jwtRevocationService.cacheUserVersion(userId, user.getTokenVersion());
    }

    // 토큰 재발급
    // 만료된 Access Token에서 userId를 추출하고, 유효한 Refresh Token이면 새 토큰 발급
    public ResponseRefreshTokenDto refreshToken(String accessToken, String refreshToken) {
        // Refresh Token 유효성 검증
        if (refreshToken == null || refreshToken.isBlank() || !jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }

        // 만료된 Access Token에서 userId 추출
        Long userId = jwtTokenProvider.getClaimsFromExpiredToken(accessToken).getSubject() != null
                ? Long.parseLong(jwtTokenProvider.getClaimsFromExpiredToken(accessToken).getSubject())
                : jwtTokenProvider.getUserId(refreshToken);

        // userId로 사용자 조회
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);

        int refreshTokenVersion = jwtTokenProvider.getTokenVersion(refreshToken);
        if (refreshTokenVersion != user.getTokenVersion()) {
            throw new IllegalArgumentException("만료된 세션입니다. 다시 로그인해주세요.");
        }

        // 새 토큰 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getStudentId(), user.getRole().name(), user.getTokenVersion());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getTokenVersion());

        return new ResponseRefreshTokenDto(newAccessToken, newRefreshToken);
    }

    private ResponseLoginDto.UserInfo toUserInfo(User user) {
        String roleStr = user.getRole().name().replace("ROLE_", "").toLowerCase();
        return new ResponseLoginDto.UserInfo(
                String.valueOf(user.getId()),
                user.getStudentId(),
                user.getName(),
                roleStr,
                user.getMajor(),
                user.getCollege(),
                user.getNaverId()
        );
    }

    private void cancelConfirmedTickets(Long userId, LocalDateTime now) {
        List<UserTicket> confirmedTickets =
                userTicketRepository.findAllByUserIdAndStatusForUpdate(userId, TicketStatus.CONFIRMED);
        for (UserTicket ticket : confirmedTickets) {
            ticket.cancelByWithdrawal(now);
        }
    }

    private void failProcessingIssueRequests(User user, LocalDateTime now) {
        List<TicketIssueRequest> processingRequests =
                ticketIssueRequestRepository.findAllByUserIdAndStatusForUpdate(
                        user.getId(), TicketIssueRequestStatus.PROCESSING);

        for (TicketIssueRequest request : processingRequests) {
            createWithdrawalTicketIfPossible(user, request, now);
            request.markFailed(USER_WITHDRAWN_ERROR_CODE, "회원 탈퇴로 티켓 권리포기 처리", now);
            ticketIssueRequestStatusCacheService.setFailed(
                    request.getEventId(),
                    request.getUserId(),
                    request.getRequestId(),
                    USER_WITHDRAWN_ERROR_CODE,
                    now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            );
        }
    }

    private void createWithdrawalTicketIfPossible(User user, TicketIssueRequest request, LocalDateTime now) {
        if (userTicketRepository.existsByUserIdAndEventIdAndStatusIn(
                user.getId(), request.getEventId(), CONSUMED_TICKET_STATUSES)) {
            return;
        }
        if (request.getRemainingAfterClaim() == null) {
            return;
        }
        festivalEventRepository.findById(request.getEventId()).ifPresent(event -> {
            Integer order = calculateOrder(event, request.getRemainingAfterClaim());
            if (order == null) {
                return;
            }
            try {
                userTicketRepository.save(UserTicket.cancelledByWithdrawal(
                        user,
                        event,
                        order,
                        request.getSeq(),
                        now
                ));
            } catch (DataIntegrityViolationException ignored) {
                // Concurrent consumer/withdrawal convergence is idempotent through the user/event unique key.
            }
        });
    }

    private Integer calculateOrder(FestivalEvent event, Long remainingAfterClaim) {
        if (remainingAfterClaim == null) {
            return null;
        }
        long order = event.getTotalCapacity() - remainingAfterClaim;
        if (order <= 0 || order > Integer.MAX_VALUE) {
            return null;
        }
        return (int) order;
    }

    private void leaveOpenQueues(Long userId) {
        List<FestivalEvent> openEvents = festivalEventRepository.findAllByTicketingStatus(TicketingStatus.OPEN);
        for (FestivalEvent event : openEvents) {
            if (event.getId() != null) {
                queueStateService.leaveQueue(String.valueOf(event.getId()), String.valueOf(userId));
            }
        }
    }
}
