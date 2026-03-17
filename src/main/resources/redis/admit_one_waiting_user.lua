-- WAITING 선두 유저 1명을 READY로 원자 승격
--
-- KEYS[1] = queueKey           (ticket:{eventId}:queue)
-- KEYS[2] = readyKey           (ticket:{eventId}:ready)
-- KEYS[3] = activeKey          (ticket:{eventId}:active)
-- KEYS[4] = stockKey           (ticket:{eventId}:stock)
-- ARGV[1] = queueUserPrefix    (ticket:{eventId}:quser:)
-- ARGV[2] = nowMs
-- ARGV[3] = readyUntilMs
-- ARGV[4] = maxConcurrent
--
-- 반환값: 승격 성공 시 userId, 실패 시 빈 문자열

local ready_count = redis.call("ZCARD", KEYS[2])
local active_count = redis.call("ZCARD", KEYS[3])
local occupied = ready_count + active_count

local max_concurrent = tonumber(ARGV[4])
if occupied >= max_concurrent then
    return ""
end

local stock = tonumber(redis.call("GET", KEYS[4]))
if stock == nil or stock <= 0 then
    return ""
end

if occupied >= stock then
    return ""
end

while true do
    local first = redis.call("ZRANGE", KEYS[1], 0, 0)
    local user_id = first[1]
    if not user_id then
        return ""
    end

    local user_key = ARGV[1] .. user_id
    local state = redis.call("HGET", user_key, "state")

    if state == "WAITING" then
        redis.call("ZREM", KEYS[1], user_id)
        redis.call("HSET", user_key,
            "state", "READY",
            "readyAt", ARGV[2],
            "readyUntil", ARGV[3]
        )
        redis.call("ZADD", KEYS[2], ARGV[3], user_id)
        return user_id
    end

    redis.call("ZREM", KEYS[1], user_id)
end
