package com.danzzan.domain.festival.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 티켓팅 회차. 티켓팅을 여는 시각마다 티켓 수량과 입장할 공연 날짜가 다를 수 있어
 * 축제 설정과 별도의 줄로 관리한다.
 *
 * 티켓팅 기능 자체(대기열/발급/내 티켓)는 festival_events 테이블을 본다. 그래서 회차를
 * 저장할 때 같은 내용의 이벤트를 만들어 두고, 그 id 를 eventId 에 적어 둔다.
 * 이 연결이 있어야 회차를 고칠 때 이미 발급된 티켓을 건드리지 않고 이벤트를 따라 고친다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "festival_ticketing_round")
public class FestivalTicketingRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 티켓팅이 열리는 날짜와 시각 */
    @Column(name = "ticketing_at", nullable = false)
    private LocalDateTime ticketingAt;

    /** 이 회차에 푸는 티켓 수량 */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    /** 이 티켓으로 입장하는 공연 날짜. 축제 운영 날짜 중 하나 */
    @Column(name = "performance_date", nullable = false)
    private LocalDate performanceDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 이 회차가 만들어 낸 티켓팅 이벤트(festival_events.id). 아직 못 만들었으면 null */
    @Column(name = "event_id")
    private Long eventId;

    public static FestivalTicketingRound create(
            LocalDateTime ticketingAt,
            int capacity,
            LocalDate performanceDate,
            int displayOrder
    ) {
        FestivalTicketingRound round = new FestivalTicketingRound();
        round.ticketingAt = ticketingAt;
        round.capacity = capacity;
        round.performanceDate = performanceDate;
        round.displayOrder = displayOrder;
        return round;
    }

    public void update(LocalDateTime ticketingAt, int capacity, LocalDate performanceDate, int displayOrder) {
        this.ticketingAt = ticketingAt;
        this.capacity = capacity;
        this.performanceDate = performanceDate;
        this.displayOrder = displayOrder;
    }

    public void linkEvent(Long eventId) {
        this.eventId = eventId;
    }
}
