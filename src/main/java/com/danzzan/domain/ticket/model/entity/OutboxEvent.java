package com.danzzan.domain.ticket.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_status_next_retry", columnList = "status,next_retry_at"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type,aggregate_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_aggregate", columnNames = {"aggregate_type", "aggregate_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public OutboxEvent(
            String aggregateType,
            String aggregateId,
            String topic,
            String eventKey,
            String payload,
            OutboxEventStatus status,
            int retryCount,
            LocalDateTime nextRetryAt
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
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

    public void markSent(LocalDateTime now) {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = now;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    public int increaseRetryCount() {
        this.retryCount += 1;
        return this.retryCount;
    }

    public void markRetry(LocalDateTime nextRetryAt, String lastError) {
        this.status = OutboxEventStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
        this.lastError = truncate(lastError);
    }

    public void markFailed(String lastError) {
        this.status = OutboxEventStatus.FAILED;
        this.nextRetryAt = null;
        this.lastError = truncate(lastError);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
