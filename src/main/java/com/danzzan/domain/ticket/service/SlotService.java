package com.danzzan.domain.ticket.service;

public interface SlotService {
    boolean acquireSlot(String eventId, String userId);
    void releaseSlot(String eventId, String userId);
    long activeSlotCount(String eventId);
}
