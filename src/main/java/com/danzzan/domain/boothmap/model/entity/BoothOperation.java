package com.danzzan.domain.boothmap.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "booth_operation",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_booth_operation_booth_date", columnNames = {"booth_id", "operation_date"})
    }
)
public class BoothOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    private Booth booth;

    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false)
    private BoothOperationStatus operationStatus;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    public BoothOperation(Booth booth, LocalDate operationDate, BoothOperationStatus operationStatus, LocalTime startTime, LocalTime endTime) {
        this.booth = booth;
        this.operationDate = operationDate;
        this.operationStatus = operationStatus;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void updateOperation(BoothOperationStatus operationStatus, LocalTime startTime, LocalTime endTime) {
        this.operationStatus = operationStatus;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
