package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.ticket.kafka.TicketIssueRequestedEvent;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketIssueConsumerServiceTest {

    @Mock
    private TicketIssueRequestRepository ticketIssueRequestRepository;

    @Mock
    private UserTicketRepository userTicketRepository;

    @Mock
    private FestivalEventRepository festivalEventRepository;

    @Mock
    private UserRepository userRepository;

    private TicketIssueConsumerService service;

    @BeforeEach
    void setUp() {
        service = new TicketIssueConsumerService(
                ticketIssueRequestRepository,
                userTicketRepository,
                festivalEventRepository,
                userRepository
        );
    }

    @Test
    void processIssueRequested_PROCESSING요청이면_티켓저장후_SUCCESS로_전환한다() {
        TicketIssueRequestedEvent event = TicketIssueRequestedEvent.of(
                "req-1", 10L, 1L, 42L, 1234L, 1773486180000L, null
        );
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        FestivalEvent festivalEvent = FestivalEvent.builder()
                .title("dan")
                .eventDate(LocalDate.now())
                .ticketingStartTime(LocalDateTime.now())
                .totalCapacity(100)
                .build();
        User user = User.builder()
                .studentId("321")
                .password("pw")
                .name("u")
                .college("c")
                .major("m")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build();

        when(ticketIssueRequestRepository.findByRequestId(eq("req-1"))).thenReturn(Optional.of(request));
        when(festivalEventRepository.findById(eq(10L))).thenReturn(Optional.of(festivalEvent));
        when(userRepository.findByIdForUpdate(eq(1L))).thenReturn(Optional.of(user));
        when(userTicketRepository.save(any(UserTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketIssueConsumerService.ProcessingResult result = service.processIssueRequested(event);

        assertThat(result).isEqualTo(TicketIssueConsumerService.ProcessingResult.ISSUED);
        assertThat(request.getStatus()).isEqualTo(TicketIssueRequestStatus.SUCCESS);
        assertThat(request.getCompletedAt()).isNotNull();
        verify(userTicketRepository).save(any(UserTicket.class));
    }

    @Test
    void processIssueRequested_중복티켓충돌이면_이미성공으로_수렴한다() {
        TicketIssueRequestedEvent event = TicketIssueRequestedEvent.of(
                "req-1", 10L, 1L, 42L, 1234L, 1773486180000L, null
        );
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        FestivalEvent festivalEvent = FestivalEvent.builder()
                .title("dan")
                .eventDate(LocalDate.now())
                .ticketingStartTime(LocalDateTime.now())
                .totalCapacity(100)
                .build();
        User user = User.builder()
                .studentId("321")
                .password("pw")
                .name("u")
                .college("c")
                .major("m")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build();

        when(ticketIssueRequestRepository.findByRequestId(eq("req-1"))).thenReturn(Optional.of(request));
        when(festivalEventRepository.findById(eq(10L))).thenReturn(Optional.of(festivalEvent));
        when(userRepository.findByIdForUpdate(eq(1L))).thenReturn(Optional.of(user));
        when(userTicketRepository.save(any(UserTicket.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(userTicketRepository.existsByUserIdAndEventId(eq(1L), eq(10L))).thenReturn(true);

        TicketIssueConsumerService.ProcessingResult result = service.processIssueRequested(event);

        assertThat(result).isEqualTo(TicketIssueConsumerService.ProcessingResult.ISSUED);
        assertThat(request.getStatus()).isEqualTo(TicketIssueRequestStatus.SUCCESS);
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    void processIssueRequested_탈퇴유저면_CONFIRMED대신_권리포기티켓으로_저장하고_FAILED처리한다() {
        TicketIssueRequestedEvent event = TicketIssueRequestedEvent.of(
                "req-1", 10L, 1L, 42L, 1234L, 1773486180000L, null
        );
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .remainingAfterClaim(42L)
                .seq(1234L)
                .acceptedAt(1773486180000L)
                .build();
        FestivalEvent festivalEvent = FestivalEvent.builder()
                .title("dan")
                .eventDate(LocalDate.now())
                .ticketingStartTime(LocalDateTime.now())
                .totalCapacity(100)
                .build();
        User user = User.builder()
                .studentId("withdrawn:1:test")
                .password("pw")
                .name("탈퇴회원")
                .college("WITHDRAWN")
                .major("WITHDRAWN")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "deleted", true);

        when(ticketIssueRequestRepository.findByRequestId(eq("req-1"))).thenReturn(Optional.of(request));
        when(festivalEventRepository.findById(eq(10L))).thenReturn(Optional.of(festivalEvent));
        when(userRepository.findByIdForUpdate(eq(1L))).thenReturn(Optional.of(user));
        when(userTicketRepository.existsByUserIdAndEventIdAndStatusIn(
                eq(1L), eq(10L), any()
        )).thenReturn(false);

        TicketIssueConsumerService.ProcessingResult result = service.processIssueRequested(event);

        assertThat(result).isEqualTo(TicketIssueConsumerService.ProcessingResult.WITHDRAWN_CANCELLED);
        assertThat(request.getStatus()).isEqualTo(TicketIssueRequestStatus.FAILED);
        assertThat(request.getErrorCode()).isEqualTo("USER_WITHDRAWN");
        verify(userTicketRepository).save(argThat(ticket ->
                ticket.getStatus() == TicketStatus.CANCELLED_WITHDRAWAL
                        && ticket.getCancelReason().equals("USER_WITHDRAWAL")
                        && ticket.getTicketingOrder() == 58
        ));
    }
}
