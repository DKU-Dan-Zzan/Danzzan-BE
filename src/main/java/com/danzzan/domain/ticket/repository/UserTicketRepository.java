package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTicketRepository extends JpaRepository<UserTicket, Long> {
    Optional<UserTicket> findByEventIdAndUser_StudentId(Long eventId, String studentId);
    Optional<UserTicket> findByIdAndEventId(Long ticketId, Long eventId);
    long countByEventId(Long eventId);

    /** 회차를 삭제할 때 그 회차로 나간 티켓을 함께 지운다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTicket t where t.event.id = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
    long countByEventIdAndStatusIn(Long eventId, Collection<TicketStatus> statuses);
    boolean existsByUserIdAndEventId(Long userId, Long eventId);
    boolean existsByUserIdAndEventIdAndStatusIn(Long userId, Long eventId, Collection<TicketStatus> statuses);
    List<UserTicket> findAllByUserIdOrderByTicketingAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserTicket t where t.user.id = :userId and t.status = :status")
    List<UserTicket> findAllByUserIdAndStatusForUpdate(
            @Param("userId") Long userId,
            @Param("status") TicketStatus status
    );

    @Query("select distinct t.user.id from UserTicket t where t.event.id = :eventId")
    List<Long> findUserIdsByEventId(@Param("eventId") Long eventId);

    @Query("select distinct t.user.id from UserTicket t where t.event.id = :eventId and t.status in :statuses")
    List<Long> findUserIdsByEventIdAndStatusIn(
            @Param("eventId") Long eventId,
            @Param("statuses") Collection<TicketStatus> statuses
    );
}
