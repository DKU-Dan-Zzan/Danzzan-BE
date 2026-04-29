package com.danzzan.domain.ticket.consumer;

import com.danzzan.domain.ticket.consumer.exception.NonRetryableTicketIssueException;
import com.danzzan.domain.ticket.kafka.TicketIssueRequestedEvent;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketIssueCompensationService;
import com.danzzan.domain.ticket.service.TicketIssueConsumerService;
import com.danzzan.domain.ticket.service.TicketIssueRequestStatusCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketIssueRequestedConsumerTest {

    @Mock
    private TicketIssueConsumerService ticketIssueConsumerService;

    @Mock
    private QueueStateService queueStateService;

    @Mock
    private TicketIssueCompensationService ticketIssueCompensationService;

    @Mock
    private TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Acknowledgment acknowledgment;

    private TicketIssueRequestedConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new TicketIssueRequestedConsumer(
                objectMapper,
                ticketIssueConsumerService,
                queueStateService,
                ticketIssueCompensationService,
                ticketIssueRequestStatusCacheService,
                redisTemplate
        );
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void consume_정상처리면_SUCCESS상태저장_markDone_ack() throws Exception {
        TicketIssueRequestedEvent event = TicketIssueRequestedEvent.of(
                "req-1", 10L, 1L, 42L, 1234L, 1773486180000L, null
        );
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("ticket.issue.requested.v1", 0, 0L, "10:1", payload);

        when(ticketIssueConsumerService.processIssueRequested(any()))
                .thenReturn(TicketIssueConsumerService.ProcessingResult.ISSUED);

        consumer.consume(record, acknowledgment);

        verify(valueOperations).set(eq("ticket:10:status:1"), eq(TicketRequestStatus.SUCCESS.name()));
        verify(ticketIssueRequestStatusCacheService).setSuccess(eq(10L), eq(1L), eq("req-1"), any(Long.class));
        verify(queueStateService).markDone(eq("10"), eq("1"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_비재시도예외면_compensate후_ack() throws Exception {
        TicketIssueRequestedEvent event = TicketIssueRequestedEvent.of(
                "req-1", 10L, 1L, 42L, 1234L, 1773486180000L, null
        );
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("ticket.issue.requested.v1", 0, 0L, "10:1", payload);

        when(ticketIssueConsumerService.processIssueRequested(any()))
                .thenThrow(new NonRetryableTicketIssueException("invalid payload"));

        consumer.consume(record, acknowledgment);

        verify(ticketIssueCompensationService).compensate(eq("req-1"), eq(10L), eq(1L), eq("invalid payload"));
        verify(acknowledgment).acknowledge();
        verify(queueStateService, never()).markDone(any(), any());
    }

    @Test
    void consume_탈퇴유저권리포기결과면_성공처리나_compensate없이_ack() throws Exception {
        TicketIssueRequestedEvent event = TicketIssueRequestedEvent.of(
                "req-1", 10L, 1L, 42L, 1234L, 1773486180000L, null
        );
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("ticket.issue.requested.v1", 0, 0L, "10:1", payload);

        when(ticketIssueConsumerService.processIssueRequested(any()))
                .thenReturn(TicketIssueConsumerService.ProcessingResult.WITHDRAWN_CANCELLED);

        consumer.consume(record, acknowledgment);

        verify(valueOperations).set(eq("ticket:10:status:1"), eq(TicketRequestStatus.FAILED.name()));
        verify(ticketIssueRequestStatusCacheService).setFailed(eq(10L), eq(1L), eq("req-1"), eq("USER_WITHDRAWN"), any(Long.class));
        verify(ticketIssueRequestStatusCacheService, never()).setSuccess(any(), any(), any(), any(Long.class));
        verify(ticketIssueCompensationService, never()).compensate(any(), any(), any(), any());
        verify(queueStateService, never()).markDone(any(), any());
        verify(acknowledgment).acknowledge();
    }
}
