package com.danzzan.domain.user.passwordreset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "비밀번호 재설정 인증코드 검증 응답")
public class ResponsePasswordResetVerifyDto {

    @Schema(description = "검증 완료 후 발급되는 일회성 토큰", example = "leXwtYI4sUvUQq6MCZi6wGGB9v2rG6pYk5LUou9SKCg")
    private final String verificationToken;
}
