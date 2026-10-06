package com.devanshi.distributed_rate_limiter.limiter;

/**
 * A limit, e.g. capacity 10 and refill 1 token per second, using the token bucket.
 *
 * @param capacity        token bucket: bucket size (biggest burst).
 *                        sliding window: max requests per window.
 * @param refillPerSecond the sustained rate. For the sliding window the window length is
 *                        capacity / refillPerSecond seconds, so both algorithms allow the same average rate.
 * @param algorithm       which algorithm enforces this rule
 */
public record RateLimitRule(long capacity, double refillPerSecond, Algorithm algorithm) {

    public RateLimitRule {
        algorithm = algorithm == null ? Algorithm.TOKEN_BUCKET : algorithm;
    }

    /** Shortcut for a token bucket rule. */
    public RateLimitRule(long capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, Algorithm.TOKEN_BUCKET);
    }

    /** Sliding window length in milliseconds (capacity requests per window). */
    public long windowMs() {
        return Math.max(1, Math.round(capacity / refillPerSecond * 1000));
    }
}
