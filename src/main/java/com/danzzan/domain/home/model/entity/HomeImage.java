package com.danzzan.domain.home.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "home_image")
public class HomeImage {
    public HomeImage(String imageUrl, Long festivalSettingId, Integer displayOrder) {
        this.imageUrl = imageUrl;
        this.festivalSettingId = festivalSettingId;
        this.displayOrder = displayOrder;
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "festival_setting_id", nullable = false)
    private Long festivalSettingId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
