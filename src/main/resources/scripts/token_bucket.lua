-- Token bucket, run atomically inside Redis.
-- KEYS[1] = bucket key, e.g. rl:tb:devanshi:/api/orders
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = tokens requested (usually 1)
-- Returns {allowed (1/0), tokens remaining, retry after in ms}

local capacity  = tonumber(ARGV[1])
local rate      = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

-- One shared clock (Redis server time) so pod clocks can't disagree
local t   = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

-- Read the bucket; a brand-new user starts with a full bucket
local data   = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1]) or capacity
local ts     = tonumber(data[2]) or now

-- Refill for the time that has passed, but never above capacity
local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + elapsed * rate / 1000)

-- Take a token if there is enough
local allowed = 0
if tokens >= requested then
  tokens  = tokens - requested
  allowed = 1
end

-- Save the bucket and let it expire if the user goes quiet
redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate * 1000) * 2)

-- How long until enough tokens are back
local retry_ms = 0
if allowed == 0 then
  retry_ms = math.ceil((requested - tokens) * 1000 / rate)
end

return {allowed, math.floor(tokens), retry_ms}
