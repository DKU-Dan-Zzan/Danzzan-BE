package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.event.service.EventOpenService;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.TicketInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 자동 오픈 스케줄러.
 *
 * 1. autoOpenDueEvents (매 1초): READY 이벤트 중 ticketingStartTime <= now인 것을 OPEN 전환
 * 2. recoverMissingStock (매 10초): OPEN인데 Redis stock 키가 없는 경우 재고 복원
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventAutoOpenScheduler {

    private final FestivalEventRepository eventRepository;
    private final EventOpenService eventOpenService;
    private final TicketInitService ticketInitService;
    private final UserTicketRepository userTicketRepository;

    @Scheduled(fixedDelay = 1000)
    public void autoOpenDueEvents() {
        List<FestivalEvent> dueEvents = eventRepository
                .findAllByTicketingStatusAndTicketingStartTimeLessThanEqual(
                        TicketingStatus.READY, LocalDateTime.now().plusSeconds(5));

        for (FestivalEvent event : dueEvents) {
            try {
                boolean opened = eventOpenService.openNow(event.getId());
                if (opened) {
                    log.info("자동 오픈 완료 eventId={} title={}", event.getId(), event.getTitle());
                }
            } catch (Exception e) {
                log.error("자동 오픈 실패 eventId={}", event.getId(), e);
            }
        }
    }

    /**
     * OPEN 이벤트인데 Redis stock 키가 없는 경우 복구합니다.
     * DB에서 이미 발급된 티켓 수를 기준으로 남은 재고를 계산한 뒤 SET NX로 복원합니다.
     * (이미 stock 키가 있으면 덮어쓰지 않음)
     */
    @Scheduled(fixedDelay = 10000)
    public void recoverMissingStock() {
        List<FestivalEvent> openEvents = eventRepository.findAllByTicketingStatus(TicketingStatus.OPEN);

        for (FestivalEvent event : openEvents) {
            try {
                long issued = userTicketRepository.countByEventId(event.getId());
                long remaining = Math.max(0, event.getTotalCapacity() - issued);
                boolean restored = ticketInitService.restoreStockIfMissing(
                        String.valueOf(event.getId()), remaining);
                if (restored) {
                    log.warn("stock 복구 완료 eventId={} remaining={}", event.getId(), remaining);
                }
            } catch (Exception e) {
                log.error("stock 복구 실패 eventId={}", event.getId(), e);
            }
        }
    }
}
