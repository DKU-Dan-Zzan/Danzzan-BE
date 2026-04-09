package com.danzzan.domain.auth.dto;

import com.danzzan.domain.user.validation.PasswordPolicy;
import com.danzzan.domain.user.validation.ValidPasswordConfirmation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@ValidPasswordConfirmation(passwordField = "password", confirmPasswordField = "confirmPassword")
@Schema(description = "회원가입 완료 요청")
public class RequestSignupDto {

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    @Schema(description = "축제 서비스 비밀번호", example = "MySecure!123")
    private final String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    @Schema(description = "축제 서비스 비밀번호 확인", example = "MySecure!123")
    private final String confirmPassword;

    @NotBlank(message = "전화번호 인증 세션 ID는 필수입니다.")
    @Schema(description = "Step2에서 검증 완료된 전화번호 인증 세션 ID", example = "6a6b84dc-7970-4305-9bf3-38a3d431f6c6")
    private final String phoneVerificationSessionId;
}
