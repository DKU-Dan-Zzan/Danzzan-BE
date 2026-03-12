package com.danzzan.domain.ticket.service;

public interface QueueService {

    /**
     * 대기열에 진입합니다 (중복 진입 무시).
     *
     * @return 새로 진입했으면 true, 이미 있으면 false
     */
    boolean enterQueue(String eventId, String userId);

    /**
     * 대기열 순번을 반환합니다 (1-indexed).
     * 대기열에 없으면 null.
     */
    Long getQueuePosition(String eventId, String userId);
}
