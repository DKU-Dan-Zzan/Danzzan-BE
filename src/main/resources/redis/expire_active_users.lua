-- 만료된 ACTIVE 유저를 원자적으로 처리 (claim_v2.lua 경쟁 조건 방지)
--
-- KEYS[1] = activeKey         (ticket:{eventId}:active)
-- ARGV[1] = queueUserPrefix   (ticket:{eventId}:quser:)
-- ARGV[2] = dedupKeyPrefix    (ticket:{eventId}:dedup:)
-- ARGV[3] = nowMs             (현재 epoch ms, 문자열)
-- ARGV[4] = batchLimit        (한 번에 처리할 최대 개수)
--
-- 반환값: 만료 처리된 userId 배열
--
-- 핵심 보장:
--   ZRANGEBYSCORE 이후 claim_v2.lua가 먼저 실행돼 state=DONE으로 바뀐 경우,
--   HGET state != 'ACTIVE' 이므로 EXPIRED 덮어쓰기를 건너뜀.

local active_key   = KEYS[1]
local user_prefix  = ARGV[1]
local dedup_prefix = ARGV[2]
local now_ms       = ARGV[3]
local batch_limit  = tonumber(ARGV[4]) or 200

if batch_limit < 1 then
    batch_limit = 1
end

local candidates = redis.call('ZRANGEBYSCORE', active_key, 0, now_ms, 'LIMIT', 0, batch_limit)
local expired = {}

for _, user_id in ipairs(candidates) do
    local hash_key = user_prefix .. user_id
    local state    = redis.call('HGET', hash_key, 'state')

    if state == 'ACTIVE' then
        -- 아직 ACTIVE → 원자적으로 EXPIRED 전이
        redis.call('HSET', hash_key, 'state', 'EXPIRED', 'expiredAt', now_ms)
        redis.call('ZREM', active_key, user_id)
        redis.call('DEL',  dedup_prefix .. user_id)
        table.insert(expired, user_id)
    else
        -- DONE / EXPIRED / CANCELLED: ZSet 잔재만 정리
        redis.call('ZREM', active_key, user_id)
    end
end

return expired
