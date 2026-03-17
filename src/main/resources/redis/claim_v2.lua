-- KEYS[1] = userKey
-- KEYS[2] = stockKey
-- KEYS[3] = statusKey
-- KEYS[4] = queueUserHashKey
-- KEYS[5] = activeKey
-- KEYS[6] = dedupKey
-- ARGV[1] = statusAlready
-- ARGV[2] = statusSoldOut
-- ARGV[3] = statusSuccess
-- ARGV[4] = userClaimedValue
-- ARGV[5] = codeAlready
-- ARGV[6] = codeSoldOut
-- ARGV[7] = codeSuccess
-- ARGV[8] = nowMs
-- ARGV[9] = codeNotActive
-- ARGV[10] = codeExpiredActive
-- ARGV[11] = userId

local userKey = KEYS[1]
local stockKey = KEYS[2]
local statusKey = KEYS[3]
local queueUserHashKey = KEYS[4]
local activeKey = KEYS[5]
local dedupKey = KEYS[6]

local statusAlready = ARGV[1]
local statusSoldOut = ARGV[2]
local statusSuccess = ARGV[3]
local userClaimedValue = ARGV[4]

local codeAlready = tonumber(ARGV[5])
local codeSoldOut = tonumber(ARGV[6])
local codeSuccess = tonumber(ARGV[7])
local nowMs = tonumber(ARGV[8])
local codeNotActive = tonumber(ARGV[9])
local codeExpiredActive = tonumber(ARGV[10])
local userId = ARGV[11]

local queueState = redis.call("HGET", queueUserHashKey, "state")
local activeUntil = tonumber(redis.call("HGET", queueUserHashKey, "activeUntil"))
local activeLeaseScore = redis.call("ZSCORE", activeKey, userId)
local activeLeaseUntil = tonumber(activeLeaseScore)

if queueState ~= "ACTIVE" or activeUntil == nil or activeUntil < nowMs
    or activeLeaseScore == false or activeLeaseUntil == nil or activeLeaseUntil < nowMs then
    if queueState == "ACTIVE" then
        redis.call("HSET", queueUserHashKey, "state", "EXPIRED", "expiredAt", ARGV[8])
        redis.call("ZREM", activeKey, userId)
        redis.call("DEL", dedupKey)
        return { codeExpiredActive, -1 }
    end
    return { codeNotActive, -1 }
end

if redis.call("EXISTS", userKey) == 1 then
    redis.call("SET", statusKey, statusAlready)
    return { codeAlready, -1 }
end

local stockValue = redis.call("GET", stockKey)
local stock = tonumber(stockValue)
if stock == nil or stock <= 0 then
    redis.call("SET", statusKey, statusSoldOut)
    return { codeSoldOut, -1 }
end

local remaining = redis.call("DECR", stockKey)
redis.call("SET", userKey, userClaimedValue)
redis.call("SET", statusKey, statusSuccess)

return { codeSuccess, remaining }
