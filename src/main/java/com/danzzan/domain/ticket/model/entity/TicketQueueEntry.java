package com.danzzan.domain.ticket.model.entity;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.user.model.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_queue_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_queue_event_user", columnNames = {"event_id", "user_id"}),
        @UniqueConstraint(name = "uk_ticket_queue_event_seq", columnNames = {"event_id", "seq"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private FestivalEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private QueueEntryStatus status;

    @Column(nullable = false)
    private Long seq;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "ready_until")
    private LocalDateTime readyUntil;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Builder
    public TicketQueueEntry(
            FestivalEvent event,
            User user,
            QueueEntryStatus status,
            Long seq,
            LocalDateTime enteredAt,
            LocalDateTime readyUntil,
            LocalDateTime leaseUntil
    ) {
        this.event = event;
        this.user = user;
        this.status = status;
        this.seq = seq;
        this.enteredAt = enteredAt;
        this.readyUntil = readyUntil;
        this.leaseUntil = leaseUntil;
    }

    public void markWaiting(Long seq, LocalDateTime enteredAt) {
        this.status = QueueEntryStatus.WAITING;
        this.seq = seq;
        this.enteredAt = enteredAt;
        this.readyUntil = null;
        this.leaseUntil = null;
    }

    public void markReady(LocalDateTime readyUntil) {
        this.status = QueueEntryStatus.READY;
        this.readyUntil = readyUntil;
        this.leaseUntil = null;
    }

    public void markActive(LocalDateTime leaseUntil) {
        this.status = QueueEntryStatus.ACTIVE;
        this.leaseUntil = leaseUntil;
    }

    public void markDone() {
        this.status = QueueEntryStatus.DONE;
        this.readyUntil = null;
        this.leaseUntil = null;
    }

    public void markFailed() {
        this.status = QueueEntryStatus.FAILED;
        this.readyUntil = null;
        this.leaseUntil = null;
    }

    public void markExpired() {
        this.status = QueueEntryStatus.EXPIRED;
        this.readyUntil = null;
        this.leaseUntil = null;
    }

    public void markCancelled() {
        this.status = QueueEntryStatus.CANCELLED;
        this.readyUntil = null;
        this.leaseUntil = null;
    }
}
