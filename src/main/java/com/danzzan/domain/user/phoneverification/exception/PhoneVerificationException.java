package com.danzzan.domain.user.phoneverification.exception;

import lombok.Getter;

@Getter
public class PhoneVerificationException extends RuntimeException {

    private final PhoneVerificationErrorType errorType;

    public PhoneVerificationException(PhoneVerificationErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
    }

    public PhoneVerificationException(PhoneVerificationErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
