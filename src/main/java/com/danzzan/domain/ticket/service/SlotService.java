package com.danzzan.domain.ticket.service;

public interface SlotService {

    /**
     * 슬롯을 획득합니다.
     * maxConcurrentSlots 이하일 때만 active ZSet에 등록.
     *
     * @return 획득 성공이면 true, 슬롯이 꽉 차면 false
     */
    boolean acquireSlot(String eventId, String userId);

    /**
     * 슬롯을 반환합니다.
     * active ZSet 제거 + gate 키 삭제.
     */
    void releaseSlot(String eventId, String userId);

    /**
     * 현재 유효한 슬롯 수를 반환합니다 (만료된 슬롯 자동 정리 후).
     */
    long activeSlotCount(String eventId);
}
