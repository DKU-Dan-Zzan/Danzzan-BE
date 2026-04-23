package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.metrics.TicketingMetrics;
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
 * 대기열 선두 유저를 ACTIVE로 직행 승격하는 스케줄러.
 *
 * capacity = READY 수(레거시) + ACTIVE 수 기준으로 여유 슬롯 계산.
 * 매 1초마다 OPEN 이벤트에 대해:
 * 1. 만료된 READY 유저 정리(레거시 데이터 청소)
 * 2. 여유 슬롯(capacity - READY - ACTIVE) 계산
 * 3. 재고 확인
 * 4. min(여유슬롯, 재고, 배치상한)만큼 queue 앞에서 꺼내 ACTIVE 승격
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketAdmissionScheduler {

    private final QueueStateService queueStateService;
    private final FestivalEventRepository eventRepository;
    private final StringRedisTemplate redisTemplate;
    private final TicketingMetrics ticketingMetrics;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrent;

    @Value("${app.ticketing.direct-admission.active-ttl-seconds:${app.ticketing.active-ttl-seconds:600}}")
    private long directAdmissionActiveTtlSeconds;

    @Value("${app.ticketing.admission.batch-ceiling:100}")
    private int batchCeiling;

    @Value("${app.ticketing.admission.max-batch-per-tick:200}")
    private int maxBatchPerTick;

    @Scheduled(fixedDelayString = "${app.ticketing.admission.fixed-delay-ms:1000}")
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

        // 2. 대기열 깊이 & 잔여 재고 Gauge 업데이트
        Long queueDepth = redisTemplate.opsForZSet().zCard(TicketRedisKeys.queueKey(eventId));
        ticketingMetrics.updateQueueDepth(eventId, queueDepth != null ? queueDepth : 0L);

        String stockStr = redisTemplate.opsForValue().get(TicketRedisKeys.stockKey(eventId));
        if (stockStr != null) {
            try {
                ticketingMetrics.updateStock(eventId, Long.parseLong(stockStr));
            } catch (NumberFormatException ignored) {}
        }

        // 3. WAITING → ACTIVE 직행 승격
        long activeUntilMs = System.currentTimeMillis() + directAdmissionActiveTtlSeconds * 1000L;
        int admittedCount = queueStateService.admitWaitingUsers(
                eventId,
                activeUntilMs,
                maxConcurrent,
                resolveAdmissionBatchLimit()
        );
        for (int i = 0; i < admittedCount; i++) {
            ticketingMetrics.incrementAdmission(eventId);
        }
    }

    private int resolveAdmissionBatchLimit() {
        int configured = Math.max(1, batchCeiling);
        int hardCap = Math.max(1, maxBatchPerTick);
        return Math.min(configured, hardCap);
    }
}
