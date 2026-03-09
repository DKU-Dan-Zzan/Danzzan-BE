package com.danzzan.domain.ticket.service;

public interface QueueService {

    /**
     * 대기열 진입. 이미 등록된 경우 무시.
     * @return true: 신규 등록, false: 이미 대기열에 있음
     */
    boolean enterQueue(String eventId, String userId);

    /**
     * 대기열 순번(0-indexed). 대기열에 없으면 null.
     */
    Long getQueuePosition(String eventId, String userId);
}
