package com.danzzan.domain.advertisement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "advertisement")
@Getter
@Setter
@NoArgsConstructor
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdvertisementPlacement placement;

    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * 광고 이미지의 object-position 값 (예: "50% 50%"). null이면 기본값 적용.
     */
    @Column(name = "object_position", length = 32)
    private String objectPosition;

    /** 광고 노출 종료 일시. null이면 종료일 없음. */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * 관리자 삭제(소프트 삭제). null이면 삭제되지 않은 행.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
