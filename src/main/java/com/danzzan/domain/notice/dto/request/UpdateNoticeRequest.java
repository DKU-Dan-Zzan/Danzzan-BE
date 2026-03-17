package com.danzzan.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    /**
     * 공지 본문 이미지 URL 목록.
     * 수정 시에는 전체 목록을 다시 보낸다는 가정으로 기존 이미지를 전부 교체합니다.
     */
    private List<String> images;

    /**
     * 대표 이미지 URL (선택).
     * 미지정이고 images가 비어있지 않으면 서버에서 images[0]을 썸네일로 사용합니다.
     */
    private String thumbnailImageUrl;
}
