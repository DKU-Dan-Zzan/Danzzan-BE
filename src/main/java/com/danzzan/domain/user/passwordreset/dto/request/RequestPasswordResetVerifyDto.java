package com.danzzan.domain.user.passwordreset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "비밀번호 재설정 인증코드 검증")
public class RequestPasswordResetVerifyDto {

    @NotBlank(message = "requestId는 필수입니다.")
    @Schema(description = "재설정 요청 ID", example = "0a53286f-c4e4-4fe1-88fe-7a2148bc2a7c")
    private final String requestId;

    @NotBlank(message = "인증코드는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자여야 합니다.")
    @Schema(description = "6자리 인증코드", example = "195027")
    private final String code;
}
