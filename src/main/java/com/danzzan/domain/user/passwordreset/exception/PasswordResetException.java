package com.danzzan.domain.user.passwordreset.exception;

import lombok.Getter;

@Getter
public class PasswordResetException extends RuntimeException {

    private final PasswordResetErrorType errorType;

    public PasswordResetException(PasswordResetErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
    }

    public PasswordResetException(PasswordResetErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
