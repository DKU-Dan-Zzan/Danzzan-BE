-- DB 저장 실패 시 claim 결과를 원자적으로 원복
--
-- KEYS[1] = stockKey   (ticket:{eventId}:stock)
-- KEYS[2] = userKey    (ticket:{eventId}:user:{userId})
-- KEYS[3] = statusKey  (ticket:{eventId}:status:{userId})
--
-- 반환값: 1

redis.call("INCR", KEYS[1])
redis.call("DEL", KEYS[2], KEYS[3])
return 1
