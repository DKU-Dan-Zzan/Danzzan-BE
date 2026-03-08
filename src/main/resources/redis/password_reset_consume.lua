local requestKey = KEYS[1]
local expectedTokenHash = ARGV[1]

if redis.call("EXISTS", requestKey) == 0 then
  return {0, "REQUEST_NOT_FOUND"}
end

local verified = redis.call("HGET", requestKey, "verified")
if verified ~= "1" then
  return {0, "TOKEN_NOT_VERIFIED"}
end

local consumed = redis.call("HGET", requestKey, "consumed")
if consumed == "1" then
  return {0, "TOKEN_ALREADY_CONSUMED"}
end

local tokenHash = redis.call("HGET", requestKey, "verificationTokenHash")
if not tokenHash or tokenHash ~= expectedTokenHash then
  return {0, "INVALID_TOKEN"}
end

redis.call("HSET", requestKey, "consumed", "1")
redis.call("HDEL", requestKey, "verificationTokenHash")

local studentId = redis.call("HGET", requestKey, "studentId")
local userExists = redis.call("HGET", requestKey, "userExists")
return {1, studentId, userExists}
