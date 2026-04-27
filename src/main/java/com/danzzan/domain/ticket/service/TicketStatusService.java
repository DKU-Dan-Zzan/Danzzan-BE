package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;

public interface TicketStatusService {

    record QueueStatusSnapshot(
            TicketRequestStatus status,
            Long queuePosition,
            Long mySequence,
            Long aheadCount,
            Long readyUntil,
            QueueUserState admissionState
    ) {}

    QueueStatusSnapshot getQueueStatusSnapshot(String eventId, String userId);

    TicketRequestStatus getStatus(String eventId, String userId);

    Long getQueuePosition(String eventId, String userId);

    Long getMySequence(String eventId, String userId);

    Long getAheadCount(String eventId, String userId);

    Long getEstimatedWaitSeconds(Long aheadCount);

    Long getReadyUntil(String eventId, String userId);

    QueueUserState getAdmissionState(String eventId, String userId);

    void setProcessing(String eventId, String userId, String requestId, long acceptedAt, long ttlSeconds);

    String getProcessingRequestId(String eventId, String userId);

    Long getProcessingAcceptedAt(String eventId, String userId);

    void clearProcessing(String eventId, String userId);

    void setFailed(String eventId, String userId, long ttlSeconds);
}
