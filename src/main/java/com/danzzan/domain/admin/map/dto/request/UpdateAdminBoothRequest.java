package com.danzzan.domain.admin.map.dto.request;

import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateAdminBoothRequest {
    @NotNull
    private LocalDate operationDate;

    @NotNull
    private BoothOperationStatus operationStatus;

    private String name;

    private String description;

    /**
     * 관리자가 직접 입력한 영문 이름. 비워두면(=null/blank) 이름의 한국어가 바뀐 경우에 한해
     * 자동번역 결과로 채워진다. 한국어가 안 바뀌었다면 저장된 영문 값이 그대로 유지된다.
     */
    private String nameEn;

    /**
     * 관리자가 직접 입력한 영문 설명. 규칙은 nameEn과 동일하다.
     */
    private String descriptionEn;

    private LocalTime startTime;

    private LocalTime endTime;

    @NotNull
    private List<@NotNull LocalDate> operationDates;
}
