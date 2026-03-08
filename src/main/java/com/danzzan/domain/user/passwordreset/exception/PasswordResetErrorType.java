package com.danzzan.domain.user.passwordreset.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PasswordResetErrorType {
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "PASSWORD_RESET_REQUEST_NOT_FOUND", "비밀번호 재설정 요청을 찾을 수 없습니다."),
    CODE_EXPIRED(HttpStatus.GONE, "PASSWORD_RESET_CODE_EXPIRED", "인증코드가 만료되었습니다."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_CODE_MISMATCH", "인증코드가 일치하지 않습니다."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "PASSWORD_RESET_TOO_MANY_ATTEMPTS", "인증 시도 횟수를 초과했습니다."),
    TOKEN_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_NOT_VERIFIED", "인증코드 검증이 완료되지 않았습니다."),
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID_VERIFICATION_TOKEN", "유효하지 않은 verificationToken입니다."),
    TOKEN_ALREADY_CONSUMED(HttpStatus.CONFLICT, "PASSWORD_RESET_TOKEN_ALREADY_CONSUMED", "이미 사용된 verificationToken입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "PASSWORD_RESET_USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "PASSWORD_RESET_RESEND_COOLDOWN", "인증코드 재요청 쿨다운 중입니다."),
    REQUEST_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "PASSWORD_RESET_RATE_LIMITED", "요청 횟수 제한을 초과했습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
