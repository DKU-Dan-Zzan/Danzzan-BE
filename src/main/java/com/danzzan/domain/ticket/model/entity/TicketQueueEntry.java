package com.danzzan.domain.ticket.model.entity;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.user.model.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_queue_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_queue_entry_event_user", columnNames = {"event_id", "user_id"})
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
    @Column(nullable = false)
    private QueueEntryStatus status;

    @Column
    private Long seq;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "ready_until")
    private LocalDateTime readyUntil;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public TicketQueueEntry(FestivalEvent event, User user, QueueEntryStatus status,
                            long seq, LocalDateTime enteredAt,
                            LocalDateTime readyUntil, LocalDateTime leaseUntil) {
        this.event = event;
        this.user = user;
        this.status = status;
        this.seq = seq;
        this.enteredAt = enteredAt;
        this.readyUntil = readyUntil;
        this.leaseUntil = leaseUntil;
        this.updatedAt = LocalDateTime.now();
    }

    public void markWaiting(long seq, LocalDateTime enteredAt) {
        this.status = QueueEntryStatus.WAITING;
        this.seq = seq;
        this.enteredAt = enteredAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void markReady(LocalDateTime readyUntil) {
        this.status = QueueEntryStatus.READY;
        this.readyUntil = readyUntil;
        this.updatedAt = LocalDateTime.now();
    }

    public void markActive(LocalDateTime leaseUntil) {
        this.status = QueueEntryStatus.ACTIVE;
        this.leaseUntil = leaseUntil;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDone() {
        this.status = QueueEntryStatus.DONE;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = QueueEntryStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markExpired() {
        this.status = QueueEntryStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = QueueEntryStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }
}
