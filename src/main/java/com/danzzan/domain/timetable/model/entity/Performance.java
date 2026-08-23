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

    @Column(name = "stage_en")
    private String stageEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

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

    /**
     * 기계번역 결과를 반영한다. 보호는 필드 단위다.
     * null 인자는 "번역하지 못했다"는 뜻이므로 기존 값을 지우지 않는다.
     */
    public void applyTranslation(String stageEn) {
        if (stageEn != null && (!this.enIsManual || this.stageEn == null)) {
            this.stageEn = stageEn;
        }
    }

    /**
     * 관리자가 직접 입력한 번역을 반영하고 수동 플래그를 켠다.
     */
    public void applyManualTranslation(String stageEn) {
        this.stageEn = stageEn;
        this.enIsManual = true;
    }

    /**
     * decideEnglish가 계산한 최종 영문 값을 그대로 반영한다.
     * null은 "번역하지 못했다"가 아니라 "비우라"는 뜻이므로 그대로 비운다.
     */
    public void applyDecidedTranslation(String stageEn) {
        this.stageEn = stageEn;
    }
}