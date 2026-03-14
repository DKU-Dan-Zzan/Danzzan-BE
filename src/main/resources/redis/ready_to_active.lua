-- READY → ACTIVE 원자 전환 Lua Script
--
-- KEYS[1] = queueUserHashKey  (ticket:{eventId}:quser:{userId})
-- KEYS[2] = readyKey          (ticket:{eventId}:ready)
-- KEYS[3] = activeKey         (ticket:{eventId}:active)
-- KEYS[4] = dedupKey          (ticket:{eventId}:dedup:{userId})
-- ARGV[1] = nowMs             (현재 epoch ms)
-- ARGV[2] = userId            (READY/ACTIVE ZSet member)
-- ARGV[3] = activeUntilMs     (ACTIVE TTL 만료 epoch ms)
--
-- 반환값: 1=READY->ACTIVE 성공, 2=이미 ACTIVE(lease 연장), 0=READY/ACTIVE 아님, -1=READY 만료, -2=ACTIVE 만료

local state = redis.call("HGET", KEYS[1], "state")
local nowMs = tonumber(ARGV[1])

if state == "ACTIVE" then
    local activeUntil = tonumber(redis.call("HGET", KEYS[1], "activeUntil"))
    if activeUntil == nil or activeUntil < nowMs then
        redis.call("HSET", KEYS[1], "state", "EXPIRED", "expiredAt", ARGV[1])
        redis.call("ZREM", KEYS[3], ARGV[2])
        redis.call("DEL", KEYS[4])
        return -2
    end

    redis.call("HSET", KEYS[1], "activeUntil", ARGV[3])
    redis.call("ZADD", KEYS[3], ARGV[3], ARGV[2])
    return 2
end

if state ~= "READY" then
    return 0
end

local readyUntil = tonumber(redis.call("HGET", KEYS[1], "readyUntil"))
if readyUntil == nil or readyUntil < nowMs then
    redis.call("HSET", KEYS[1], "state", "EXPIRED", "expiredAt", ARGV[1])
    redis.call("ZREM", KEYS[2], ARGV[2])
    redis.call("DEL", KEYS[4])
    return -1
end

redis.call("HSET", KEYS[1], "state", "ACTIVE")
redis.call("HSET", KEYS[1], "activeAt", ARGV[1])
redis.call("HSET", KEYS[1], "activeUntil", ARGV[3])
redis.call("ZREM", KEYS[2], ARGV[2])
redis.call("ZADD", KEYS[3], ARGV[3], ARGV[2])
return 1
