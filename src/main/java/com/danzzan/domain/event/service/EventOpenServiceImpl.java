package com.danzzan.domain.event.service;

import com.danzzan.domain.event.exception.EventNotFoundException;
import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.TicketInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventOpenServiceImpl implements EventOpenService {

    private static final List<TicketStatus> REDIS_ISSUED_USER_STATUSES = List.of(
            TicketStatus.CONFIRMED,
            TicketStatus.ISSUED
    );

    private final FestivalEventRepository eventRepository;
    private final TicketInitService ticketInitService;
    private final UserTicketRepository userTicketRepository;

    /**
     * 조건부 DB 업데이트 (WHERE ticketing_status = 'READY')로 멱등성을 보장합니다.
     * 스케줄러/admin API 동시 호출이 들어와도 딱 한 번만 OPEN 전환 + initStock이 실행됩니다.
     */
    @Override
    @Transactional
    public boolean openNow(Long eventId) {
        FestivalEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(EventNotFoundException::new);

        // 조건부 업데이트: READY인 경우에만 OPEN으로 전환 (동시 호출 안전)
        int updated = eventRepository.openIfReady(eventId);
        if (updated == 0) {
            log.debug("openNow skipped (이미 OPEN/CLOSED) eventId={} status={}", eventId, event.getTicketingStatus());
            return false;
        }

        // DB 전환 성공한 경우에만 Redis 초기화
        ticketInitService.initStock(String.valueOf(eventId), (long) event.getTotalCapacity());
        ticketInitService.setEventStatus(String.valueOf(eventId), TicketingStatus.OPEN);
        List<Long> issuedUserIds = userTicketRepository.findUserIdsByEventIdAndStatusIn(
                eventId,
                REDIS_ISSUED_USER_STATUSES
        );
        long synced = ticketInitService.syncIssuedUsers(String.valueOf(eventId), issuedUserIds);
        log.info(
                "이벤트 OPEN 전환 완료 eventId={} stock={} syncedUsers={}",
                eventId,
                event.getTotalCapacity(),
                synced
        );
        return true;
    }
}
