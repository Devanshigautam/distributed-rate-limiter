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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE headline test: 50 threads fire 1,000 requests at the same bucket
 * (limit 100) at the same moment. If the check were not atomic, two threads
 * could spend the same token and more than 100 would get through.
 * Repeated 20 times: it must pass every time.
 */
@SpringBootTest
class ConcurrencyTest extends RedisTestBase {

    private static final int THREADS = 50;
    private static final int REQUESTS = 1_000;
    private static final int LIMIT = 100;

    @Autowired
    private TokenBucketLimiter limiter;

    @RepeatedTest(20)
    void exactlyTheLimitIsAllowedUnderContention() throws Exception {
        String key = "test:race:" + UUID.randomUUID();
        RateLimitRule rule = new RateLimitRule(LIMIT, 0.001);   // refill so slow it can't affect the count

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < REQUESTS; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();                       // every thread waits here...
                if (limiter.check(key, rule).allowed()) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }

        startGun.countDown();                           // ...then all go at once
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(LIMIT, allowed.get(), "exactly " + LIMIT + " of " + REQUESTS + " should be allowed");
    }
}
