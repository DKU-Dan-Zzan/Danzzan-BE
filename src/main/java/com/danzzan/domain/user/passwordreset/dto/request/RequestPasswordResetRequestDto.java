package com.danzzan.domain.user.passwordreset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "비밀번호 재설정 요청")
public class RequestPasswordResetRequestDto {

    @NotBlank(message = "학번은 필수입니다.")
    @Pattern(regexp = "^\\d{8}$", message = "학번은 8자리 숫자여야 합니다.")
    @Schema(description = "학번(8자리)", example = "32100000")
    private final String studentId;

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Schema(description = "이메일(선택)", example = "32100000@dankook.ac.kr", nullable = true)
    private final String email;
}
