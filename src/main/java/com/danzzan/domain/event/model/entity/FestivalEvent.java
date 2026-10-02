package com.danzzan.domain.event.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "festival_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FestivalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "ticketing_start_time", nullable = false)
    private LocalDateTime ticketingStartTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticketing_status", nullable = false)
    private TicketingStatus ticketingStatus;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @Builder
    public FestivalEvent(String title, LocalDate eventDate, LocalDateTime ticketingStartTime,
                         TicketingStatus ticketingStatus, Integer totalCapacity) {
        this.title = title;
        this.eventDate = eventDate;
        this.ticketingStartTime = ticketingStartTime;
        this.ticketingStatus = ticketingStatus != null ? ticketingStatus : TicketingStatus.READY;
        this.totalCapacity = totalCapacity;
    }

    /** 축제 이름/일차 표시만 변경한다. 오픈 상태·재고·시각은 유지한다. */
    public void rename(String title) {
        this.title = title;
    }

    /**
     * 관리자 축제 설정에서 회차를 고쳤을 때 이벤트를 따라 고친다.
     *
     * 오픈 전(READY)일 때만 부른다. 이미 열렸거나 마감된 이벤트를 고치면 대기열과
     * 발급된 티켓의 전제가 흔들린다.
     */
    public void updateBeforeOpen(String title, LocalDate eventDate, LocalDateTime ticketingStartTime,
                                 Integer totalCapacity) {
        if (this.ticketingStatus != TicketingStatus.READY) {
            throw new IllegalStateException("이미 오픈했거나 마감한 티켓팅은 수정할 수 없습니다.");
        }
        this.title = title;
        this.eventDate = eventDate;
        this.ticketingStartTime = ticketingStartTime;
        this.totalCapacity = totalCapacity;
    }

}
