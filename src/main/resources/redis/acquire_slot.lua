-- KEYS[1] = activeKey  (ticket:{eventId}:active)
-- ARGV[1] = nowMs      (현재 epoch ms — 만료 정리 기준)
-- ARGV[2] = maxSlots   (최대 동시 슬롯 수)
-- ARGV[3] = userId     (추가할 멤버)
-- ARGV[4] = expiryMs   (score = 입장 시각 + TTL ms)

-- 1단계: 만료된 슬롯 정리 (score <= nowMs)
redis.call("ZREMRANGEBYSCORE", KEYS[1], "0", ARGV[1])

-- 2단계: 현재 슬롯 수 확인
local count = redis.call("ZCARD", KEYS[1])
if count >= tonumber(ARGV[2]) then
    return 0  -- 슬롯 꽉 참
end

-- 3단계: 슬롯 등록 (ZREMRANGEBYSCORE ~ ZADD 사이 끼어들 수 없음)
redis.call("ZADD", KEYS[1], ARGV[4], ARGV[3])
return 1  -- 성공
