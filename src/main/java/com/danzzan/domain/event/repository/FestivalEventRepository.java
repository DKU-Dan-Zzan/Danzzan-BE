package com.danzzan.domain.event.repository;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FestivalEventRepository extends JpaRepository<FestivalEvent, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM FestivalEvent e WHERE e.id = :id")
    java.util.Optional<FestivalEvent> findByIdForUpdate(@Param("id") Long id);

    /** 관리자 설정에 연결된 회차만 공개/팔찌 배부 목록에 표시한다. */
    @Query("SELECT e FROM FestivalEvent e WHERE e.id IN " +
            "(SELECT r.eventId FROM FestivalTicketingRound r) " +
            "ORDER BY e.eventDate, e.ticketingStartTime, e.id")
    List<FestivalEvent> findConfiguredEvents();

    List<FestivalEvent> findAllByTicketingStatus(TicketingStatus status);

    List<FestivalEvent> findAllByTicketingStatusAndTicketingStartTimeLessThanEqual(
            TicketingStatus status, LocalDateTime threshold);

    /**
     * READY 상태인 경우에만 OPEN으로 전환합니다. (조건부 업데이트)
     * 반환값: 변경된 row 수 (1 = 이번에 최초 전환, 0 = 이미 OPEN/CLOSED)
     */
    @Modifying
    @Query("UPDATE FestivalEvent e SET e.ticketingStatus = com.danzzan.domain.event.model.entity.TicketingStatus.OPEN " +
           "WHERE e.id = :id AND e.ticketingStatus = com.danzzan.domain.event.model.entity.TicketingStatus.READY")
    int openIfReady(@Param("id") Long id);
}
