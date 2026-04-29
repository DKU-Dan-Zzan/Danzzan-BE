package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.ticket.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.ticketing.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

    private final OutboxPublisherService outboxPublisherService;

    @Scheduled(fixedDelayString = "${app.ticketing.outbox.publisher.fixed-delay-ms:1000}")
    public void run() {
        try {
            outboxPublisherService.publishPendingBatch();
        } catch (Exception e) {
            log.error("outbox publisher loop failed", e);
        }
    }
}
