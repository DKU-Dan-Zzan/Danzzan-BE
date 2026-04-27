package com.danzzan.domain.ticket.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ticket_issue_compensation_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_comp_request_id", columnNames = "request_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketIssueCompensationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rollback_result", nullable = false, length = 30)
    private String rollbackResult;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "error_reason", length = 500)
    private String errorReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TicketIssueCompensationLog(
            String requestId,
            Long eventId,
            Long userId,
            String rollbackResult,
            int attempt,
            String errorReason
    ) {
        this.requestId = requestId;
        this.eventId = eventId;
        this.userId = userId;
        this.rollbackResult = rollbackResult;
        this.attempt = attempt;
        this.errorReason = errorReason;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateResult(String rollbackResult, int attempt, String errorReason) {
        this.rollbackResult = rollbackResult;
        this.attempt = attempt;
        this.errorReason = errorReason;
    }
}
