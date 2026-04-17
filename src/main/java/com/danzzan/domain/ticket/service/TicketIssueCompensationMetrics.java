package com.danzzan.domain.ticket.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class TicketIssueCompensationMetrics {

    private final Counter compensationSuccessTotal;
    private final Counter compensationRetryTotal;
    private final Counter compensationFailedTotal;
    private final AtomicLong compensationPendingCount = new AtomicLong(0);

    public TicketIssueCompensationMetrics(MeterRegistry registry) {
        this.compensationSuccessTotal = Counter.builder("ticket_compensation_success_total").register(registry);
        this.compensationRetryTotal = Counter.builder("ticket_compensation_retry_total").register(registry);
        this.compensationFailedTotal = Counter.builder("ticket_compensation_failed_total").register(registry);
        Gauge.builder("ticket_compensation_pending_count", compensationPendingCount, AtomicLong::get).register(registry);
    }

    public void incrementSuccess() {
        compensationSuccessTotal.increment();
    }

    public void incrementRetry() {
        compensationRetryTotal.increment();
    }

    public void incrementFailed() {
        compensationFailedTotal.increment();
    }

    public void updatePendingCount(long pendingCount) {
        compensationPendingCount.set(Math.max(0L, pendingCount));
    }
}
