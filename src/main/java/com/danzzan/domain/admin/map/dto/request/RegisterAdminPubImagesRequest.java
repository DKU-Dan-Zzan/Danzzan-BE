package com.danzzan.domain.admin.map.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RegisterAdminPubImagesRequest {

    @NotEmpty(message = "등록할 이미지 URL을 하나 이상 전달해 주세요.")
    private List<String> imageUrls;

    private String mainImageUrl;
}
