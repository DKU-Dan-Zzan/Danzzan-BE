package com.danzzan.domain.timetable.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PresignArtistImageRequest {

    @NotBlank(message = "파일명을 입력해 주세요.")
    private String fileName;

    /**
     * 예: image/png. (선택) 미입력 시 Content-Type 없이 presign이 발급된다.
     */
    private String contentType;

    /**
     * 파일 크기(byte). 5MB 초과 시 서버에서 1차 검증.
     */
    private Long fileSize;
}
