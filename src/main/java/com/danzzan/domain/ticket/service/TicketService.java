package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.event.exception.EventNotFoundException;
import com.danzzan.domain.ticket.dto.*;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.domain.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketService {

    private final FestivalEventRepository eventRepository;
    private final UserTicketRepository ticketRepository;
    private final UserRepository userRepository;

    // 이벤트 목록 조회 (로그인 불필요)
    public ResponseTicketEventListDto getTicketingEvents() {
        List<FestivalEvent> events = eventRepository.findAll();

        List<ResponseTicketEventDto> items = events.stream()
                .map(this::toTicketEventDto)
                .collect(Collectors.toList());

        return new ResponseTicketEventListDto(items);
    }

    /**
     * Redis Lua claim 성공 후 DB에 티켓을 저장하고 응답을 생성합니다.
     * remaining = Lua DECR 후 남은 재고 → order = totalCapacity - remaining
     */
    @Transactional
    public ResponseReserveTicketDto persistAndBuildResponse(Long userId, Long eventId, long remaining) {
        if (ticketRepository.existsByUserIdAndEventId(userId, eventId)) {
            throw new AlreadyReservedException("이미 예매 처리가 완료되었습니다. 내 티켓에서 확인해주세요.");
        }

        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        if (event.getTicketingStatus() == TicketingStatus.READY) {
            throw new EventNotOpenException("아직 예매가 시작되지 않았습니다.");
        }
        if (event.getTicketingStatus() == TicketingStatus.CLOSED) {
            throw new EventSoldOutException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        int order = (int) (event.getTotalCapacity() - remaining);

        UserTicket ticket = UserTicket.builder()
                .user(user)
                .event(event)
                .ticketingOrder(order)
                .build();

        try {
            ticketRepository.save(ticket);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyReservedException("이미 예매 처리가 완료되었습니다. 내 티켓에서 확인해주세요.");
        }

        return new ResponseReserveTicketDto(order, toMyTicketDto(ticket, event));
    }

    public boolean hasTicket(Long userId, Long eventId) {
        return ticketRepository.existsByUserIdAndEventId(userId, eventId);
    }

    public boolean isClosedEvent(Long eventId) {
        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        return event.getTicketingStatus() == TicketingStatus.CLOSED;
    }

    // 내 티켓 목록 조회 (로그인 필요)
    public ResponseMyTicketListDto getMyTickets(Long userId) {
        List<UserTicket> tickets = ticketRepository.findAllByUserIdOrderByTicketingAtDesc(userId);

        List<ResponseMyTicketDto> items = tickets.stream()
                .map(t -> toMyTicketDto(t, t.getEvent()))
                .collect(Collectors.toList());

        return new ResponseMyTicketListDto(items);
    }

    // ===== 변환 메서드 =====

    private ResponseTicketEventDto toTicketEventDto(FestivalEvent event) {
        long ticketCount = ticketRepository.countByEventId(event.getId());
        int remaining = Math.max(0, event.getTotalCapacity() - (int) ticketCount);

        // BE status → FE status 변환
        String feStatus;
        switch (event.getTicketingStatus()) {
            case READY -> feStatus = "upcoming";
            case OPEN -> feStatus = remaining > 0 ? "open" : "soldout";
            case CLOSED -> feStatus = "soldout";
            default -> feStatus = "upcoming";
        }

        // 날짜 포맷팅
        LocalDate date = event.getEventDate();
        String[] dayOfWeekKor = {"", "월", "화", "수", "목", "금", "토", "일"};
        String formattedDate = String.format("%02d월 %02d일 (%s)",
                date.getMonthValue(), date.getDayOfMonth(),
                dayOfWeekKor[date.getDayOfWeek().getValue()]);

        String formattedTime = event.getTicketingStartTime()
                .format(DateTimeFormatter.ofPattern("HH:mm")) + " 예매 오픈";

        return ResponseTicketEventDto.builder()
                .id(String.valueOf(event.getId()))
                .title(event.getTitle())
                .eventDate(formattedDate)
                .eventTime(formattedTime)
                .ticketOpenAt(event.getTicketingStartTime().toString())
                .status(feStatus)
                .remainingCount(remaining)
                .totalCount(event.getTotalCapacity())
                .build();
    }

    private ResponseMyTicketDto toMyTicketDto(UserTicket ticket, FestivalEvent event) {
        // CONFIRMED → "issued" (팔찌 미수령), ISSUED → "used" (팔찌 수령완료)
        String feStatus = ticket.getStatus() == TicketStatus.CONFIRMED ? "issued" : "used";
        boolean wristbandIssued = ticket.getStatus() == TicketStatus.ISSUED;

        // 날짜 포맷팅
        LocalDate date = event.getEventDate();
        String[] dayOfWeekKor = {"", "월", "화", "수", "목", "금", "토", "일"};
        String formattedDate = String.format("%02d월 %02d일 (%s) 19:00",
                date.getMonthValue(), date.getDayOfMonth(),
                dayOfWeekKor[date.getDayOfWeek().getValue()]);

        // 몇 일차 계산 (첫 이벤트 기준)
        String eventName = event.getTitle();

        return ResponseMyTicketDto.builder()
                .id(String.valueOf(ticket.getId()))
                .status(feStatus)
                .eventName(eventName)
                .eventDate(formattedDate)
                .issuedAt(ticket.getTicketingAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .seat("단국존 순번 #" + ticket.getTicketingOrder())
                .queueNumber(ticket.getTicketingOrder())
                .wristbandIssued(wristbandIssued)
                .venue("단국존")
                .contact("축제 운영본부")
                .eventDescription(event.getTitle())
                .build();
    }
}
