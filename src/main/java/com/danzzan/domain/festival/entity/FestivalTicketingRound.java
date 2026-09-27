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
 * 티켓팅 기능 자체(대기열/발급)는 festival_events 테이블을 쓴다. 지금은 티켓팅이 꺼져
 * 있어 두 테이블을 잇지 않았고, 티켓팅을 되살릴 때 이 회차를 festival_events 로
 * 옮겨 심는 작업이 필요하다.
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
}
