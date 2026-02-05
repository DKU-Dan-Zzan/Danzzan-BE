package com.danzzan.ticketing.domain.user.exception;

public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("비밀번호가 일치하지 않습니다.");
    }

    public WrongPasswordException(String message) {
        super(message);
    }
}
