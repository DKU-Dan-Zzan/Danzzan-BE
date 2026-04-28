package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketIssueRequestRepository extends JpaRepository<TicketIssueRequest, Long> {

    Optional<TicketIssueRequest> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketIssueRequest t where t.requestId = :requestId")
    Optional<TicketIssueRequest> findByRequestIdForUpdate(@Param("requestId") String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketIssueRequest t where t.userId = :userId and t.status = :status")
    List<TicketIssueRequest> findAllByUserIdAndStatusForUpdate(
            @Param("userId") Long userId,
            @Param("status") TicketIssueRequestStatus status
    );

    Optional<TicketIssueRequest> findByEventIdAndUserId(Long eventId, Long userId);

    Optional<TicketIssueRequest> findByEventIdAndUserIdAndStatus(Long eventId, Long userId, TicketIssueRequestStatus status);

    Optional<TicketIssueRequest> findByRequestIdAndEventIdAndUserId(String requestId, Long eventId, Long userId);

    @Query(value = """
            SELECT *
            FROM ticket_issue_requests
            WHERE compensation_pending = true
              AND compensation_attempts < :maxAttempts
            ORDER BY updated_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<TicketIssueRequest> findPendingCompensationBatch(@Param("batchSize") int batchSize, @Param("maxAttempts") int maxAttempts);

    long countByCompensationPendingTrue();
}
