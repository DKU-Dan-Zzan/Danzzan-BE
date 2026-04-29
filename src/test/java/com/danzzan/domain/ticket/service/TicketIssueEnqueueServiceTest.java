package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.repository.OutboxEventRepository;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketIssueEnqueueServiceTest {

    @Mock
    private TicketIssueRequestRepository ticketIssueRequestRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;

    private TicketIssueEnqueueService service;

    @BeforeEach
    void setUp() {
        service = new TicketIssueEnqueueService(
                ticketIssueRequestRepository,
                outboxEventRepository,
                new ObjectMapper(),
                ticketIssueRequestStatusCacheService
        );
    }

    @Test
    void findRequestStatus_캐시히트면_DB를_조회하지않는다() {
        when(ticketIssueRequestStatusCacheService.get(10L, 1L, "req-1"))
                .thenReturn(Optional.of(new TicketIssueRequestStatusCacheService.CachedRequestStatus(
                        TicketIssueRequestStatus.PROCESSING,
                        null,
                        1775918400000L
                )));

        Optional<TicketIssueEnqueueService.IssueRequestStatusSnapshot> snapshot =
                service.findRequestStatus(10L, 1L, "req-1");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().status()).isEqualTo(TicketIssueRequestStatus.PROCESSING);
        assertThat(snapshot.get().updatedAt()).isEqualTo(1775918400000L);
        verify(ticketIssueRequestRepository, never()).findByRequestIdAndEventIdAndUserId(any(), any(), any());
    }

    @Test
    void findRequestStatus_캐시미스면_DB조회후_캐시를동기화한다() {
        when(ticketIssueRequestStatusCacheService.get(10L, 1L, "req-1"))
                .thenReturn(Optional.empty());

        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.PROCESSING)
                .build();
        ReflectionTestUtils.setField(request, "updatedAt", LocalDateTime.of(2026, 4, 21, 14, 10, 0));
        when(ticketIssueRequestRepository.findByRequestIdAndEventIdAndUserId("req-1", 10L, 1L))
                .thenReturn(Optional.of(request));

        Optional<TicketIssueEnqueueService.IssueRequestStatusSnapshot> snapshot =
                service.findRequestStatus(10L, 1L, "req-1");

        assertThat(snapshot).isPresent();
        verify(ticketIssueRequestStatusCacheService).setProcessing(eq(10L), eq(1L), eq("req-1"), any(Long.class));
    }

    @Test
    void enqueueIssueRequest_성공하면_PROCESSING캐시를_기록한다() {
        long acceptedAt = 1775918400123L;

        String requestId = service.enqueueIssueRequest(10L, 1L, "req-1", 42L, 7L, acceptedAt);

        assertThat(requestId).isEqualTo("req-1");
        verify(ticketIssueRequestStatusCacheService).setProcessing(10L, 1L, "req-1", acceptedAt);
    }
}
