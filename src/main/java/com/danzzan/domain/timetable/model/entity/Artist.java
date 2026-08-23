package com.danzzan.domain.timetable.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "artist")
public class Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static Artist create(String name, String description, String imageUrl) {
        Artist artist = new Artist();
        artist.name = name;
        artist.description = description;
        artist.imageUrl = imageUrl;
        return artist;
    }

    public void updateProfile(String name, String description) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
    }

    public void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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