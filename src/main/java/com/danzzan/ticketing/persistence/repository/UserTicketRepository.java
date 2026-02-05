package com.danzzan.ticketing.persistence.repository;

import com.danzzan.ticketing.persistence.entity.TicketStatus;
import com.danzzan.ticketing.persistence.entity.UserTicket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTicketRepository extends JpaRepository<UserTicket, Long> {
    Optional<UserTicket> findByEventIdAndUser_StudentId(Long eventId, String studentId);
    Optional<UserTicket> findByIdAndEventId(Long ticketId, Long eventId);
    long countByEventId(Long eventId);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
}
