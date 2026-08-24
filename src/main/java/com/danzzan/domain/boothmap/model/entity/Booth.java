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
@Table(name = "booth")
public class Booth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BoothType type;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "location_x")
    private Double locationX;

    @Column(name = "location_y")
    private Double locationY;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public Booth(String name, BoothType type, String description, String imageUrl, Double locationX, Double locationY) {
        validateLocationPair(locationX, locationY);
        this.name = name;
        this.type = type;
        this.description = description;
        this.imageUrl = imageUrl;
        this.locationX = locationX;
        this.locationY = locationY;
    }

    public void updateLocation(Double locationX, Double locationY) {
        validateLocationPair(locationX, locationY);
        this.locationX = locationX;
        this.locationY = locationY;
    }

    public void clearLocation() {
        this.locationX = null;
        this.locationY = null;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateAdminInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public boolean hasLocation() {
        return locationX != null && locationY != null;
    }

    private void validateLocationPair(Double locationX, Double locationY) {
        if ((locationX == null) != (locationY == null)) {
            throw new IllegalArgumentException("부스 좌표는 모두 비어 있거나 모두 존재해야 합니다.");
        }
    }

    /**
     * 기계번역 결과를 반영한다. 보호는 필드 단위다.
     * null 인자는 "번역하지 못했다"는 뜻이므로 기존 값을 지우지 않는다.
     */
    public void applyTranslation(String nameEn, String descriptionEn) {
        if (nameEn != null && (!this.enIsManual || this.nameEn == null)) {
            this.nameEn = nameEn;
        }
        if (descriptionEn != null && (!this.enIsManual || this.descriptionEn == null)) {
            this.descriptionEn = descriptionEn;
        }
    }

    /**
     * 관리자가 직접 입력한 번역을 반영하고 수동 플래그를 켠다.
     */
    public void applyManualTranslation(String nameEn, String descriptionEn) {
        this.nameEn = nameEn;
        this.descriptionEn = descriptionEn;
        this.enIsManual = true;
    }

    /**
     * decideEnglish가 계산한 최종 영문 값을 그대로 반영한다.
     * null은 "번역하지 못했다"가 아니라 "비우라"는 뜻이므로 그대로 비운다.
     */
    public void applyDecidedTranslation(String nameEn, String descriptionEn) {
        this.nameEn = nameEn;
        this.descriptionEn = descriptionEn;
    }
}
