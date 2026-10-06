package com.devanshi.distributed_rate_limiter.limiter;

import com.devanshi.distributed_rate_limiter.RedisTestBase;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE headline test: 50 threads fire 1,000 requests at the same key (limit 100)
 * at the same moment. If the check were not atomic, two threads could take the
 * same slot and more than 100 would get through. Repeated 20 times per algorithm.
 */
@SpringBootTest
class ConcurrencyTest extends RedisTestBase {

    static final int THREADS = 50;
    static final int REQUESTS = 1_000;
    static final int LIMIT = 100;

    @Autowired
    private TokenBucketLimiter tokenBucket;

    @Autowired
    private SlidingWindowLimiter slidingWindow;

    @RepeatedTest(20)
    void tokenBucketAllowsExactlyTheLimitUnderContention() throws Exception {
        String key = "test:race:tb:" + UUID.randomUUID();
        RateLimitRule rule = new RateLimitRule(LIMIT, 0.001, Algorithm.TOKEN_BUCKET);   // refill too slow to matter
        assertEquals(LIMIT, countAllowed(() -> tokenBucket.check(key, rule).allowed()));
    }

    @RepeatedTest(20)
    void slidingWindowAllowsExactlyTheLimitUnderContention() throws Exception {
        String key = "test:race:sw:" + UUID.randomUUID();
        RateLimitRule rule = new RateLimitRule(LIMIT, 0.001, Algorithm.SLIDING_WINDOW); // window far longer than the test
        assertEquals(LIMIT, countAllowed(() -> slidingWindow.check(key, rule).allowed()));
    }

    /**
     * Holds 50 threads at a start line, releases them together, and counts how many
     * of the 1,000 checks were allowed. Also used by NonAtomicLimiterTest.
     */
    static int countAllowed(BooleanSupplier check) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < REQUESTS; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();                       // every thread waits here...
                if (check.getAsBoolean()) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }

        startGun.countDown();                           // ...then all go at once
        try {
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return allowed.get();
    }
}
