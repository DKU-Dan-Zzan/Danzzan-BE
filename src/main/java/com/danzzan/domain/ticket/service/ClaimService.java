package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.service.model.ClaimResult;

public interface ClaimService {

    ClaimResult claim(String eventId, String userId);

    /**
     * DB 저장 실패 시 Redis 재고·상태를 원복합니다.
     */
    boolean rollback(String eventId, String userId);
}
