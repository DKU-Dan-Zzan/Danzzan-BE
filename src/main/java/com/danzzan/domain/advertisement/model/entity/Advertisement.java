package com.danzzan.domain.advertisement.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 광고 엔티티.
 * 추후 확장: 조회수(viewCount), 클릭수(clickCount), 광고 단가(unitPrice) 등은
 * 별도 AdvertisementStats 엔티티 또는 이 엔티티에 컬럼 추가로 확장 가능.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "advertisement")
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2048)
    private String imageUrl;

    @Column(length = 2048)
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdvertisementPlacement placement;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * 노출 우선순위. 숫자가 클수록 우선 노출 (선택 필드, null 가능).
     */
    private Integer priority;

    private LocalDateTime createdAt;

    @PrePersist
    void createdAt() {
        this.createdAt = LocalDateTime.now();
    }

    public static Advertisement create(
            String title,
            String imageUrl,
            String linkUrl,
            AdvertisementPlacement placement,
            LocalDate startDate,
            LocalDate endDate,
            Boolean isActive,
            Integer priority
    ) {
        Advertisement ad = new Advertisement();
        ad.setTitle(title);
        ad.setImageUrl(imageUrl);
        ad.setLinkUrl(linkUrl);
        ad.setPlacement(placement);
        ad.setStartDate(startDate);
        ad.setEndDate(endDate);
        ad.setIsActive(isActive != null ? isActive : true);
        ad.setPriority(priority);
        return ad;
    }
}
