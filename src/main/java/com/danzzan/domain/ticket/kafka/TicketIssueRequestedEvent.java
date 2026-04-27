package com.danzzan.domain.ticket.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketIssueRequestedEvent(
        String eventType,
        int eventVersion,
        String requestId,
        Long eventId,
        Long userId,
        long remaining,
        Long seq,
        long claimedAt,
        String traceId
) {

    public static TicketIssueRequestedEvent of(
            String requestId,
            Long eventId,
            Long userId,
            long remaining,
            Long seq,
            long claimedAt,
            String traceId
    ) {
        return new TicketIssueRequestedEvent(
                TicketIssueKafkaSpec.EVENT_TYPE_TICKET_ISSUE_REQUESTED,
                TicketIssueKafkaSpec.EVENT_VERSION_V1,
                requestId,
                eventId,
                userId,
                remaining,
                seq,
                claimedAt,
                traceId
        );
    }
}
