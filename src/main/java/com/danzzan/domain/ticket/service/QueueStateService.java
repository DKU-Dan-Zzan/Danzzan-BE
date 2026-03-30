package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.QueueUserState;

public interface QueueStateService {

    /**
     * WAITING 선두 1명을 READY로 원자 승격합니다.
     * @return true면 승격 성공, false면 승격할 사용자가 없거나 슬롯/재고가 없음
     */
    boolean admitNextWaitingUser(String eventId, long readyUntilMs, int maxConcurrent);

    /**
     * Lua로 READY → ACTIVE 원자 전환.
     * @return 1=READY→ACTIVE 성공, 2=이미 ACTIVE(lease 연장), 0=READY/ACTIVE 아님, -1=READY 만료, -2=ACTIVE 만료
     */
    long activateIfReady(String eventId, String userId);

    /** 예매 완료 → DONE, active Set 제거 */
    void markDone(String eventId, String userId);

    /** active Set 제거 (finally 블록용) */
    void releaseActive(String eventId, String userId);

    /** READY 만료 처리 (scheduler 호출) — 만료된 READY 유저 수 반환 */
    int expireReadyUsers(String eventId);

    /** ACTIVE 만료 처리 (scheduler 호출) — 탭 이탈 등으로 슬롯 미반환 시 강제 정리 */
    int expireActiveUsers(String eventId);

    /** 이벤트 마감 시 queue/ready/active에 남아있는 사용자를 CANCELLED 처리 — 처리된 유저 수 반환 */
    int cancelWaitingQueue(String eventId);

    /** 사용자 자발적 이탈 — WAITING/READY/ACTIVE 상태를 CANCELLED로 전환하고 슬롯 반환 */
    void leaveQueue(String eventId, String userId);

    QueueUserState getState(String eventId, String userId);

    long readyCount(String eventId);

    long activeCount(String eventId);
}
