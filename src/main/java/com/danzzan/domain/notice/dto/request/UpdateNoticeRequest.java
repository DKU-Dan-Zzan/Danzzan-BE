package com.danzzan.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateNoticeRequest {

    @NotBlank(message = "제목을 입력해 주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    @NotBlank(message = "작성자를 입력해 주세요.")
    private String author;

    /** 카테고리 (미입력 시 GENERAL) */
    private String category;

    /** 상단 고정 여부 (기본 false) */
    private Boolean isPinned = false;

    /** 대표 이미지 URL (선택) */
    private String thumbnailImageUrl;
}
