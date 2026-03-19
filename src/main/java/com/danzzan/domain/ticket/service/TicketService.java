package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.event.exception.EventNotFoundException;
import com.danzzan.domain.ticket.dto.*;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.domain.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final String[] DAY_OF_WEEK_KOR = {"", "월", "화", "수", "목", "금", "토", "일"};

    private final FestivalEventRepository eventRepository;
    private final UserTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

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

        if (event.getTicketingStatus() == TicketingStatus.CLOSED) {
            throw new EventSoldOutException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Lua DECR 후 반환된 remaining을 사용해 순번 계산 (원자적, 동시성 안전)
        // remaining = 차감 후 남은 재고 → 순번 = totalCapacity - remaining
        int order = (int) (event.getTotalCapacity() - remaining);

        // 대기열 진입 순번(seq) — Redis hash에서 읽어 저장
        Long seq = readSeqFromRedis(String.valueOf(eventId), String.valueOf(userId));

        UserTicket ticket = UserTicket.builder()
                .user(user)
                .event(event)
                .ticketingOrder(order)
                .seq(seq)
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

    public TicketingStatus getTicketingStatus(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new)
                .getTicketingStatus();
    }

    // 내 티켓 목록 조회 (로그인 필요)
    public ResponseMyTicketListDto getMyTickets(Long userId) {
        List<UserTicket> tickets = ticketRepository.findAllByUserIdOrderByTicketingAtDesc(userId);

        List<ResponseMyTicketDto> items = tickets.stream()
                .map(t -> toMyTicketDto(t, t.getEvent()))
                .collect(Collectors.toList());

        return new ResponseMyTicketListDto(items);
    }

    private Long readSeqFromRedis(String eventId, String userId) {
        Object raw = redisTemplate.opsForHash().get(
                TicketRedisKeys.queueUserHashKey(eventId, userId), "seq");
        if (raw == null) return null;
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===== 변환 메서드 =====

    private ResponseTicketEventDto toTicketEventDto(FestivalEvent event) {
        // Redis stock을 진실원천으로 사용 — DB 발급 수와 totalCapacity 기반 계산은 초기화 값과 어긋남
        String stockStr = redisTemplate.opsForValue().get(
                TicketRedisKeys.stockKey(String.valueOf(event.getId())));
        int remaining;
        if (stockStr != null) {
            try {
                remaining = Math.max(0, Integer.parseInt(stockStr));
            } catch (NumberFormatException e) {
                remaining = 0;
            }
        } else {
            // OPEN인데 stock 키 없음 = initStock 미호출 → 실제로 예매 불가 상태
            // admit/claim Lua 모두 stock nil이면 거부하므로 0으로 표시해 FE와 일치시킴
            // READY이면 아직 초기화 전 정상 상태 → totalCapacity로 예고 표시
            remaining = event.getTicketingStatus() == TicketingStatus.OPEN
                    ? 0 : event.getTotalCapacity();
        }

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
        String formattedDate = String.format("%02d월 %02d일 (%s)",
                date.getMonthValue(), date.getDayOfMonth(),
                DAY_OF_WEEK_KOR[date.getDayOfWeek().getValue()]);

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
        String formattedDate = String.format("%02d월 %02d일 (%s) 19:00",
                date.getMonthValue(), date.getDayOfMonth(),
                DAY_OF_WEEK_KOR[date.getDayOfWeek().getValue()]);

        return ResponseMyTicketDto.builder()
                .id(String.valueOf(ticket.getId()))
                .status(feStatus)
                .eventName(event.getTitle())
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
