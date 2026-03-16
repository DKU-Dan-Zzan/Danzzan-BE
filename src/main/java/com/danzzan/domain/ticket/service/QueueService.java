package com.danzzan.domain.ticket.service;

public interface QueueService {

    /**
     * 대기열에 진입합니다 (INCR sequence score, dedup 보장).
     *
     * @return 발급된 순번(1 이상), 0이면 이미 진입된 상태
     */
    long enterQueue(String eventId, String userId);

    /**
     * 대기열 순번을 반환합니다 (1-indexed).
     * 대기열에 없으면 null.
     */
    Long getQueuePosition(String eventId, String userId);
}
