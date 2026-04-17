package com.danzzan.domain.ticket.consumer;

import com.danzzan.domain.ticket.consumer.exception.NonRetryableTicketIssueException;
import com.danzzan.domain.ticket.kafka.TicketIssueKafkaSpec;
import com.danzzan.domain.ticket.kafka.TicketIssueRequestedEvent;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketIssueCompensationService;
import com.danzzan.domain.ticket.service.TicketIssueConsumerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketIssueRequestedConsumer {

    private final ObjectMapper objectMapper;
    private final TicketIssueConsumerService ticketIssueConsumerService;
    private final QueueStateService queueStateService;
    private final TicketIssueCompensationService ticketIssueCompensationService;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = "${app.ticketing.kafka.topics.issue-requested:ticket.issue.requested.v1}",
            groupId = "${spring.kafka.consumer.group-id:ticket-issue-consumer-v1}",
            containerFactory = "ticketIssueKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        TicketIssueRequestedEvent event = null;
        try {
            event = parse(record.value());
            validate(event);

            TicketIssueConsumerService.ProcessingResult result = ticketIssueConsumerService.processIssueRequested(event);
            if (result == TicketIssueConsumerService.ProcessingResult.ISSUED
                    || result == TicketIssueConsumerService.ProcessingResult.ALREADY_SUCCESS) {
                markSuccessAndDone(event);
            }
            acknowledgment.acknowledge();
        } catch (NonRetryableTicketIssueException e) {
            handleNonRetryable(event, e);
            acknowledgment.acknowledge();
        }
    }

    private TicketIssueRequestedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, TicketIssueRequestedEvent.class);
        } catch (JsonProcessingException e) {
            throw new NonRetryableTicketIssueException("invalid payload json");
        }
    }

    private void validate(TicketIssueRequestedEvent event) {
        if (event.requestId() == null || event.eventId() == null || event.userId() == null) {
            throw new NonRetryableTicketIssueException("missing required fields");
        }
        if (event.remaining() < 0L || event.claimedAt() <= 0L) {
            throw new NonRetryableTicketIssueException("invalid numeric fields");
        }
        if (!TicketIssueKafkaSpec.EVENT_TYPE_TICKET_ISSUE_REQUESTED.equals(event.eventType())) {
            throw new NonRetryableTicketIssueException("unsupported eventType=" + event.eventType());
        }
        if (event.eventVersion() != TicketIssueKafkaSpec.EVENT_VERSION_V1) {
            throw new NonRetryableTicketIssueException("unsupported eventVersion=" + event.eventVersion());
        }
    }

    private void markSuccessAndDone(TicketIssueRequestedEvent event) {
        String eventId = String.valueOf(event.eventId());
        String userId = String.valueOf(event.userId());
        redisTemplate.opsForValue().set(TicketRedisKeys.statusKey(eventId, userId), TicketRequestStatus.SUCCESS.name());
        try {
            queueStateService.markDone(eventId, userId);
        } catch (Exception e) {
            log.error("markDone failed after issue success eventId={} userId={}", eventId, userId, e);
        }
    }

    private void handleNonRetryable(TicketIssueRequestedEvent event, NonRetryableTicketIssueException e) {
        log.warn("non-retryable ticket issue message dropped reason={}", e.getMessage());
        if (event == null) {
            return;
        }
        ticketIssueCompensationService.compensate(
                event.requestId(),
                event.eventId(),
                event.userId(),
                e.getMessage()
        );
    }
}
