-- WAITING 선두 유저를 ACTIVE로 배치 승격
--
-- KEYS[1] = queueKey           (ticket:{eventId}:queue)
-- KEYS[2] = readyKey           (ticket:{eventId}:ready) -- 레거시 READY 정리용 카운트
-- KEYS[3] = activeKey          (ticket:{eventId}:active)
-- KEYS[4] = stockKey           (ticket:{eventId}:stock)
-- KEYS[5] = admittedSeqKey     (ticket:{eventId}:admitted-seq)
-- ARGV[1] = queueUserPrefix    (ticket:{eventId}:quser:)
-- ARGV[2] = nowMs
-- ARGV[3] = activeUntilMs
-- ARGV[4] = maxConcurrent
-- ARGV[5] = batchLimit
--
-- 반환값: 실제 승격된 유저 수

local ready_count = redis.call("ZCARD", KEYS[2])
local active_count = redis.call("ZCARD", KEYS[3])
local occupied = ready_count + active_count

local max_concurrent = tonumber(ARGV[4])
if not max_concurrent or max_concurrent <= 0 then
    return 0
end

local stock = tonumber(redis.call("GET", KEYS[4]))
if stock == nil or stock <= 0 then
    return 0
end

local batch_limit = tonumber(ARGV[5]) or 1
if batch_limit < 1 then
    batch_limit = 1
end

local admitted = 0

while admitted < batch_limit do
    if occupied >= max_concurrent or occupied >= stock then
        break
    end

    local first = redis.call("ZRANGE", KEYS[1], 0, 0)
    local user_id = first[1]
    if not user_id then
        break
    end

    local user_key = ARGV[1] .. user_id
    local state = redis.call("HGET", user_key, "state")

    if state == "WAITING" then
        redis.call("ZREM", KEYS[1], user_id)
        local seq = redis.call("HGET", user_key, "seq")
        redis.call("HSET", user_key,
            "state", "ACTIVE",
            "activeAt", ARGV[2],
            "activeUntil", ARGV[3]
        )
        redis.call("HDEL", user_key, "readyAt", "readyUntil")
        redis.call("ZADD", KEYS[3], ARGV[3], user_id)
        if seq then
            redis.call("SET", KEYS[5], seq)
        end
        occupied = occupied + 1
        admitted = admitted + 1
    else
        redis.call("ZREM", KEYS[1], user_id)
    end
end

return admitted
