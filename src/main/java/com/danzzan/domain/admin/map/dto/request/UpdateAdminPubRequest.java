package com.danzzan.domain.admin.map.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateAdminPubRequest {
    private String name;
    private String intro;
    private String description;
    private String instagram;

    /**
     * 관리자가 직접 입력한 영문 필드들. 비워두면(=null/blank) 그 필드의 한국어가
     * 이번 요청에서 실제로 바뀐 경우에 한해 자동번역 결과로 채워진다.
     * department는 이 요청에 한국어 필드 자체가 없어(수정 불가) 한국어 변경으로 인한
     * 재번역 트리거는 없지만, departmentEn만 단독으로 넣으면 기존 영문 부서명을
     * 수동으로 고쳐 쓸 수 있다.
     */
    private String nameEn;
    private String introEn;
    private String descriptionEn;
    private String departmentEn;

    @NotNull(message = "displayOperationIds는 null일 수 없습니다.")
    private List<@NotNull(message = "표시 일자 ID는 null일 수 없습니다.") Long> displayOperationIds;
}
