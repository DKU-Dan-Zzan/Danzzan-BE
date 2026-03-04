package com.danzzan.ticketing.domain.user.exception;

public class AlreadyStudentIdException extends RuntimeException {

    public AlreadyStudentIdException() {
        super("이미 가입된 학번입니다.");
    }

    public AlreadyStudentIdException(String message) {
        super(message);
    }
}
