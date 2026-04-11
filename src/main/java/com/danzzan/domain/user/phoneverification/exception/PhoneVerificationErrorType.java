package com.danzzan.domain.user.phoneverification.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PhoneVerificationErrorType {
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "PHONE_VERIFICATION_SESSION_NOT_FOUND", "전화번호 인증 세션을 찾을 수 없습니다."),
    SESSION_EXPIRED(HttpStatus.GONE, "PHONE_VERIFICATION_SESSION_EXPIRED", "전화번호 인증 세션이 만료되었습니다."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "PHONE_VERIFICATION_ALREADY_VERIFIED", "이미 인증 완료된 세션입니다."),
    NOT_VERIFIED(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_NOT_VERIFIED", "전화번호 인증이 완료되지 않았습니다."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "PHONE_VERIFICATION_TOO_MANY_ATTEMPTS", "전화번호 인증 확인 시도 횟수를 초과했습니다."),
    CREATE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "PHONE_VERIFICATION_CREATE_RATE_LIMITED", "전화번호 인증 세션 생성 요청이 너무 많습니다."),
    CREATE_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "PHONE_VERIFICATION_CREATE_COOLDOWN", "새 인증 세션을 너무 자주 생성하고 있습니다."),
    OCTOMO_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, "PHONE_VERIFICATION_OCTOMO_LOOKUP_FAILED", "OCTOMO 수신 메시지 조회에 실패했습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_MESSAGE_NOT_FOUND", "아직 OCTOMO 수신 메시지가 확인되지 않았습니다."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_CODE_MISMATCH", "수신된 메시지의 인증코드가 일치하지 않습니다."),
    PHONE_ALREADY_LINKED(HttpStatus.CONFLICT, "PHONE_VERIFICATION_PHONE_ALREADY_LINKED", "이미 다른 계정에 연결된 전화번호입니다."),
    SESSION_ALREADY_CONSUMED(HttpStatus.CONFLICT, "PHONE_VERIFICATION_SESSION_ALREADY_CONSUMED", "이미 회원가입에 사용된 인증 세션입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
