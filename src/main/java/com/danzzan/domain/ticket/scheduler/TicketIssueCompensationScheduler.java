package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.ticket.service.TicketIssueCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.ticketing.compensation.retry.enabled", havingValue = "true", matchIfMissing = true)
public class TicketIssueCompensationScheduler {

    private final TicketIssueCompensationService ticketIssueCompensationService;

    @Scheduled(fixedDelayString = "${app.ticketing.compensation.retry.fixed-delay-ms:1000}")
    public void run() {
        try {
            ticketIssueCompensationService.retryPendingCompensations();
        } catch (Exception e) {
            log.error("ticket compensation scheduler loop failed", e);
        }
    }
}
