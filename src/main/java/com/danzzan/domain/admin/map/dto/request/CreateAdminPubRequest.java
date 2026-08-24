package com.danzzan.domain.admin.map.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CreateAdminPubRequest {
    @NotNull(message = "collegeId는 필수입니다.")
    private Long collegeId;

    @NotBlank(message = "department는 필수입니다.")
    private String department;

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    private String intro;
    private String description;
    private String instagram;

    /**
     * 관리자가 직접 입력한 영문 필드들. 비워두면 자동번역 결과가 채워진다.
     */
    private String nameEn;
    private String introEn;
    private String descriptionEn;
    private String departmentEn;

    @NotNull(message = "displayOperationIds는 null일 수 없습니다.")
    private List<@NotNull(message = "표시 일자 ID는 null일 수 없습니다.") Long> displayOperationIds;
}
