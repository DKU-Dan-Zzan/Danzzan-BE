package com.danzzan.domain.user.passwordreset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "비밀번호 재설정 에러 응답")
public class PasswordResetErrorResponse {

    @Schema(description = "에러 메시지", example = "인증코드가 만료되었습니다.")
    private final String error;

    @Schema(description = "에러 코드", example = "PASSWORD_RESET_CODE_EXPIRED")
    private final String errorCode;
}
