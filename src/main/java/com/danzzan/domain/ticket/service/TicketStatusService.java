package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.model.QueueStatusSnapshot;

public interface TicketStatusService {

    /**
     * Redis Pipeline으로 단일 RTT에 유저 대기열 상태 전체를 수집합니다.
     * status 폴링과 queue/enter 응답 빌드에 사용하세요.
     */
    QueueStatusSnapshot fetchSnapshot(String eventId, String userId);

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
