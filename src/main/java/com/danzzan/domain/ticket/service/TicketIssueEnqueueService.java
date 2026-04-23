package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.kafka.TicketIssueKafkaSpec;
import com.danzzan.domain.ticket.kafka.TicketIssueRequestedEvent;
import com.danzzan.domain.ticket.model.entity.OutboxEvent;
import com.danzzan.domain.ticket.model.entity.OutboxEventStatus;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.repository.OutboxEventRepository;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TicketIssueEnqueueService {

    private static final String AGGREGATE_TYPE = "TICKET_ISSUE";

    private final TicketIssueRequestRepository ticketIssueRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;

    public record InFlightProcessingRequest(String requestId, Long acceptedAt) {
    }

    public record IssueRequestStatusSnapshot(
            String requestId,
            Long eventId,
            TicketIssueRequestStatus status,
            String errorCode,
            Long updatedAt
    ) {
    }

    @Transactional(readOnly = true)
    public Optional<InFlightProcessingRequest> findProcessingRequest(Long eventId, Long userId) {
        return ticketIssueRequestRepository
                .findByEventIdAndUserIdAndStatus(eventId, userId, TicketIssueRequestStatus.PROCESSING)
                .map(request -> new InFlightProcessingRequest(
                        request.getRequestId(),
                        request.getCreatedAt() == null
                                ? null
                                : request.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                ));
    }

    @Transactional(readOnly = true)
    public Optional<IssueRequestStatusSnapshot> findRequestStatus(Long eventId, Long userId, String requestId) {
        Optional<IssueRequestStatusSnapshot> cached = ticketIssueRequestStatusCacheService.get(eventId, userId, requestId)
                .map(snapshot -> new IssueRequestStatusSnapshot(
                        requestId,
                        eventId,
                        snapshot.status(),
                        snapshot.errorCode(),
                        snapshot.updatedAt()
                ));
        if (cached.isPresent()) {
            return cached;
        }

        return ticketIssueRequestRepository
                .findByRequestIdAndEventIdAndUserId(requestId, eventId, userId)
                .map(request -> {
                    Long updatedAt = request.getUpdatedAt() == null
                            ? null
                            : request.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    if (updatedAt != null) {
                        syncCache(request, updatedAt);
                    }
                    return new IssueRequestStatusSnapshot(
                            request.getRequestId(),
                            request.getEventId(),
                            request.getStatus(),
                            request.getErrorCode(),
                            updatedAt
                    );
                });
    }

    @Transactional
    public String enqueueIssueRequest(
            Long eventId,
            Long userId,
            String requestId,
            long remaining,
            Long seq,
            long acceptedAtEpochMs
    ) {
        try {
            TicketIssueRequest request = TicketIssueRequest.builder()
                    .requestId(requestId)
                    .eventId(eventId)
                    .userId(userId)
                    .status(TicketIssueRequestStatus.PROCESSING)
                    .build();
            ticketIssueRequestRepository.save(request);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(requestId)
                    .topic(TicketIssueKafkaSpec.ISSUE_REQUESTED_TOPIC_V1)
                    .eventKey(TicketIssueKafkaSpec.buildIssueRequestedKey(eventId, userId))
                    .payload(toPayload(requestId, eventId, userId, remaining, seq, acceptedAtEpochMs))
                    .status(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
            ticketIssueRequestStatusCacheService.setProcessing(eventId, userId, requestId, acceptedAtEpochMs);
            return requestId;
        } catch (DataIntegrityViolationException e) {
            String existingRequestId = ticketIssueRequestRepository
                    .findByEventIdAndUserIdAndStatus(eventId, userId, TicketIssueRequestStatus.PROCESSING)
                    .map(TicketIssueRequest::getRequestId)
                    .orElseThrow(() -> e);
            ticketIssueRequestStatusCacheService.setProcessing(eventId, userId, existingRequestId, acceptedAtEpochMs);
            return existingRequestId;
        }
    }

    private void syncCache(TicketIssueRequest request, long updatedAt) {
        if (request.getStatus() == TicketIssueRequestStatus.SUCCESS) {
            ticketIssueRequestStatusCacheService.setSuccess(
                    request.getEventId(),
                    request.getUserId(),
                    request.getRequestId(),
                    updatedAt
            );
            return;
        }
        if (request.getStatus() == TicketIssueRequestStatus.FAILED) {
            ticketIssueRequestStatusCacheService.setFailed(
                    request.getEventId(),
                    request.getUserId(),
                    request.getRequestId(),
                    request.getErrorCode(),
                    updatedAt
            );
            return;
        }
        ticketIssueRequestStatusCacheService.setProcessing(
                request.getEventId(),
                request.getUserId(),
                request.getRequestId(),
                updatedAt
        );
    }

    private String toPayload(
            String requestId,
            Long eventId,
            Long userId,
            long remaining,
            Long seq,
            long acceptedAtEpochMs
    ) {
        TicketIssueRequestedEvent payload = TicketIssueRequestedEvent.of(
                requestId,
                eventId,
                userId,
                remaining,
                seq,
                acceptedAtEpochMs,
                MDC.get("traceId")
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload serialization failed", e);
        }
    }
}
