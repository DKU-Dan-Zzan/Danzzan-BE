-- 만료된 READY 유저를 원자적으로 처리 (activate 경쟁 조건 방지)
--
-- KEYS[1] = readyKey          (ticket:{eventId}:ready)
-- ARGV[1] = queueUserPrefix   (ticket:{eventId}:quser:)
-- ARGV[2] = dedupKeyPrefix    (ticket:{eventId}:dedup:)
-- ARGV[3] = nowMs             (현재 epoch ms, 문자열)
--
-- 반환값: 만료 처리된 userId 배열
--
-- 핵심 보장:
--   ZRANGEBYSCORE 이후 ready_to_active.lua가 먼저 실행돼 state=ACTIVE로 바뀐 경우,
--   HGET state == 'ACTIVE' 이므로 EXPIRED 덮어쓰기를 건너뜀.

local ready_key    = KEYS[1]
local user_prefix  = ARGV[1]
local dedup_prefix = ARGV[2]
local now_ms       = ARGV[3]

local candidates = redis.call('ZRANGEBYSCORE', ready_key, 0, now_ms)
local expired = {}

for _, user_id in ipairs(candidates) do
    local hash_key = user_prefix .. user_id
    local state    = redis.call('HGET', hash_key, 'state')

    if state == 'READY' then
        -- 아직 READY → 원자적으로 EXPIRED 전이
        redis.call('HSET', hash_key, 'state', 'EXPIRED', 'expiredAt', now_ms)
        redis.call('ZREM', ready_key, user_id)
        redis.call('DEL',  dedup_prefix .. user_id)
        table.insert(expired, user_id)
    elseif state ~= 'ACTIVE' then
        -- ACTIVE 이외의 다른 상태(이미 만료·취소 등): ZSet 잔재만 정리
        redis.call('ZREM', ready_key, user_id)
    end
    -- state == 'ACTIVE': ready_to_active.lua 가 정상 처리한 유저 → 건드리지 않음
end

return expired
