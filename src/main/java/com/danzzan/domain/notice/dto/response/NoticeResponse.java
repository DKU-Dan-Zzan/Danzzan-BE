package com.danzzan.domain.notice.dto.response;

import com.danzzan.domain.notice.entity.Notice;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String thumbnailImageUrl;
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
        res.createdAt = notice.getCreatedAt();
        res.updatedAt = notice.getUpdatedAt();
        return res;
    }
}
