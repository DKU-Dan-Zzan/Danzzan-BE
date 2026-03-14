package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.TicketQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketQueueEntryRepository extends JpaRepository<TicketQueueEntry, Long> {

    Optional<TicketQueueEntry> findByEventIdAndUserId(Long eventId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TicketQueueEntry t where t.event.id = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);
}
