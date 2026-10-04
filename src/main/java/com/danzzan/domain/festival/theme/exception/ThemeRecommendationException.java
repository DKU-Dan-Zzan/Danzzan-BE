package com.danzzan.domain.festival.theme.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ThemeRecommendationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ThemeRecommendationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
