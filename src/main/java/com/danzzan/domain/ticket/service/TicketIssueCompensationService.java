package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.model.entity.TicketIssueCompensationLog;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.repository.TicketIssueCompensationLogRepository;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketIssueCompensationService {

    private static final String PROCESSING_FAILED_CODE = "RESERVE_PROCESSING_FAILED";
    private static final String ROLLBACK_RESULT_SUCCESS = "SUCCESS";
    private static final String ROLLBACK_RESULT_RETRY_PENDING = "RETRY_PENDING";
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_ATTEMPTS = 20;

    private final TicketIssueRequestRepository ticketIssueRequestRepository;
    private final TicketIssueCompensationLogRepository ticketIssueCompensationLogRepository;
    private final ClaimService claimService;
    private final TicketStatusService ticketStatusService;
    private final TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;
    private final TicketIssueCompensationMetrics ticketIssueCompensationMetrics;

    @Value("${app.ticketing.async.reserve.failed-ttl-seconds:300}")
    private long failedTtlSeconds = 300L;

    @Value("${app.ticketing.compensation.batch-size:100}")
    private int compensationBatchSize = DEFAULT_BATCH_SIZE;

    @Value("${app.ticketing.compensation.max-attempts:20}")
    private int maxCompensationAttempts = DEFAULT_MAX_ATTEMPTS;

    @Transactional
    public void compensate(String requestId, Long eventId, Long userId, String errorReason) {
        ticketIssueRequestRepository.findByRequestIdForUpdate(requestId)
                .ifPresentOrElse(
                        request -> compensateInternal(request, errorReason),
                        () -> log.warn("compensation skipped: request not found requestId={} eventId={} userId={}",
                                requestId, eventId, userId)
                );
        updatePendingCountMetric();
    }

    @Transactional
    public void retryPendingCompensations() {
        List<TicketIssueRequest> pendingRequests =
                ticketIssueRequestRepository.findPendingCompensationBatch(compensationBatchSize, maxCompensationAttempts);
        for (TicketIssueRequest request : pendingRequests) {
            compensateInternal(request, "pending compensation retry");
        }
        updatePendingCountMetric();
    }

    private void compensateInternal(TicketIssueRequest request, String errorReason) {
        if (request.getStatus() == TicketIssueRequestStatus.SUCCESS) {
            log.info("compensation skipped: already success requestId={}", request.getRequestId());
            return;
        }
        if (request.getStatus() == TicketIssueRequestStatus.FAILED && request.isCompensated()) {
            log.info("compensation skipped: already compensated requestId={}", request.getRequestId());
            return;
        }

        String eventId = String.valueOf(request.getEventId());
        String userId = String.valueOf(request.getUserId());
        int currentAttempt = request.getCompensationAttempts() + 1;
        boolean rollbackSuccess = claimService.rollback(eventId, userId);

        if (rollbackSuccess) {
            LocalDateTime now = LocalDateTime.now();
            request.markCompensatedFailure(PROCESSING_FAILED_CODE, trimReason(errorReason), now);
            ticketStatusService.setFailed(eventId, userId, failedTtlSeconds);
            ticketIssueRequestStatusCacheService.setFailed(
                    request.getEventId(),
                    request.getUserId(),
                    request.getRequestId(),
                    PROCESSING_FAILED_CODE,
                    now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            );
            upsertCompensationLog(request, ROLLBACK_RESULT_SUCCESS, currentAttempt, errorReason);
            ticketIssueCompensationMetrics.incrementSuccess();
            log.info(
                    "compensation success requestId={} eventId={} userId={} rollbackResult={} attempt={}",
                    request.getRequestId(),
                    request.getEventId(),
                    request.getUserId(),
                    ROLLBACK_RESULT_SUCCESS,
                    currentAttempt
            );
            return;
        }

        int failedAttempt = request.markCompensationPending();
        upsertCompensationLog(request, ROLLBACK_RESULT_RETRY_PENDING, failedAttempt, errorReason);
        ticketIssueCompensationMetrics.incrementRetry();

        if (failedAttempt >= maxCompensationAttempts) {
            ticketIssueCompensationMetrics.incrementFailed();
            log.error(
                    "compensation permanently pending requestId={} eventId={} userId={} rollbackResult={} attempt={}",
                    request.getRequestId(),
                    request.getEventId(),
                    request.getUserId(),
                    ROLLBACK_RESULT_RETRY_PENDING,
                    failedAttempt
            );
            return;
        }

        log.warn(
                "compensation retry scheduled requestId={} eventId={} userId={} rollbackResult={} attempt={}",
                request.getRequestId(),
                request.getEventId(),
                request.getUserId(),
                ROLLBACK_RESULT_RETRY_PENDING,
                failedAttempt
        );
    }

    private void upsertCompensationLog(
            TicketIssueRequest request,
            String rollbackResult,
            int attempt,
            String errorReason
    ) {
        try {
            ticketIssueCompensationLogRepository.findByRequestId(request.getRequestId())
                    .ifPresentOrElse(
                            existing -> existing.updateResult(rollbackResult, attempt, trimReason(errorReason)),
                            () -> ticketIssueCompensationLogRepository.save(
                                    TicketIssueCompensationLog.builder()
                                            .requestId(request.getRequestId())
                                            .eventId(request.getEventId())
                                            .userId(request.getUserId())
                                            .rollbackResult(rollbackResult)
                                            .attempt(attempt)
                                            .errorReason(trimReason(errorReason))
                                            .build()
                            )
                    );
        } catch (DataIntegrityViolationException e) {
            log.info("compensation log duplicated requestId={} (treated as idempotent)", request.getRequestId());
        }
    }

    private void updatePendingCountMetric() {
        long pendingCount = ticketIssueRequestRepository.countByCompensationPendingTrue();
        ticketIssueCompensationMetrics.updatePendingCount(pendingCount);
    }

    private String trimReason(String errorReason) {
        if (errorReason == null) {
            return null;
        }
        if (errorReason.length() <= 500) {
            return errorReason;
        }
        return errorReason.substring(0, 500);
    }
}
