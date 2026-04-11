package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketIssueRequestRepository extends JpaRepository<TicketIssueRequest, Long> {

    Optional<TicketIssueRequest> findByRequestId(String requestId);

    Optional<TicketIssueRequest> findByEventIdAndUserId(Long eventId, Long userId);

    Optional<TicketIssueRequest> findByEventIdAndUserIdAndStatus(Long eventId, Long userId, TicketIssueRequestStatus status);
}
