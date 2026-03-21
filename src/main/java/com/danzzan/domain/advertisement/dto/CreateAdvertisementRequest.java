package com.danzzan.domain.advertisement.dto;

import com.danzzan.domain.advertisement.AdvertisementPlacement;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateAdvertisementRequest {

    @NotBlank(message = "광고 제목을 입력해 주세요.")
    @JsonAlias({"adTitle", "ad_title"})
    private String title;

    @NotBlank(message = "광고 이미지 URL을 입력해 주세요.")
    @JsonAlias({"fileUrl", "image_url", "imageURL"})
    private String imageUrl;

    @NotNull(message = "광고 노출 위치를 선택해 주세요.")
    @JsonAlias({"adLocation", "ad_location"})
    private AdvertisementPlacement placement;
}
