package com.devanshi.distributed_rate_limiter.limiter;

/**
 * Anything that can decide allow / deny for a key under a rule.
 * One implementation per algorithm: TokenBucketLimiter, SlidingWindowLimiter.
 */
public interface RateLimiter {

    /** Which algorithm this limiter implements. */
    Algorithm algorithm();

    Decision check(String key, RateLimitRule rule);
}
