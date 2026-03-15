package com.danzzan.domain.advertisement.dto.request;

import com.danzzan.domain.advertisement.model.entity.AdvertisementPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UpdateAdvertisementRequest {

    @NotBlank(message = "광고 제목을 입력해 주세요.")
    private String title;

    @NotBlank(message = "광고 이미지 URL을 입력해 주세요.")
    private String imageUrl;

    private String linkUrl;

    @NotNull(message = "노출 위치(placement)를 선택해 주세요.")
    private AdvertisementPlacement placement;

    @NotNull(message = "노출 시작일을 입력해 주세요.")
    private LocalDate startDate;

    @NotNull(message = "노출 종료일을 입력해 주세요.")
    private LocalDate endDate;

    private Boolean isActive = true;

    private Integer priority;
}
