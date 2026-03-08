package com.danzzan.domain.user.passwordreset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "비밀번호 재설정 요청 응답")
public class ResponsePasswordResetRequestDto {

    @Schema(description = "재설정 요청 ID", example = "0a53286f-c4e4-4fe1-88fe-7a2148bc2a7c")
    private final String requestId;

    @Schema(description = "인증코드 만료까지 남은 초", example = "300")
    private final long expiresInSec;
}
