package com.danzzan.domain.user.passwordreset.dto.request;

import com.danzzan.domain.user.validation.PasswordPolicy;
import com.danzzan.domain.user.validation.ValidPasswordConfirmation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@ValidPasswordConfirmation(passwordField = "newPassword", confirmPasswordField = "confirmPassword")
@Schema(description = "비밀번호 재설정 최종 요청")
public class RequestPasswordResetDto {

    @NotBlank(message = "requestId는 필수입니다.")
    @Schema(description = "재설정 요청 ID", example = "0a53286f-c4e4-4fe1-88fe-7a2148bc2a7c")
    private final String requestId;

    @NotBlank(message = "verificationToken은 필수입니다.")
    @Schema(description = "검증 완료 후 발급된 일회성 토큰", example = "leXwtYI4sUvUQq6MCZi6wGGB9v2rG6pYk5LUou9SKCg")
    private final String verificationToken;

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    @Schema(description = "새 비밀번호", example = "NewPass!2026")
    private final String newPassword;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    @Schema(description = "비밀번호 확인", example = "NewPass!2026")
    private final String confirmPassword;
}
