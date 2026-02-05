package com.danzzan.ticketing.infra.dku.exception;

public class DkuFailedLoginException extends RuntimeException {

    public DkuFailedLoginException() {
        super("단국대 포털 로그인에 실패했습니다. 학번과 비밀번호를 확인해주세요.");
    }

    public DkuFailedLoginException(String message) {
        super(message);
    }
}
