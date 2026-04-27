package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTicketRepository extends JpaRepository<UserTicket, Long> {
    Optional<UserTicket> findByEventIdAndUser_StudentId(Long eventId, String studentId);
    Optional<UserTicket> findByIdAndEventId(Long ticketId, Long eventId);
    long countByEventId(Long eventId);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
    boolean existsByUserIdAndEventId(Long userId, Long eventId);
    List<UserTicket> findAllByUserIdOrderByTicketingAtDesc(Long userId);

    @Query("select distinct t.user.id from UserTicket t where t.event.id = :eventId")
    List<Long> findUserIdsByEventId(@Param("eventId") Long eventId);
}
