package com.danzzan.domain.notice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 관리자 공지 목록에서 드래그 앤 드롭으로 변경된 displayOrder를 일괄 업데이트하기 위한 요청 DTO.
 * 프론트에서는 핀 공지 섹션 등, 재정렬된 공지들에 대해서만 id와 displayOrder를 보내면 됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateNoticeDisplayOrderRequest {

    @NotEmpty
    @Valid
    private List<Item> orders;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        @NotNull
        private Long id;

        @NotNull
        private Integer displayOrder;
    }
}

