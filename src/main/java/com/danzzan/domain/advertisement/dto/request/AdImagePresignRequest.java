package com.danzzan.domain.advertisement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdImagePresignRequest {

    @NotBlank(message = "파일명을 입력해 주세요.")
    private String fileName;

    @NotBlank(message = "콘텐츠 타입을 입력해 주세요.")
    private String contentType;

    /**
     * 선택: 프론트에서 파일 크기(바이트)를 같이 보내면
     * 서버에서도 1차로 5MB 초과 여부를 필터링할 수 있습니다.
     */
    private Long fileSize;
}

