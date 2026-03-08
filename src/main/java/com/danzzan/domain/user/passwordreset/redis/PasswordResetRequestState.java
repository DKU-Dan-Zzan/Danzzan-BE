package com.danzzan.domain.user.passwordreset.redis;

import java.util.Map;

public record PasswordResetRequestState(
        String requestId,
        String studentId,
        String codeHash,
        int verifyAttempts,
        boolean verified,
        String verificationTokenHash,
        boolean consumed,
        boolean userExists
) {

    public static final String FIELD_REQUEST_ID = "requestId";
    public static final String FIELD_STUDENT_ID = "studentId";
    public static final String FIELD_CODE_HASH = "codeHash";
    public static final String FIELD_VERIFY_ATTEMPTS = "verifyAttempts";
    public static final String FIELD_VERIFIED = "verified";
    public static final String FIELD_VERIFICATION_TOKEN_HASH = "verificationTokenHash";
    public static final String FIELD_CONSUMED = "consumed";
    public static final String FIELD_USER_EXISTS = "userExists";

    public static PasswordResetRequestState fromHash(Map<Object, Object> raw) {
        return new PasswordResetRequestState(
                string(raw.get(FIELD_REQUEST_ID)),
                string(raw.get(FIELD_STUDENT_ID)),
                string(raw.get(FIELD_CODE_HASH)),
                intValue(raw.get(FIELD_VERIFY_ATTEMPTS), 0),
                bool(raw.get(FIELD_VERIFIED)),
                string(raw.get(FIELD_VERIFICATION_TOKEN_HASH)),
                bool(raw.get(FIELD_CONSUMED)),
                bool(raw.get(FIELD_USER_EXISTS))
        );
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean bool(Object value) {
        return "1".equals(string(value));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
