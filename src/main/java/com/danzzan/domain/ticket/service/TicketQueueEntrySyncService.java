package com.danzzan.domain.ticket.service;

public interface TicketQueueEntrySyncService {

    void markWaiting(String eventId, String userId, long seq, long enteredAtMs);

    void markReady(String eventId, String userId, long readyUntilMs);

    void markActive(String eventId, String userId, long leaseUntilMs);

    void markDone(String eventId, String userId);

    void markFailed(String eventId, String userId);

    void markExpired(String eventId, String userId);

    void markCancelled(String eventId, String userId);
}
