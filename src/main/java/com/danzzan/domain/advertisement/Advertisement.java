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

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdvertisementPlacement placement;

    @Column(nullable = false)
    private Boolean isActive = true;

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
