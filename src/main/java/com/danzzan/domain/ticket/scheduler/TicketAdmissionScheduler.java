package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 1초마다 대기열(ZSet)에서 여유 슬롯 수만큼 꺼내 gate 키를 발급하는 스케줄러.
 * gate 키가 발급된 유저는 AdmissionService.admit()에서 ADMITTED를 받아 Lua claim 실행.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketAdmissionScheduler {

    private static final int BATCH_CEILING = 100;

    @Value("${app.ticketing.max-concurrent-slots:100}") private int maxConcurrentSlots;
    @Value("${app.ticketing.gate-ttl-seconds:300}") private long gateTtlSeconds;

    private final StringRedisTemplate stringRedisTemplate;
    private final FestivalEventRepository eventRepository;
    private final SlotService slotService;

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        List<FestivalEvent> openEvents = eventRepository.findByTicketingStatus(TicketingStatus.OPEN);
        for (FestivalEvent event : openEvents) {
            processEventQueue(String.valueOf(event.getId()));
        }
    }

    private void processEventQueue(String eventId) {
        long freeSlots = maxConcurrentSlots - slotService.activeSlotCount(eventId);
        if (freeSlots <= 0) return;

        String stockRaw = stringRedisTemplate.opsForValue().get(TicketRedisKeys.stockKey(eventId));
        long stock = parseStock(stockRaw);
        if (stock <= 0) return;

        long toAdmit = Math.min(freeSlots, Math.min(stock, BATCH_CEILING));
        String queueKey = TicketRedisKeys.queueKey(eventId);
        Set<TypedTuple<String>> popped = stringRedisTemplate.opsForZSet().popMin(queueKey, toAdmit);
        if (popped == null || popped.isEmpty()) return;

        List<String> requeue = new ArrayList<>();
        int admitted = 0;
        for (TypedTuple<String> entry : popped) {
            String userId = entry.getValue();
            if (userId == null) continue;
            if (!slotService.acquireSlot(eventId, userId)) {
                requeue.add(userId);
                continue;
            }
            stringRedisTemplate.opsForValue().set(
                    TicketRedisKeys.gateUserKey(eventId, userId), "1",
                    Duration.ofSeconds(gateTtlSeconds));
            log.debug("admitted userId={} eventId={}", userId, eventId);
            admitted++;
        }

        // 슬롯 용량 초과로 거부된 사용자 재입대
        requeue.forEach(uid ->
                stringRedisTemplate.opsForZSet().addIfAbsent(queueKey, uid, (double) System.currentTimeMillis()));

        log.info("scheduler: admitted {} users for eventId={}", admitted, eventId);
    }

    private long parseStock(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
