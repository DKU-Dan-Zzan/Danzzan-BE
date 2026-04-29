package com.danzzan.domain.admin.map.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PresignAdminPubImageRequest {

    @NotBlank(message = "파일명을 입력해 주세요.")
    private String fileName;

    private String contentType;

    private Long fileSize;
}
