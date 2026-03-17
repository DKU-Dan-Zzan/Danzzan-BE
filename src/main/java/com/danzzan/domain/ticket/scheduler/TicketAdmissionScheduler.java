package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.service.QueueStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대기열 선두 유저를 READY로 승격하는 스케줄러.
 *
 * capacity = READY 수 + ACTIVE 수 기준으로 여유 슬롯 계산.
 * 매 1초마다 OPEN 이벤트에 대해:
 * 1. 만료된 READY 유저 정리
 * 2. 여유 슬롯(capacity - READY - ACTIVE) 계산
 * 3. 재고 확인
 * 4. min(여유슬롯, 재고, 배치상한)만큼 queue 앞에서 꺼내 READY 승격
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketAdmissionScheduler {

    private static final long BATCH_CEILING = 100L;

    private final QueueStateService queueStateService;
    private final FestivalEventRepository eventRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.gate-ttl-seconds:180}")
    private long readyTtlSeconds;

    @Scheduled(fixedDelay = 1000)
    public void admitFromQueue() {
        List<FestivalEvent> openEvents = eventRepository.findAllByTicketingStatus(TicketingStatus.OPEN);
        for (FestivalEvent event : openEvents) {
            try {
                String eventId = String.valueOf(event.getId());
                redisTemplate.delete(TicketRedisKeys.closedCleanupKey(eventId));
                processEventQueue(eventId);
            } catch (Exception e) {
                log.error("admitFromQueue 오류 eventId={}", event.getId(), e);
            }
        }

        List<FestivalEvent> closedEvents = eventRepository.findAllByTicketingStatus(TicketingStatus.CLOSED);
        for (FestivalEvent event : closedEvents) {
            try {
                String eventId = String.valueOf(event.getId());
                String cleanupKey = TicketRedisKeys.closedCleanupKey(eventId);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(cleanupKey))) {
                    continue;
                }
                int cancelled = queueStateService.cancelWaitingQueue(eventId);
                redisTemplate.opsForValue().set(cleanupKey, "1");
                if (cancelled > 0) {
                    log.info("CLOSED 이벤트 대기열 정리 eventId={} cancelled={}", eventId, cancelled);
                }
            } catch (Exception e) {
                log.error("closedEvent 대기열 정리 오류 eventId={}", event.getId(), e);
            }
        }
    }

    private void processEventQueue(String eventId) {
        // 1. 만료된 READY/ACTIVE 유저 정리
        int expiredReady = queueStateService.expireReadyUsers(eventId);
        if (expiredReady > 0) {
            log.debug("READY 만료 정리 eventId={} count={}", eventId, expiredReady);
        }
        int expiredActive = queueStateService.expireActiveUsers(eventId);
        if (expiredActive > 0) {
            log.debug("ACTIVE 만료 정리 eventId={} count={}", eventId, expiredActive);
        }

        long readyUntilMs = System.currentTimeMillis() + readyTtlSeconds * 1000L;
        for (int i = 0; i < BATCH_CEILING; i++) {
            boolean admitted = queueStateService.admitNextWaitingUser(eventId, readyUntilMs, maxConcurrent);
            if (!admitted) {
                return;
            }
        }
    }
}
