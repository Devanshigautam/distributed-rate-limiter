package com.devanshi.distributed_rate_limiter.limiter;

import com.devanshi.distributed_rate_limiter.RedisTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token bucket against a REAL Redis: burst, deny, refill.
 */
@SpringBootTest
class TokenBucketLimiterTest extends RedisTestBase {

    @Autowired
    private TokenBucketLimiter limiter;

    @Test
    void burstOfTenAllowedEleventhDeniedThenRefills() throws InterruptedException {
        String key = "test:burst:" + UUID.randomUUID();
        RateLimitRule rule = new RateLimitRule(10, 1);   // bucket 10, refill 1 token/sec

        for (int i = 1; i <= 10; i++) {
            assertTrue(limiter.check(key, rule).allowed(), "request " + i + " should be allowed");
        }

        Decision eleventh = limiter.check(key, rule);
        assertFalse(eleventh.allowed(), "11th request should be denied");
        assertEquals(0, eleventh.remaining());
        assertTrue(eleventh.retryAfterMs() > 0 && eleventh.retryAfterMs() <= 1000,
                "retry-after should be at most 1 second, was " + eleventh.retryAfterMs());

        Thread.sleep(1100);   // wait for 1 token to refill
        assertTrue(limiter.check(key, rule).allowed(), "should be allowed again after refill");
    }

    @Test
    void eachClientHasItsOwnBucket() {
        RateLimitRule rule = new RateLimitRule(1, 0.001);
        String devanshi = "test:own:devanshi:" + UUID.randomUUID();
        String priya = "test:own:priya:" + UUID.randomUUID();

        assertTrue(limiter.check(devanshi, rule).allowed());
        assertFalse(limiter.check(devanshi, rule).allowed());   // devanshi is empty...
        assertTrue(limiter.check(priya, rule).allowed());       // ...but priya is not affected
    }
}
