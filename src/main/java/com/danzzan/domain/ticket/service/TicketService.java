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
    private static final List<TicketStatus> CONSUMED_TICKET_STATUSES = List.of(
            TicketStatus.CONFIRMED,
            TicketStatus.ISSUED,
            TicketStatus.CANCELLED_WITHDRAWAL
    );

    private final FestivalEventRepository eventRepository;
    private final UserTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    // 이벤트 목록 조회 (로그인 불필요)
    public ResponseTicketEventListDto getTicketingEvents() {
        List<FestivalEvent> events = eventRepository.findConfiguredEvents();

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
        if (ticketRepository.existsByUserIdAndEventIdAndStatusIn(userId, eventId, CONSUMED_TICKET_STATUSES)) {
            throw new AlreadyReservedException("이미 예매 처리가 완료되었습니다. 내 티켓에서 확인해주세요.");
        }

        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        if (event.getTicketingStatus() == TicketingStatus.CLOSED) {
            throw new EventSoldOutException();
        }

        User user = userRepository.findActiveByIdForUpdate(userId)
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
        String userKey = TicketRedisKeys.userKey(String.valueOf(eventId), String.valueOf(userId));
        return Boolean.TRUE.equals(redisTemplate.hasKey(userKey));
    }

    /**
     * queue_enter 경로 최적화를 위해 user 티켓 보유 여부와 event 상태를 Redis multiGet 1회로 조회한다.
     */
    public QueueEnterEligibility getQueueEnterEligibility(Long userId, Long eventId) {
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);
        List<String> values = redisTemplate.opsForValue().multiGet(List.of(
                TicketRedisKeys.userKey(eventIdStr, userIdStr),
                TicketRedisKeys.eventStatusKey(eventIdStr)
        ));
        String userTicketValue = values != null && values.size() > 0 ? values.get(0) : null;
        String eventStatusValue = values != null && values.size() > 1 ? values.get(1) : null;
        return new QueueEnterEligibility(userTicketValue != null, parseStatus(eventStatusValue));
    }

    public TicketingStatus getTicketingStatusFromCache(Long eventId) {
        String eventIdStr = String.valueOf(eventId);
        return parseStatus(redisTemplate.opsForValue().get(TicketRedisKeys.eventStatusKey(eventIdStr)));
    }

    public TicketingStatus getTicketingStatus(Long eventId) {
        String eventIdStr = String.valueOf(eventId);
        TicketingStatus cached = parseStatus(redisTemplate.opsForValue().get(TicketRedisKeys.eventStatusKey(eventIdStr)));
        if (cached != null) {
            return cached;
        }

        TicketingStatus status = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new)
                .getTicketingStatus();
        redisTemplate.opsForValue().set(TicketRedisKeys.eventStatusKey(eventIdStr), status.name());
        return status;
    }

    private TicketingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TicketingStatus.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record QueueEnterEligibility(boolean hasTicket, TicketingStatus ticketingStatus) {
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
        TicketingStatus status = event.getTicketingStatus();
        int remaining;
        if (status == TicketingStatus.CLOSED) {
            // CLOSED는 stock 키 유무와 무관하게 항상 0
            remaining = 0;
        } else if (status == TicketingStatus.READY) {
            // 아직 오픈 전이라 빠져나간 티켓이 없다. 재고 키는 오픈 시점에 새로 세팅되므로
            // 오픈 전에는 보지 않는다. (같은 id 를 다시 쓴 이벤트가 예전 값을 물려받는 것도 막는다)
            remaining = event.getTotalCapacity();
        } else if (stockStr != null) {
            try {
                remaining = Math.max(0, Integer.parseInt(stockStr));
            } catch (NumberFormatException e) {
                remaining = 0;
            }
        } else {
            // stock 키 없음: OPEN 이면 예매 불가 상태(0)
            remaining = 0;
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
        String feStatus = switch (ticket.getStatus()) {
            case CONFIRMED -> "issued";
            case ISSUED -> "used";
            case CANCELLED_WITHDRAWAL -> "cancelled";
        };
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
