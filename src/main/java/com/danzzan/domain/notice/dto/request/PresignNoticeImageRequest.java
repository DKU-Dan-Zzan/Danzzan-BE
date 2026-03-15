package com.danzzan.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PresignNoticeImageRequest {

    @NotBlank(message = "파일명을 입력해 주세요.")
    private String fileName;

    /**
     * 예: image/png
     * (선택) 미입력 시 content-type 없이 presign 발급됩니다.
     */
    private String contentType;
}

