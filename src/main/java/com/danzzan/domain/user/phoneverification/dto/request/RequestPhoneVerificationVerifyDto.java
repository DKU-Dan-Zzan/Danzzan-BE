package com.danzzan.domain.user.phoneverification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "전화번호 인증 검증 요청")
public class RequestPhoneVerificationVerifyDto {

    @NotBlank(message = "전화번호는 필수입니다.")
    @Schema(description = "문자를 보낸 사용자 전화번호", example = "01012345678")
    private final String phoneNumber;
}
