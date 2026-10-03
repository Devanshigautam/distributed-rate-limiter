package com.devanshi.distributed_rate_limiter.limiter;

/**
 * A limit, e.g. capacity 10 and refill 1 token per second.
 *
 * @param capacity        bucket size: the biggest burst allowed
 * @param refillPerSecond tokens added back every second
 */
public record RateLimitRule(long capacity, double refillPerSecond) {
}
