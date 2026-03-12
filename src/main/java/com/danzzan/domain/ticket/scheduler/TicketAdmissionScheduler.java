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
 * 대기열에서 여유 슬롯만큼만 입장 허가(gate 발급)하는 스케줄러.
 *
 * 매 1초마다 OPEN 상태인 이벤트에 대해:
 * 1. active ZSet에서 만료된 슬롯 정리
 * 2. 여유 슬롯(freeSlots) 계산
 * 3. 남은 재고 확인
 * 4. min(freeSlots, stock, BATCH_CEILING)만큼 대기열 앞에서 꺼냄
 * 5. acquireSlot 성공한 유저에게 gate 발급
 * 6. 슬롯 경쟁 실패 유저는 requeue
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketAdmissionScheduler {

    /** 한 tick에 최대 처리 인원 상한 */
    private static final long BATCH_CEILING = 100L;

    private final StringRedisTemplate redisTemplate;
    private final SlotService slotService;
    private final FestivalEventRepository eventRepository;

    @Value("${app.ticketing.max-concurrent-slots:100}")
    private int maxConcurrentSlots;

    @Value("${app.ticketing.gate-ttl-seconds:300}")
    private long gateTtlSeconds;

    @Scheduled(fixedDelay = 1000)
    public void admitFromQueue() {
        List<FestivalEvent> openEvents = eventRepository.findAllByTicketingStatus(TicketingStatus.OPEN);
        for (FestivalEvent event : openEvents) {
            try {
                processEventQueue(String.valueOf(event.getId()));
            } catch (Exception e) {
                log.error("admitFromQueue 오류 eventId={}", event.getId(), e);
            }
        }
    }

    private void processEventQueue(String eventId) {
        // 1. 여유 슬롯 계산
        long freeSlots = maxConcurrentSlots - slotService.activeSlotCount(eventId);
        if (freeSlots <= 0) {
            return;
        }

        // 2. 재고 확인
        String stockRaw = redisTemplate.opsForValue().get(TicketRedisKeys.stockKey(eventId));
        long stock = parseStock(stockRaw);
        if (stock <= 0) {
            return;
        }

        // 3. 입장시킬 인원 = min(여유슬롯, 남은재고, 배치상한)
        long toAdmit = Math.min(freeSlots, Math.min(stock, BATCH_CEILING));

        // 4. 대기열 앞에서 꺼냄 (popMin이 ZSet에서 삭제)
        String queueKey = TicketRedisKeys.queueKey(eventId);
        Set<TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(queueKey, toAdmit);
        if (popped == null || popped.isEmpty()) {
            return;
        }

        List<String> requeue = new ArrayList<>();

        for (TypedTuple<String> entry : popped) {
            String userId = entry.getValue();
            if (userId == null) {
                continue;
            }

            // 5. 슬롯 획득 시도
            if (!slotService.acquireSlot(eventId, userId)) {
                // 슬롯이 꽉 찼으면 다시 대기열로
                requeue.add(userId);
                continue;
            }

            // 6. gate 발급 (TTL = gateTtlSeconds)
            redisTemplate.opsForValue().set(
                    TicketRedisKeys.gateUserKey(eventId, userId),
                    "1",
                    Duration.ofSeconds(gateTtlSeconds)
            );

            log.debug("gate 발급 eventId={} userId={}", eventId, userId);
        }

        // 7. 슬롯 실패 유저 requeue (현재 시각 score → 대기열 뒤로)
        double now = System.currentTimeMillis();
        requeue.forEach(uid ->
                redisTemplate.opsForZSet().addIfAbsent(queueKey, uid, now));
    }

    private long parseStock(String stockRaw) {
        if (stockRaw == null || stockRaw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(stockRaw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
