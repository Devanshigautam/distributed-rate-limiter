package com.devanshi.distributed_rate_limiter.limiter;

import com.devanshi.distributed_rate_limiter.RedisTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative control: proves ConcurrencyTest really can catch a race.
 * This deliberately BROKEN limiter reads the token count in Java, then writes it back
 * in a second call. Under the same 50-thread race it lets more than 100 through,
 * which is exactly the bug the Lua script prevents.
 */
@SpringBootTest
class NonAtomicLimiterTest extends RedisTestBase {

    @Autowired
    private StringRedisTemplate redis;

    /** Read-then-write in Java: NOT atomic. Never do this in real code. */
    private boolean brokenCheck(String key, long capacity) {
        Object stored = redis.opsForHash().get(key, "tokens");
        long tokens = stored == null ? capacity : Long.parseLong(stored.toString());
        if (tokens < 1) {
            return false;
        }
        // another thread can read the same old value right here
        redis.opsForHash().put(key, "tokens", String.valueOf(tokens - 1));
        return true;
    }

    @Test
    void readThenWriteInJavaLetsMoreThanTheLimitThrough() throws Exception {
        int worst = 0;
        for (int attempt = 0; attempt < 5 && worst <= ConcurrencyTest.LIMIT; attempt++) {
            String key = "test:broken:" + UUID.randomUUID();
            worst = Math.max(worst, ConcurrencyTest.countAllowed(() -> brokenCheck(key, ConcurrencyTest.LIMIT)));
        }
        assertTrue(worst > ConcurrencyTest.LIMIT,
                "a non-atomic limiter should over-allow under contention, but allowed only " + worst);
    }
}
