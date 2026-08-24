package com.danzzan.infra.translation;

public class TranslationUnavailableException extends RuntimeException {

    public TranslationUnavailableException(String message) {
        super(message);
    }

    public TranslationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
