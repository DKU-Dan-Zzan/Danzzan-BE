package com.danzzan.festival.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "학번을 입력해 주세요.")
    private String studentNumber;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;
}
