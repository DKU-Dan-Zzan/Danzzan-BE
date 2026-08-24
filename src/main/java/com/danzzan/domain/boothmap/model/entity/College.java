package com.danzzan.domain.boothmap.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "college")
public class College {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location_x", nullable = false)
    private Double locationX;

    @Column(name = "location_y", nullable = false)
    private Double locationY;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public void updateLocation(Double locationX, Double locationY) {
        this.locationX = locationX;
        this.locationY = locationY;
    }

    /**
     * 기계번역 결과를 반영한다. 보호는 필드 단위다.
     * null 인자는 "번역하지 못했다"는 뜻이므로 기존 값을 지우지 않는다.
     */
    public void applyTranslation(String nameEn) {
        if (nameEn != null && (!this.enIsManual || this.nameEn == null)) {
            this.nameEn = nameEn;
        }
    }

    /**
     * 관리자가 직접 입력한 번역을 반영하고 수동 플래그를 켠다.
     */
    public void applyManualTranslation(String nameEn) {
        this.nameEn = nameEn;
        this.enIsManual = true;
    }
}