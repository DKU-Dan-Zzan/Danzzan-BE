package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.kafka.TicketIssueKafkaSpec;
import com.danzzan.domain.ticket.model.entity.OutboxEvent;
import com.danzzan.domain.ticket.model.entity.OutboxEventStatus;
import com.danzzan.domain.ticket.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private static final int MAX_RETRY_COUNT = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublisherMetrics outboxPublisherMetrics;

    @Value("${app.ticketing.outbox.publisher.batch-size:100}")
    private int batchSize;

    @Transactional
    public void publishPendingBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> batch = outboxEventRepository.findPendingBatchForPublish(batchSize, now);
        List<PublishAttempt> attempts = new ArrayList<>(batch.size());

        for (OutboxEvent outboxEvent : batch) {
            try {
                attempts.add(new PublishAttempt(outboxEvent, publishAsync(outboxEvent)));
            } catch (Exception e) {
                handlePublishFailure(outboxEvent, now, e);
            }
        }

        for (PublishAttempt attempt : attempts) {
            try {
                attempt.future().get();
                OutboxEvent outboxEvent = attempt.outboxEvent();
                outboxEvent.markSent(now);
                outboxPublisherMetrics.incrementSuccess();
            } catch (Exception e) {
                handlePublishFailure(attempt.outboxEvent(), now, e);
            }
        }

        updatePendingMetrics(now);
        warnIfFailedRowsExist();
    }

    private CompletableFuture<SendResult<String, String>> publishAsync(OutboxEvent outboxEvent) {
        long producedAt = System.currentTimeMillis();
        Map<String, String> headers = TicketIssueKafkaSpec.buildIssueRequestedHeaders(
                outboxEvent.getAggregateId(),
                producedAt
        );
        ProducerRecord<String, String> record = new ProducerRecord<>(
                outboxEvent.getTopic(),
                outboxEvent.getEventKey(),
                outboxEvent.getPayload()
        );
        headers.forEach((headerName, headerValue) ->
                record.headers().add(headerName, headerValue.getBytes(StandardCharsets.UTF_8)));

        return kafkaTemplate.send(record);
    }

    private void handlePublishFailure(OutboxEvent outboxEvent, LocalDateTime now, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        int retryCount = outboxEvent.increaseRetryCount();
        String errorMessage = extractErrorMessage(e);
        outboxPublisherMetrics.incrementRetry();

        if (retryCount >= MAX_RETRY_COUNT) {
            outboxEvent.markFailed(errorMessage);
            outboxPublisherMetrics.incrementFailed();
            log.warn(
                    "outbox publish permanently failed aggregateId={} retryCount={} error={}",
                    outboxEvent.getAggregateId(),
                    retryCount,
                    errorMessage
            );
            return;
        }

        LocalDateTime nextRetryAt = now.plusSeconds(backoffSeconds(retryCount));
        outboxEvent.markRetry(nextRetryAt, errorMessage);
        log.warn(
                "outbox publish failed, will retry aggregateId={} retryCount={} nextRetryAt={} error={}",
                outboxEvent.getAggregateId(),
                retryCount,
                nextRetryAt,
                errorMessage
        );
    }

    private long backoffSeconds(int retryCount) {
        if (retryCount <= 1) {
            return 1L;
        }
        if (retryCount == 2) {
            return 5L;
        }
        if (retryCount == 3) {
            return 30L;
        }
        return 60L;
    }

    private void updatePendingMetrics(LocalDateTime now) {
        long pendingCount = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        LocalDateTime oldestPending = outboxEventRepository.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING);
        long oldestAgeSeconds = oldestPending == null ? 0L : Math.max(0L, Duration.between(oldestPending, now).getSeconds());
        outboxPublisherMetrics.updatePendingMetrics(pendingCount, oldestAgeSeconds);
    }

    private void warnIfFailedRowsExist() {
        long failedCount = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
        if (failedCount > 0) {
            log.warn("outbox failed rows detected count={}", failedCount);
        }
    }

    private String extractErrorMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    private record PublishAttempt(
            OutboxEvent outboxEvent,
            CompletableFuture<SendResult<String, String>> future
    ) {
    }
}
