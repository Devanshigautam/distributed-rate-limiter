package com.devanshi.distributed_rate_limiter.limiter;

/**
 * Anything that can decide allow / deny for a key under a rule.
 * Week 1 has one implementation (token bucket); Week 2 adds a sliding window.
 */
public interface RateLimiter {

    Decision check(String key, RateLimitRule rule);
}
