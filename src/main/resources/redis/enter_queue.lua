-- 대기열 진입 원자 처리 Lua Script
--
-- KEYS[1] = dedupKey           (ticket:{eventId}:dedup:{userId})
-- KEYS[2] = seqKey             (ticket:{eventId}:seq)
-- KEYS[3] = queueKey           (ticket:{eventId}:queue)
-- KEYS[4] = queueUserHashKey   (ticket:{eventId}:quser:{userId})
-- KEYS[5] = stockKey           (ticket:{eventId}:stock)
-- KEYS[6] = statusKey          (ticket:{eventId}:status:{userId})
-- KEYS[7] = processingMetaKey  (ticket:{eventId}:processing:{userId})
-- KEYS[8] = userKey            (ticket:{eventId}:user:{userId})
-- KEYS[9] = eventStatusKey     (ticket:{eventId}:event-status)
-- KEYS[10] = readyKey          (ticket:{eventId}:ready)
-- KEYS[11] = activeKey         (ticket:{eventId}:active)
-- KEYS[12] = admittedSeqKey    (ticket:{eventId}:admitted-seq)
-- ARGV[1] = userId             (ZSet member)
-- ARGV[2] = nowMs              (현재 epoch ms)
-- ARGV[3] = readyUntilMs
-- ARGV[4] = maxConcurrent
--
-- 반환값: {status, queuePosition, requestId, acceptedAt}
-- status: WAITING | ADMITTED | SUCCESS | SOLD_OUT | ALREADY | PROCESSING | NONE
-- queuePosition: WAITING일 때 1-indexed, 그 외 0

local function response(status, queuePosition, requestId, acceptedAt)
    local rid = requestId or ""
    local accepted = acceptedAt or "0"
    return { status, queuePosition, rid, accepted }
end

local max_concurrent = tonumber(ARGV[4])

local function can_admit_now(stock_num)
    if not max_concurrent or max_concurrent <= 0 then
        return false
    end
    local ready_count = redis.call("ZCARD", KEYS[10])
    local active_count = redis.call("ZCARD", KEYS[11])
    local occupied = ready_count + active_count
    return occupied < max_concurrent and occupied < stock_num
end

local claim_status = redis.call("GET", KEYS[6])
if claim_status and claim_status ~= "" then
    if claim_status == "PROCESSING" then
        local request_id = redis.call("HGET", KEYS[7], "requestId")
        local accepted_at = redis.call("HGET", KEYS[7], "acceptedAt")
        return response("PROCESSING", 0, request_id, accepted_at)
    end
    if claim_status == "FAILED" then
        redis.call("DEL", KEYS[6])
        redis.call("DEL", KEYS[7])
    elseif claim_status == "SUCCESS" or claim_status == "SOLD_OUT" or claim_status == "ALREADY" then
        return response(claim_status, 0, "", "0")
    end
end

if redis.call("EXISTS", KEYS[8]) == 1 then
    return response("ALREADY", 0, "", "0")
end

local event_status = redis.call("GET", KEYS[9])
if not event_status or event_status == "" then
    return response("NONE", 0, "", "0")
end
if event_status == "CLOSED" then
    return response("SOLD_OUT", 0, "", "0")
end
if event_status ~= "OPEN" then
    return response("NONE", 0, "", "0")
end

local state = redis.call("HGET", KEYS[4], "state")
if not state then
    local set_result = redis.call("SET", KEYS[1], "1", "NX")
    if set_result then
        local seq = redis.call("INCR", KEYS[2])
        local stock = redis.call("GET", KEYS[5])
        local stock_num = tonumber(stock)

        -- 신규 유저는 슬롯이 비어 있으면 큐 enqueue 없이 바로 READY 승격
        if stock and stock_num and stock_num > 0 and can_admit_now(stock_num) then
            redis.call("HSET", KEYS[4],
                "state", "READY",
                "seq", tostring(seq),
                "enteredAt", ARGV[2],
                "readyAt", ARGV[2],
                "readyUntil", ARGV[3]
            )
            redis.call("ZADD", KEYS[10], ARGV[3], ARGV[1])
            redis.call("SET", KEYS[12], tostring(seq))
            return response("ADMITTED", 0, "", "0")
        end

        redis.call("ZADD", KEYS[3], seq, ARGV[1])
        redis.call("HSET", KEYS[4], "state", "WAITING", "seq", tostring(seq), "enteredAt", ARGV[2])
        if not stock or not stock_num or stock_num <= 0 then
            return response("SOLD_OUT", 0, "", "0")
        end
        return response("WAITING", 0, "", "0")
    end
    state = redis.call("HGET", KEYS[4], "state")
end

if not state then
    return response("NONE", 0, "", "0")
end

if state == "WAITING" then
    local stock = redis.call("GET", KEYS[5])
    if not stock or tonumber(stock) <= 0 then
        return response("SOLD_OUT", 0, "", "0")
    end

    local stock_num = tonumber(stock)
    if can_admit_now(stock_num) then
        local seq = redis.call("HGET", KEYS[4], "seq")
        redis.call("ZREM", KEYS[3], ARGV[1])
        redis.call("HSET", KEYS[4],
            "state", "READY",
            "readyAt", ARGV[2],
            "readyUntil", ARGV[3]
        )
        redis.call("ZADD", KEYS[10], ARGV[3], ARGV[1])
        if seq then
            redis.call("SET", KEYS[12], seq)
        end
        return response("ADMITTED", 0, "", "0")
    end

    return response("WAITING", 0, "", "0")
end

if state == "READY" or state == "ACTIVE" then
    return response("ADMITTED", 0, "", "0")
end

if state == "DONE" then
    return response("SUCCESS", 0, "", "0")
end

return response("NONE", 0, "", "0")
