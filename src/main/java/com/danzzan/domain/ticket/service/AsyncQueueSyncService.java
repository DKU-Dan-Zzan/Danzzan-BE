package com.danzzan.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncQueueSyncService {

    private final TicketQueueEntrySyncService delegate;

    @Async("queueSyncExecutor")
    public void markWaitingAsync(String eventId, String userId, long seq, long enteredAtMs) {
        try {
            delegate.markWaiting(eventId, userId, seq, enteredAtMs);
        } catch (Exception e) {
            log.warn("markWaiting async 실패 eventId={} userId={} seq={} — markReady 시점에 복구됨",
                    eventId, userId, seq, e);
        }
    }
}
