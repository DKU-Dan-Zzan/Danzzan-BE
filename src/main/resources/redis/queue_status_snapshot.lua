-- KEYS[1] = ticket:{eventId}:status:{userId}
-- KEYS[2] = ticket:{eventId}:quser:{userId}
-- KEYS[3] = ticket:{eventId}:admitted-seq
-- KEYS[4] = ticket:{eventId}:stock
--
-- Returns:
-- [1] claim status
-- [2] queue user state
-- [3] seq
-- [4] readyUntil
-- [5] admittedSeq
-- [6] stock

local claimStatus = redis.call('GET', KEYS[1]) or ''
local state = redis.call('HGET', KEYS[2], 'state') or ''
local seq = redis.call('HGET', KEYS[2], 'seq') or ''
local readyUntil = redis.call('HGET', KEYS[2], 'readyUntil') or ''
local admittedSeq = redis.call('GET', KEYS[3]) or ''
local stock = redis.call('GET', KEYS[4]) or ''

return {claimStatus, state, seq, readyUntil, admittedSeq, stock}
