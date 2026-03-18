package com.danzzan.domain.notice.dto.response;

import com.danzzan.domain.notice.entity.Notice;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class NoticeResponse {

    private Long id;
    private String title;
    private String content;
    private String author;
    private Boolean isEmergency;
    private Boolean isActive;
    private String category;
    private Boolean isPinned;

    /**
     * 썸네일 이미지 URL (없을 수 있음).
     */
    private String thumbnailImageUrl;

    /**
     * 본문 이미지 URL 배열.
     * 관리자/클라이언트 공지 모두 동일하게 사용합니다.
     */
    private List<String> imageUrls = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoticeResponse from(Notice notice) {
        NoticeResponse res = new NoticeResponse();
        res.id = notice.getId();
        res.title = notice.getTitle();
        res.content = notice.getContent();
        res.author = notice.getAuthor();
        res.isEmergency = notice.getIsEmergency();
        res.isActive = notice.getIsActive();
        res.category = (notice.getCategory() == null || notice.getCategory().isBlank())
                ? "GENERAL"
                : notice.getCategory();
        res.isPinned = Boolean.TRUE.equals(notice.getIsPinned());
        res.thumbnailImageUrl = notice.getThumbnailImageUrl();
        if (notice.getImages() != null) {
            res.imageUrls = new ArrayList<>(notice.getImages());
        }
        res.createdAt = notice.getCreatedAt();
        res.updatedAt = notice.getUpdatedAt();
        return res;
    }
}
