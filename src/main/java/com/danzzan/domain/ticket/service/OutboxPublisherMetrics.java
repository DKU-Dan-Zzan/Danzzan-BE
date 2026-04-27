package com.danzzan.domain.ticket.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class OutboxPublisherMetrics {

    private final Counter publishSuccessTotal;
    private final Counter publishRetryTotal;
    private final Counter publishFailedTotal;

    private final AtomicLong pendingCount = new AtomicLong(0);
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong(0);

    public OutboxPublisherMetrics(MeterRegistry registry) {
        this.publishSuccessTotal = Counter.builder("outbox_publish_success_total").register(registry);
        this.publishRetryTotal = Counter.builder("outbox_publish_retry_total").register(registry);
        this.publishFailedTotal = Counter.builder("outbox_publish_failed_total").register(registry);

        Gauge.builder("outbox_pending_count", pendingCount, AtomicLong::get).register(registry);
        Gauge.builder("outbox_oldest_pending_age_seconds", oldestPendingAgeSeconds, AtomicLong::get)
                .register(registry);
    }

    public void incrementSuccess() {
        publishSuccessTotal.increment();
    }

    public void incrementRetry() {
        publishRetryTotal.increment();
    }

    public void incrementFailed() {
        publishFailedTotal.increment();
    }

    public void updatePendingMetrics(long pending, long oldestAgeSeconds) {
        pendingCount.set(Math.max(0L, pending));
        oldestPendingAgeSeconds.set(Math.max(0L, oldestAgeSeconds));
    }
}
