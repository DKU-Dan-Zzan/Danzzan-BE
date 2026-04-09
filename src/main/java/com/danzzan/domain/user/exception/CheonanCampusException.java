package com.danzzan.domain.user.exception;

public class CheonanCampusException extends RuntimeException {

    public CheonanCampusException() {
        super("천안캠퍼스 학생은 단국축제(DANFESTA) 회원가입이 불가합니다. 죽전캠퍼스 학생만 이용할 수 있습니다.");
    }
}
