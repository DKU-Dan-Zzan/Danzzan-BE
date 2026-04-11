package com.danzzan.domain.ticket.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketIssueEnqueueService {

    private static final String ISSUE_REQUESTED_TOPIC = "ticket.issue.requested.v1";
    private static final String AGGREGATE_TYPE = "TICKET_ISSUE";

    private final TicketIssueRequestRepository ticketIssueRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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
                    .topic(ISSUE_REQUESTED_TOPIC)
                    .eventKey(eventId + ":" + userId)
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "TICKET_ISSUE_REQUESTED");
        payload.put("eventVersion", 1);
        payload.put("requestId", requestId);
        payload.put("eventId", eventId);
        payload.put("userId", userId);
        payload.put("remaining", remaining);
        payload.put("seq", seq);
        payload.put("claimedAt", acceptedAtEpochMs);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload serialization failed", e);
        }
    }
}
