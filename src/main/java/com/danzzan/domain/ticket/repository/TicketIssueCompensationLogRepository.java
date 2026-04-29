package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.TicketIssueCompensationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketIssueCompensationLogRepository extends JpaRepository<TicketIssueCompensationLog, Long> {

    Optional<TicketIssueCompensationLog> findByRequestId(String requestId);
}
