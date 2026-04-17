package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.kafka.TicketIssueKafkaSpec;
import com.danzzan.domain.ticket.model.entity.OutboxEvent;
import com.danzzan.domain.ticket.model.entity.OutboxEventStatus;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.repository.OutboxEventRepository;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class TicketIssueEnqueueServiceTest {

    @Mock
    private TicketIssueRequestRepository ticketIssueRequestRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private TicketIssueEnqueueService ticketIssueEnqueueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ticketIssueEnqueueService = new TicketIssueEnqueueService(
                ticketIssueRequestRepository,
                outboxEventRepository,
                objectMapper
        );

        lenient().when(ticketIssueRequestRepository.save(any(TicketIssueRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void enqueueIssueRequest_아웃박스에_task2_스키마를_저장한다() throws Exception {
        MDC.put("traceId", "trace-abc-1");

        ticketIssueEnqueueService.enqueueIssueRequest(
                10L,
                1L,
                "2d1a4c9f-1fd1-4ef9-b442-c344a1d3950a",
                42L,
                1234L,
                1773486180000L
        );

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();

        assertThat(savedOutbox.getTopic()).isEqualTo(TicketIssueKafkaSpec.ISSUE_REQUESTED_TOPIC_V1);
        assertThat(savedOutbox.getEventKey()).isEqualTo(TicketIssueKafkaSpec.buildIssueRequestedKey(10L, 1L));
        assertThat(savedOutbox.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        JsonNode payload = objectMapper.readTree(savedOutbox.getPayload());
        assertThat(payload.get("eventType").asText())
                .isEqualTo(TicketIssueKafkaSpec.EVENT_TYPE_TICKET_ISSUE_REQUESTED);
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(TicketIssueKafkaSpec.EVENT_VERSION_V1);
        assertThat(payload.get("requestId").asText()).isEqualTo("2d1a4c9f-1fd1-4ef9-b442-c344a1d3950a");
        assertThat(payload.get("eventId").asLong()).isEqualTo(10L);
        assertThat(payload.get("userId").asLong()).isEqualTo(1L);
        assertThat(payload.get("remaining").asLong()).isEqualTo(42L);
        assertThat(payload.get("seq").asLong()).isEqualTo(1234L);
        assertThat(payload.get("claimedAt").asLong()).isEqualTo(1773486180000L);
        assertThat(payload.get("traceId").asText()).isEqualTo("trace-abc-1");
    }

    @Test
    void enqueueIssueRequest_seq와_traceId가_없으면_payload에서_생략한다() throws Exception {
        ticketIssueEnqueueService.enqueueIssueRequest(
                10L,
                1L,
                "2d1a4c9f-1fd1-4ef9-b442-c344a1d3950a",
                42L,
                null,
                1773486180000L
        );

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        JsonNode payload = objectMapper.readTree(outboxCaptor.getValue().getPayload());

        assertThat(payload.has("seq")).isFalse();
        assertThat(payload.has("traceId")).isFalse();
    }

    @Test
    void enqueueIssueRequest_요청상태를_PROCESSING으로_저장한다() {
        ticketIssueEnqueueService.enqueueIssueRequest(
                10L,
                1L,
                "2d1a4c9f-1fd1-4ef9-b442-c344a1d3950a",
                42L,
                null,
                1773486180000L
        );

        ArgumentCaptor<TicketIssueRequest> requestCaptor = ArgumentCaptor.forClass(TicketIssueRequest.class);
        verify(ticketIssueRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(TicketIssueRequestStatus.PROCESSING);
    }

    @Test
    void findRequestStatus_요청이있으면_snapshot을_반환한다() {
        TicketIssueRequest request = TicketIssueRequest.builder()
                .requestId("req-1")
                .eventId(10L)
                .userId(1L)
                .status(TicketIssueRequestStatus.SUCCESS)
                .build();
        request.prePersist();
        request.markSuccess(LocalDateTime.of(2026, 4, 17, 13, 10, 11));

        when(ticketIssueRequestRepository.findByRequestIdAndEventIdAndUserId(eq("req-1"), eq(10L), eq(1L)))
                .thenReturn(Optional.of(request));

        Optional<TicketIssueEnqueueService.IssueRequestStatusSnapshot> result =
                ticketIssueEnqueueService.findRequestStatus(10L, 1L, "req-1");

        assertThat(result).isPresent();
        assertThat(result.get().requestId()).isEqualTo("req-1");
        assertThat(result.get().eventId()).isEqualTo(10L);
        assertThat(result.get().status()).isEqualTo(TicketIssueRequestStatus.SUCCESS);
        assertThat(result.get().errorCode()).isNull();
        assertThat(result.get().updatedAt()).isNotNull();
    }

    @Test
    void findRequestStatus_요청이없으면_empty를_반환한다() {
        when(ticketIssueRequestRepository.findByRequestIdAndEventIdAndUserId(eq("req-none"), eq(10L), eq(1L)))
                .thenReturn(Optional.empty());

        Optional<TicketIssueEnqueueService.IssueRequestStatusSnapshot> result =
                ticketIssueEnqueueService.findRequestStatus(10L, 1L, "req-none");

        assertThat(result).isEmpty();
    }
}
