package com.devanshi.distributed_rate_limiter.limiter;

import com.devanshi.distributed_rate_limiter.RedisTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sliding window against a REAL Redis.
 */
@SpringBootTest
class SlidingWindowLimiterTest extends RedisTestBase {

    @Autowired
    private SlidingWindowLimiter limiter;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void limitPerWindowThenAllowedAgainWhenWindowSlides() throws InterruptedException {
        String key = "test:sw:" + UUID.randomUUID();
        RateLimitRule rule = new RateLimitRule(5, 5, Algorithm.SLIDING_WINDOW);   // 5 requests per 1-second window

        for (int i = 1; i <= 5; i++) {
            assertTrue(limiter.check(key, rule).allowed(), "request " + i + " should be allowed");
        }

        Decision sixth = limiter.check(key, rule);
        assertFalse(sixth.allowed(), "6th request in the window should be denied");
        assertEquals(0, sixth.remaining());
        assertTrue(sixth.retryAfterMs() > 0 && sixth.retryAfterMs() <= 1000,
                "retry-after should be at most the window, was " + sixth.retryAfterMs());

        Thread.sleep(1100);   // the whole window slides past
        assertTrue(limiter.check(key, rule).allowed(), "should be allowed again after the window");
    }

    @Test
    void storesOneEntryPerAllowedRequestAndExpires() {
        String key = "test:sw:mem:" + UUID.randomUUID();
        RateLimitRule rule = new RateLimitRule(50, 25, Algorithm.SLIDING_WINDOW);  // 50 per 2 seconds

        for (int i = 0; i < 30; i++) {
            limiter.check(key, rule);
        }

        // memory grows with requests: one sorted-set entry each (token bucket keeps just 2 fields)
        assertEquals(30L, redis.opsForZSet().zCard(key));
        Long ttl = redis.getExpire(key);
        assertTrue(ttl != null && ttl > 0, "key should expire when the client goes quiet");
    }
}
