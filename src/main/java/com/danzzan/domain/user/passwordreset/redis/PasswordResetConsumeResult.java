package com.danzzan.domain.user.passwordreset.redis;

public record PasswordResetConsumeResult(
        ConsumeStatus status,
        String studentId,
        boolean userExists
) {

    public enum ConsumeStatus {
        SUCCESS,
        REQUEST_NOT_FOUND,
        TOKEN_NOT_VERIFIED,
        INVALID_TOKEN,
        TOKEN_ALREADY_CONSUMED
    }

    public static PasswordResetConsumeResult success(String studentId, boolean userExists) {
        return new PasswordResetConsumeResult(ConsumeStatus.SUCCESS, studentId, userExists);
    }

    public static PasswordResetConsumeResult fail(ConsumeStatus status) {
        return new PasswordResetConsumeResult(status, null, false);
    }
}
