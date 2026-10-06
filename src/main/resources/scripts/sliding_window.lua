-- Sliding window log, run atomically inside Redis.
-- Keeps one sorted-set entry per allowed request (score = time in ms)
-- and allows a request only if fewer than "limit" happened in the last window.
-- KEYS[1] = window key, e.g. rl:sw:priya:/api/orders
-- ARGV[1] = limit (max requests per window)
-- ARGV[2] = window length in ms
-- ARGV[3] = unique id for this request (so two requests in the same ms don't merge)
-- Returns {allowed (1/0), requests left in this window, retry after in ms}

local limit  = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

-- One shared clock (Redis server time)
local t   = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

-- Forget requests that slid out of the window
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)

-- How many requests are still inside the window?
local count = redis.call('ZCARD', KEYS[1])

local allowed  = 0
local retry_ms = 0
if count < limit then
  -- Unique member: identical members would silently overwrite each other
  redis.call('ZADD', KEYS[1], now, now .. '-' .. ARGV[3])
  count   = count + 1
  allowed = 1
else
  -- Wait until the oldest request leaves the window
  local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
  retry_ms = tonumber(oldest[2]) + window - now
  if retry_ms < 1 then retry_ms = 1 end
end

-- Drop the whole key if the client goes quiet for a full window
redis.call('PEXPIRE', KEYS[1], window)

return {allowed, limit - count, retry_ms}
