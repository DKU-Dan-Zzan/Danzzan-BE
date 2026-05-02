package com.danzzan.domain.user.exception;

public class CheonanCampusException extends RuntimeException {

    public CheonanCampusException() {
        super("대학원생 및 천안캠퍼스 학생은 단국축제서비스 회원가입이 불가합니다. 단국대학교 죽전캠퍼스 재학생만 이용할 수 있습니다.");
    }
}
