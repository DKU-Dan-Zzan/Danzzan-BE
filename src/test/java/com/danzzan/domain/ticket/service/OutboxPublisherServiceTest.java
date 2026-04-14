package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.kafka.TicketIssueKafkaSpec;
import com.danzzan.domain.ticket.model.entity.OutboxEvent;
import com.danzzan.domain.ticket.model.entity.OutboxEventStatus;
import com.danzzan.domain.ticket.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxPublisherMetrics outboxPublisherMetrics;

    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {
        outboxPublisherService = new OutboxPublisherService(
                outboxEventRepository,
                kafkaTemplate,
                outboxPublisherMetrics
        );
    }

    @Test
    void publishPendingBatch_성공시_SENT로_전환하고_헤더를_포함한다() {
        OutboxEvent event = pendingEvent(0);
        when(outboxEventRepository.findPendingBatchForPublish(eq(100))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(0L);
        when(outboxEventRepository.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING)).thenReturn(null);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(0L);

        outboxPublisherService.publishPendingBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("ticket.issue.requested.v1");
        assertThat(record.key()).isEqualTo("10:1");
        assertThat(record.value()).isEqualTo("{\"requestId\":\"r1\"}");
        assertThat(record.headers().lastHeader(TicketIssueKafkaSpec.HEADER_REQUEST_ID)).isNotNull();

        verify(outboxPublisherMetrics).incrementSuccess();
    }

    @Test
    void publishPendingBatch_실패시_retryCount증가와_백오프를_설정한다() {
        OutboxEvent event = pendingEvent(0);
        when(outboxEventRepository.findPendingBatchForPublish(eq(100))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));
        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(1L);
        when(outboxEventRepository.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING)).thenReturn(LocalDateTime.now().minusSeconds(2));
        when(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(0L);

        outboxPublisherService.publishPendingBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isNotNull();
        assertThat(event.getLastError()).contains("kafka unavailable");
        verify(outboxPublisherMetrics).incrementRetry();
    }

    @Test
    void publishPendingBatch_10회째_실패면_FAILED로_전환한다() {
        OutboxEvent event = pendingEvent(9);
        when(outboxEventRepository.findPendingBatchForPublish(eq(100))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("permanent failure")));
        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(0L);
        when(outboxEventRepository.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING)).thenReturn(null);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(1L);

        outboxPublisherService.publishPendingBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(10);
        assertThat(event.getNextRetryAt()).isNull();
        verify(outboxPublisherMetrics).incrementFailed();
    }

    private OutboxEvent pendingEvent(int retryCount) {
        return OutboxEvent.builder()
                .aggregateType("TICKET_ISSUE")
                .aggregateId("r1")
                .topic("ticket.issue.requested.v1")
                .eventKey("10:1")
                .payload("{\"requestId\":\"r1\"}")
                .status(OutboxEventStatus.PENDING)
                .retryCount(retryCount)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }
}
