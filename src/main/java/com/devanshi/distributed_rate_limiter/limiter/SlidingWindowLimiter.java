package com.devanshi.distributed_rate_limiter.limiter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Sliding window log limiter: Redis keeps a sorted set of request times and the
 * Lua script allows a request only if fewer than "capacity" happened in the last window.
 * Exact, but memory grows with the number of requests in the window.
 */
@Component
public class SlidingWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script;

    @SuppressWarnings("rawtypes")
    public SlidingWindowLimiter(StringRedisTemplate redis,
                                @Qualifier("slidingWindowScript") DefaultRedisScript<List> script) {
        this.redis = redis;
        this.script = script;git add .
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.SLIDING_WINDOW;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decision check(String key, RateLimitRule rule) {
        List<Long> result = redis.execute(
                script,
                List.of(key),                                // KEYS[1]
                String.valueOf(rule.capacity()),             // ARGV[1] limit
                String.valueOf(rule.windowMs()),             // ARGV[2] window in ms
                UUID.randomUUID().toString());               // ARGV[3] unique request id

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long retryAfterMs = result.get(2);
        return new Decision(allowed, remaining, retryAfterMs);
    }
}
