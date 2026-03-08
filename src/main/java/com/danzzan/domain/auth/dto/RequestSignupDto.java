package com.danzzan.domain.auth.dto;

import com.danzzan.domain.user.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "회원가입 완료 요청")
public class RequestSignupDto {

    @NotBlank(message = "비밀번호는 필수입니다")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    @Schema(description = "비밀번호", example = "MySecure!123")
    private final String password;
}
