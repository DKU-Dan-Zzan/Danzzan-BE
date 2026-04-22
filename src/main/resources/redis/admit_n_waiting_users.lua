-- WAITING 유저를 최대 N명 한 번에 READY로 원자 승격
--
-- KEYS[1] = queueKey           (ticket:{eventId}:queue)
-- KEYS[2] = readyKey           (ticket:{eventId}:ready)
-- KEYS[3] = activeKey          (ticket:{eventId}:active)
-- KEYS[4] = stockKey           (ticket:{eventId}:stock)
-- ARGV[1] = queueUserPrefix    (ticket:{eventId}:quser:)
-- ARGV[2] = nowMs
-- ARGV[3] = readyUntilMs
-- ARGV[4] = maxConcurrent
-- ARGV[5] = batchCeiling       (최대 승격 인원 상한)
--
-- 반환값: 승격된 userId 배열 (빈 배열 = 승격 없음)

local ready_count  = redis.call("ZCARD", KEYS[2])
local active_count = redis.call("ZCARD", KEYS[3])
local occupied     = ready_count + active_count

local max_concurrent = tonumber(ARGV[4])
local batch_ceiling  = tonumber(ARGV[5])

if occupied >= max_concurrent then
    return {}
end

local stock = tonumber(redis.call("GET", KEYS[4]))
if stock == nil or stock <= 0 then
    return {}
end

-- 슬롯 여유, 재고, 배치 상한 중 가장 작은 값만큼만 승격
local available = math.min(max_concurrent - occupied, stock - occupied, batch_ceiling)
if available <= 0 then
    return {}
end

local admitted = {}

for _ = 1, available do
    while true do
        local first = redis.call("ZRANGE", KEYS[1], 0, 0)
        local user_id = first[1]
        if not user_id then
            return admitted
        end

        local user_key = ARGV[1] .. user_id
        local state = redis.call("HGET", user_key, "state")

        if state == "WAITING" then
            redis.call("ZREM", KEYS[1], user_id)
            redis.call("HSET", user_key,
                "state",      "READY",
                "readyAt",    ARGV[2],
                "readyUntil", ARGV[3]
            )
            redis.call("ZADD", KEYS[2], ARGV[3], user_id)
            table.insert(admitted, user_id)
            break
        end

        -- state가 WAITING이 아닌 좀비 항목은 queue에서 제거 후 다음 시도
        redis.call("ZREM", KEYS[1], user_id)
    end
end

return admitted
