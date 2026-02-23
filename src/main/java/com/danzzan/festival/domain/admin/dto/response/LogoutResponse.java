package com.danzzan.festival.domain.admin.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LogoutResponse {

    private String message = "로그아웃되었습니다.";

    public static LogoutResponse ok() {
        return new LogoutResponse();
    }
}
