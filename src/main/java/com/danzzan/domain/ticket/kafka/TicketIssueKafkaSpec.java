package com.danzzan.domain.ticket.kafka;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TicketIssueKafkaSpec {

    private TicketIssueKafkaSpec() {
    }

    public static final String ISSUE_REQUESTED_TOPIC_V1 = "ticket.issue.requested.v1";
    public static final String ISSUE_REQUESTED_CONSUMER_GROUP_V1 = "ticket-issue-consumer-v1";

    public static final String EVENT_TYPE_TICKET_ISSUE_REQUESTED = "TICKET_ISSUE_REQUESTED";
    public static final int EVENT_VERSION_V1 = 1;

    public static final String HEADER_EVENT_TYPE = "x-event-type";
    public static final String HEADER_EVENT_VERSION = "x-event-version";
    public static final String HEADER_REQUEST_ID = "x-request-id";
    public static final String HEADER_PRODUCED_AT = "x-produced-at";

    public static String buildIssueRequestedKey(Long eventId, Long userId) {
        return eventId + ":" + userId;
    }

    public static Map<String, String> buildIssueRequestedHeaders(String requestId, long producedAtEpochMs) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_EVENT_TYPE, EVENT_TYPE_TICKET_ISSUE_REQUESTED);
        headers.put(HEADER_EVENT_VERSION, String.valueOf(EVENT_VERSION_V1));
        headers.put(HEADER_REQUEST_ID, requestId);
        headers.put(HEADER_PRODUCED_AT, String.valueOf(producedAtEpochMs));
        return Map.copyOf(headers);
    }
}
