package com.danzzan.domain.notice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
     * 공지 본문에 첨부된 이미지 URL 목록.
     * 최대 10개까지를 권장하며, 썸네일은 이 목록 중 하나를 사용합니다.
     */
    @ElementCollection
    @CollectionTable(name = "notice_image", joinColumns = @JoinColumn(name = "notice_id"))
    @Column(name = "image_url", length = 2048, nullable = false)
    @OrderColumn(name = "position")
    private List<String> images = new ArrayList<>();

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

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

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
        return create(title, content, author, null, false, null, null, isEmergency);
    }

    public static Notice create(
            String title,
            String content,
            String author,
            String category,
            Boolean isPinned,
            String thumbnailImageUrl,
            java.util.List<String> images,
            Boolean isEmergency
    ) {
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAuthor(author);
        notice.setCategory((category == null || category.isBlank()) ? "GENERAL" : category.trim());
        notice.setIsPinned(Boolean.TRUE.equals(isPinned));

        // 썸네일 정책: 명시된 썸네일이 없고 이미지가 있으면 첫 번째 이미지를 썸네일로 사용
        if ((thumbnailImageUrl == null || thumbnailImageUrl.isBlank())
                && images != null
                && !images.isEmpty()) {
            notice.setThumbnailImageUrl(images.get(0));
        } else {
            notice.setThumbnailImageUrl(thumbnailImageUrl);
        }

        if (images != null) {
            notice.getImages().clear();
            notice.getImages().addAll(images);
        }

        notice.setIsEmergency(isEmergency != null && isEmergency);
        return notice;
    }

    /**
     * 기계번역 결과를 반영한다.
     *
     * 보호는 엔티티가 아니라 <b>필드</b> 단위다.
     * 수동 플래그가 켜져 있어도 비어 있는 필드는 채운다. 사람이 실제로 쓴 값
     * (null이 아닌 값)만 지켜주면 되기 때문이다. 엔티티 단위로 막으면
     * 관리자가 영문 제목만 채우고 본문을 비워둔 순간 본문이 영원히 비게 된다.
     *
     * null 인자는 "번역하지 못했다"는 뜻이므로 기존 값을 지우지 않는다.
     */
    public void applyTranslation(String titleEn, String contentEn) {
        if (titleEn != null && (!this.enIsManual || this.titleEn == null)) {
            this.titleEn = titleEn;
        }
        if (contentEn != null && (!this.enIsManual || this.contentEn == null)) {
            this.contentEn = contentEn;
        }
    }

    /**
     * 관리자가 직접 입력한 번역을 반영하고 수동 플래그를 켠다.
     */
    public void applyManualTranslation(String titleEn, String contentEn) {
        this.titleEn = titleEn;
        this.contentEn = contentEn;
        this.enIsManual = true;
    }
}
