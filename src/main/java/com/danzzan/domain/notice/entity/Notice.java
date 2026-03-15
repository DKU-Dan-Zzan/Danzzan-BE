package com.danzzan.domain.notice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notice")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private String author;

    /**
     * 카테고리 (예: GENERAL, EVENT 등).
     * 카테고리 목록이 확정되지 않은 상태를 고려해 String으로 유지합니다.
     */
    @Column(length = 50)
    private String category = "GENERAL";

    /** 상단 고정 여부 */
    private Boolean isPinned = false;

    /** 대표 이미지 URL (선택) */
    @Column(length = 2048)
    private String thumbnailImageUrl;

    /**
     * 동일 카테고리 내에서의 표시 순서.
     * 값이 작을수록 위에 노출되며, isPinned=true 그룹과 조합해 정렬에 사용됩니다.
     * 기본값 0으로 두고, 필요 시 관리자 페이지에서 드래그 앤 드롭으로 재정렬합니다.
     */
    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean isEmergency = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void createdAt() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void updatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public static Notice create(String title, String content, String author, Boolean isEmergency) {
        return create(title, content, author, null, false, null, isEmergency);
    }

    public static Notice create(
            String title,
            String content,
            String author,
            String category,
            Boolean isPinned,
            String thumbnailImageUrl,
            Boolean isEmergency
    ) {
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAuthor(author);
        notice.setCategory((category == null || category.isBlank()) ? "GENERAL" : category.trim());
        notice.setIsPinned(Boolean.TRUE.equals(isPinned));
        notice.setThumbnailImageUrl(thumbnailImageUrl);
        notice.setIsEmergency(isEmergency != null && isEmergency);
        return notice;
    }
}
