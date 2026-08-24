package com.danzzan.domain.admin.map.dto.request;

import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class CreateAdminBoothRequest {
    @NotNull(message = "type는 필수입니다.")
    private BoothType type;

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    private String description;

    /**
     * 관리자가 직접 입력한 영문 이름. 비워두면 자동번역 결과가 채워진다.
     */
    private String nameEn;

    /**
     * 관리자가 직접 입력한 영문 설명. 비워두면 자동번역 결과가 채워진다.
     */
    private String descriptionEn;

    @NotNull(message = "operationStatus는 필수입니다.")
    private BoothOperationStatus operationStatus;

    private LocalTime startTime;

    private LocalTime endTime;

    @NotNull(message = "operationDates는 null일 수 없습니다.")
    private List<@NotNull(message = "운영 날짜는 null일 수 없습니다.") LocalDate> operationDates;
}
