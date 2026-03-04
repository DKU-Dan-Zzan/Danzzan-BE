package com.danzzan.ticketing.global.exception;

// 관리자 권한이 없을 때 발생하는 예외
public class AdminForbiddenException extends RuntimeException {
    public AdminForbiddenException() {
        super("관리자 권한이 필요합니다.");
    }

    public AdminForbiddenException(String message) {
        super(message);
    }
}
