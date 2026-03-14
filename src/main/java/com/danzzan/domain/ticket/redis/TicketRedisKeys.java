package com.danzzan.domain.ticket.redis;

public final class TicketRedisKeys {

    private static final String PREFIX = "ticket";
    private static final String COLON_ESCAPE = "%3A";

    private TicketRedisKeys() {
    }

    /** Lua claim 재고 차감용 키 */
    public static String stockKey(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":stock";
    }

    /** Lua claim 중복 방지 키 (claim 여부 저장) */
    public static String userKey(String eventId, String userId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":user:" + keyPart(userId, "userId");
    }

    /** Lua claim 결과 상태 키 (SUCCESS/SOLD_OUT/ALREADY) */
    public static String statusKey(String eventId, String userId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":status:" + keyPart(userId, "userId");
    }

    /** 대기열 ZSet (score = INCR sequence, WAITING 유저만 존재) */
    public static String queueKey(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":queue";
    }

    /** 순번 발급 카운터 (INCR) */
    public static String seqKey(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":seq";
    }

    /** 유저 상태 Hash (state, seq, enteredAt, readyAt, readyUntil, activeAt) */
    public static String queueUserHashKey(String eventId, String userId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":quser:" + keyPart(userId, "userId");
    }

    /** 유저 상태 Hash prefix (Lua 내부 동적 조합용) */
    public static String queueUserPrefix(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":quser:";
    }

    /** READY 유저 ZSet (score = readyUntil epoch ms) — 만료 정리 + 카운트용 */
    public static String readyKey(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":ready";
    }

    /** ACTIVE 유저 ZSet (score = activeUntil epoch ms) — 만료 정리 + 카운트용 */
    public static String activeKey(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":active";
    }

    /** 중복 진입 방지 키 */
    public static String dedupKey(String eventId, String userId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":dedup:" + keyPart(userId, "userId");
    }

    /** 중복 진입 방지 키 prefix (Lua 내부 동적 조합용) */
    public static String dedupKeyPrefix(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":dedup:";
    }

    /** CLOSED 이벤트 대기열 정리 완료 마커 */
    public static String closedCleanupKey(String eventId) {
        return PREFIX + ":" + keyPart(eventId, "eventId") + ":closed-cleanup";
    }

    private static String keyPart(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return raw.trim().replace(":", COLON_ESCAPE);
    }
}
