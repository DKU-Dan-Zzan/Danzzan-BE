-- 대기열 진입 원자 처리 Lua Script
--
-- KEYS[1] = dedupKey           (ticket:{eventId}:dedup:{userId})
-- KEYS[2] = seqKey             (ticket:{eventId}:seq)
-- KEYS[3] = queueKey           (ticket:{eventId}:queue)
-- KEYS[4] = queueUserHashKey   (ticket:{eventId}:quser:{userId})
-- ARGV[1] = userId             (ZSet member)
-- ARGV[2] = nowMs              (현재 epoch ms)
--
-- 반환값: seq (>0 = 신규 진입), 0 = 이미 대기열에 있음

local set_result = redis.call("SET", KEYS[1], "1", "NX")
if not set_result then
    return 0
end

local seq = redis.call("INCR", KEYS[2])
redis.call("ZADD", KEYS[3], seq, ARGV[1])
redis.call("HSET", KEYS[4], "state", "WAITING", "seq", tostring(seq), "enteredAt", ARGV[2])
return seq
