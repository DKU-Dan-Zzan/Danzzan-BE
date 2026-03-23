package com.danzzan.domain.advertisement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PresignAdvertisementImageRequest {

    @NotBlank(message = "파일명을 입력해 주세요.")
    private String fileName;

    /**
     * 예: image/png
     */
    private String contentType;

    /**
     * 파일 크기 (byte 단위).
     */
    private Long fileSize;
}
