package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.repository.TicketIssueCompensationLogRepository;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketIssueCompensationServiceTest {

    @Mock
    private TicketIssueRequestRepository ticketIssueRequestRepository;

    @Mock
    private TicketIssueCompensationLogRepository ticketIssueCompensationLogRepository;

    @Mock
    private ClaimService claimService;

    @Mock
    private TicketStatusService ticketStatusService;

    @Mock
    private TicketIssueCompensationMetrics ticketIssueCompensationMetrics;

    private TicketIssueCompensationService service;

    @BeforeEach
    void setUp() {
        service = new TicketIssueCompensationService(
                ticketIssueRequestRepository,
                ticketIssueCompensationLogRepository,
                claimService,
                ticketStatusService,
                ticketIssueCompensationMetrics
        );
    }

    @Test
    void compensate_롤백성공이면_FAILED_보상완료로_수렴한다() {
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        when(ticketIssueRequestRepository.findByRequestIdForUpdate(eq("req-1"))).thenReturn(Optional.of(request));
        when(claimService.rollback(eq("10"), eq("1"))).thenReturn(true);
        when(ticketIssueRequestRepository.countByCompensationPendingTrue()).thenReturn(0L);

        service.compensate("req-1", 10L, 1L, "invalid payload");

        assertThat(request.getStatus()).isEqualTo(TicketIssueRequestStatus.FAILED);
        assertThat(request.isCompensated()).isTrue();
        assertThat(request.isCompensationPending()).isFalse();
        assertThat(request.getCompletedAt()).isNotNull();
        verify(ticketStatusService).setFailed(eq("10"), eq("1"), eq(300L));
        verify(ticketIssueCompensationMetrics).incrementSuccess();
        verify(ticketIssueCompensationLogRepository).save(any());
    }

    @Test
    void compensate_롤백실패면_PROCESSING유지_pending으로_마킹한다() {
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        when(ticketIssueRequestRepository.findByRequestIdForUpdate(eq("req-1"))).thenReturn(Optional.of(request));
        when(claimService.rollback(eq("10"), eq("1"))).thenReturn(false);
        when(ticketIssueRequestRepository.countByCompensationPendingTrue()).thenReturn(1L);

        service.compensate("req-1", 10L, 1L, "redis timeout");

        assertThat(request.getStatus()).isEqualTo(TicketIssueRequestStatus.PROCESSING);
        assertThat(request.isCompensated()).isFalse();
        assertThat(request.isCompensationPending()).isTrue();
        assertThat(request.getCompensationAttempts()).isEqualTo(1);
        verify(ticketStatusService, never()).setFailed(any(), any(), anyLong());
        verify(ticketIssueCompensationMetrics).incrementRetry();
        verify(ticketIssueCompensationLogRepository).save(any());
    }

    @Test
    void compensate_이미보상완료된요청이면_아무작업도하지않는다() {
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        request.markCompensatedFailure("RESERVE_PROCESSING_FAILED", "done", LocalDateTime.now());
        when(ticketIssueRequestRepository.findByRequestIdForUpdate(eq("req-1"))).thenReturn(Optional.of(request));
        when(ticketIssueRequestRepository.countByCompensationPendingTrue()).thenReturn(0L);

        service.compensate("req-1", 10L, 1L, "ignored");

        verify(claimService, never()).rollback(any(), any());
        verify(ticketIssueCompensationLogRepository, never()).save(any());
    }

    @Test
    void retryPendingCompensations_pending요청을_재시도한다() {
        TicketIssueRequest pending = TicketIssueRequest.builder()
                .requestId("req-2")
                .eventId(11L)
                .userId(2L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        pending.markCompensationPending();
        when(ticketIssueRequestRepository.findPendingCompensationBatch(eq(100), eq(20))).thenReturn(List.of(pending));
        when(claimService.rollback(eq("11"), eq("2"))).thenReturn(true);
        when(ticketIssueRequestRepository.countByCompensationPendingTrue()).thenReturn(0L);

        service.retryPendingCompensations();

        assertThat(pending.getStatus()).isEqualTo(TicketIssueRequestStatus.FAILED);
        assertThat(pending.isCompensated()).isTrue();
        assertThat(pending.isCompensationPending()).isFalse();
        verify(ticketStatusService).setFailed(eq("11"), eq("2"), eq(300L));
        verify(ticketIssueCompensationMetrics).incrementSuccess();
    }
}
