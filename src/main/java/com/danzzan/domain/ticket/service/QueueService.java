package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;

public interface QueueService {

    /**
     * 대기열에 진입하고 상태 스냅샷을 반환합니다.
     *
     * @return 상태 + 대기열 순번 스냅샷
     */
    QueueEnterSnapshot enterQueue(String eventId, String userId);

    /**
     * 대기열 순번을 반환합니다 (1-indexed).
     * 대기열에 없으면 null.
     */
    Long getQueuePosition(String eventId, String userId);

    record QueueEnterSnapshot(
            TicketRequestStatus status,
            Long queuePosition,
            String requestId,
            Long acceptedAt
    ) {
    }
}
