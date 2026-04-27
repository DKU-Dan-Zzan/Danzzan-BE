-- 대기열 신규 진입 전용 Lua Script
--
-- KEYS[1] = dedupKey           (ticket:{eventId}:dedup:{userId})
-- KEYS[2] = seqKey             (ticket:{eventId}:seq)
-- KEYS[3] = queueKey           (ticket:{eventId}:queue)
-- KEYS[4] = queueUserHashKey   (ticket:{eventId}:quser:{userId})
-- KEYS[5] = stockKey           (ticket:{eventId}:stock)
-- ARGV[1] = userId             (ZSet member)
-- ARGV[2] = nowMs              (현재 epoch ms)
--
-- 반환값: {status, queuePosition, requestId, acceptedAt}
-- status: WAITING | SOLD_OUT
-- queuePosition: 항상 0 (상태 상세는 /queue/status에서 계산)

local function response(status, queuePosition, requestId, acceptedAt)
    local rid = requestId or ""
    local accepted = acceptedAt or "0"
    return { status, queuePosition, rid, accepted }
end

local dedup_set = redis.call("SET", KEYS[1], "1", "NX")
if not dedup_set then
    return response("WAITING", 0, "", "0")
end

local seq = redis.call("INCR", KEYS[2])
local stock = tonumber(redis.call("GET", KEYS[5]))
if stock == nil or stock <= 0 then
    redis.call("DEL", KEYS[1])
    return response("SOLD_OUT", 0, "", "0")
end

redis.call("ZADD", KEYS[3], seq, ARGV[1])
redis.call("HSET", KEYS[4], "state", "WAITING", "seq", tostring(seq), "enteredAt", ARGV[2])
return response("WAITING", 0, "", "0")
