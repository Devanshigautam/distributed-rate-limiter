package com.devanshi.distributed_rate_limiter.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token bucket limiter: sends the Lua script to Redis, which does
 * "refill -> check -> take a token -> save" as one atomic step.
 */
@Component
public class TokenBucketLimiter implements RateLimiter {

    private static final String TOKENS_REQUESTED = "1";

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script;

    @SuppressWarnings("rawtypes")
    public TokenBucketLimiter(StringRedisTemplate redis, DefaultRedisScript<List> tokenBucketScript) {
        this.redis = redis;
        this.script = tokenBucketScript;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decision check(String key, RateLimitRule rule) {
        List<Long> result = redis.execute(
                script,
                List.of(key),                                // KEYS[1]
                String.valueOf(rule.capacity()),             // ARGV[1]
                String.valueOf(rule.refillPerSecond()),      // ARGV[2]
                TOKENS_REQUESTED);                           // ARGV[3]

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long retryAfterMs = result.get(2);
        return new Decision(allowed, remaining, retryAfterMs);
    }
}
