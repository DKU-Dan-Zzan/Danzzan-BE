package com.danzzan.ticketing.global.exception;

// 관리자 인증 실패 시 발생하는 예외
public class AdminAuthenticationException extends RuntimeException {
    public AdminAuthenticationException() {
        super("관리자 인증에 실패했습니다.");
    }

    public AdminAuthenticationException(String message) {
        super(message);
    }
}
