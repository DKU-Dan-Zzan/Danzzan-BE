package com.danzzan.festival.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String message,
        int status,
        List<FieldError> errors
) {
    public static ErrorResponse of(String message, int status) {
        return new ErrorResponse(message, status, null);
    }

    public static ErrorResponse of(String message, int status, List<FieldError> errors) {
        return new ErrorResponse(message, status, errors);
    }

    public record FieldError(String field, String reason) {}
}
