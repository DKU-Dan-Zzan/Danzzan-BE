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

    public record InFlightProcessingRequest(String requestId, Long acceptedAt) {
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
            return requestId;
        } catch (DataIntegrityViolationException e) {
            return ticketIssueRequestRepository
                    .findByEventIdAndUserIdAndStatus(eventId, userId, TicketIssueRequestStatus.PROCESSING)
                    .map(TicketIssueRequest::getRequestId)
                    .orElseThrow(() -> e);
        }
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
