package com.danzzan.domain.timetable.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "performance")
public class Performance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "performance_date", nullable = false)
    private LocalDate performanceDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(name = "stage")
    private String stage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static Performance create(
            LocalDate performanceDate,
            LocalTime startTime,
            LocalTime endTime,
            Artist artist,
            String stage
    ) {
        Performance performance = new Performance();
        performance.performanceDate = performanceDate;
        performance.startTime = startTime;
        performance.endTime = endTime;
        performance.artist = artist;
        performance.stage = stage;
        return performance;
    }

    public void update(
            LocalDate performanceDate,
            LocalTime startTime,
            LocalTime endTime,
            Artist artist,
            String stage
    ) {
        if (performanceDate != null) {
            this.performanceDate = performanceDate;
        }
        if (startTime != null) {
            this.startTime = startTime;
        }
        if (endTime != null) {
            this.endTime = endTime;
        }
        if (artist != null) {
            this.artist = artist;
        }
        this.stage = stage;
    }
}