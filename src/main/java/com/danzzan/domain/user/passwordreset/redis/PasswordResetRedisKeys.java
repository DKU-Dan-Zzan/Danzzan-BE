package com.danzzan.domain.user.passwordreset.redis;

public final class PasswordResetRedisKeys {

    private static final String PREFIX = "password-reset";
    private static final String COLON_ESCAPE = "%3A";

    private PasswordResetRedisKeys() {
    }

    public static String requestKey(String requestId) {
        return PREFIX + ":req:" + keyPart(requestId, "requestId");
    }

    public static String cooldownKey(String studentId) {
        return PREFIX + ":cooldown:" + keyPart(studentId, "studentId");
    }

    public static String requestRateKey(String studentId, String clientIp) {
        return PREFIX + ":rate:" + keyPart(studentId, "studentId") + ":" + keyPart(clientIp, "clientIp");
    }

    private static String keyPart(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return raw.trim().replace(":", COLON_ESCAPE);
    }
}
