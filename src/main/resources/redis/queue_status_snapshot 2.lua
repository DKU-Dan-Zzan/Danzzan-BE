-- KEYS[1] = ticket:{eventId}:status:{userId}
-- KEYS[2] = ticket:{eventId}:quser:{userId}
-- KEYS[3] = ticket:{eventId}:admitted-seq
-- KEYS[4] = ticket:{eventId}:stock
--
-- Returns:
-- [1] claimStatus
-- [2] state
-- [3] seq
-- [4] readyUntil
-- [5] activeUntil
-- [6] admittedSeq
-- [7] stock
--
-- 목적:
-- 1) terminal claim 상태(PROCESSING/FAILED 포함)는 즉시 반환
-- 2) 상태별로 필요한 필드만 조회해 Redis 명령 수 최소화

local claimStatus = redis.call("GET", KEYS[1]) or ""
if claimStatus == "SUCCESS"
    or claimStatus == "SOLD_OUT"
    or claimStatus == "ALREADY"
    or claimStatus == "PROCESSING"
    or claimStatus == "FAILED" then
    return { claimStatus, "", "", "", "", "", "" }
end

local hashValues = redis.call("HMGET", KEYS[2], "state", "seq", "readyUntil", "activeUntil")
local state = hashValues[1] or ""
if state == "" then
    return { claimStatus, "", "", "", "", "", "" }
end

local seq = hashValues[2] or ""
if state == "WAITING" then
    local admittedSeq = redis.call("GET", KEYS[3]) or ""
    local stock = redis.call("GET", KEYS[4]) or ""
    return { claimStatus, state, seq, "", "", admittedSeq, stock }
end

if state == "READY" then
    local readyUntil = hashValues[3] or ""
    return { claimStatus, state, seq, readyUntil, "", "", "" }
end

if state == "ACTIVE" then
    local activeUntil = hashValues[4] or ""
    return { claimStatus, state, seq, "", activeUntil, "", "" }
end

return { claimStatus, state, seq, "", "", "", "" }
