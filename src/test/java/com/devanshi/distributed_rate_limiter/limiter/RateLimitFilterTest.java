package com.devanshi.distributed_rate_limiter.limiter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fast unit tests for the filter: no Spring, no Redis, no Docker.
 * The RateLimiter is a Mockito fake, so we can force "allow", "deny" or "Redis down".
 * This is possible because the filter depends on the RateLimiter interface.
 */
class RateLimitFilterTest {

    private static RateLimitProperties properties(boolean failOpen) {
        return new RateLimitProperties(failOpen, "free",
                new RateLimitProperties.RuleConfig(5, 1, Algorithm.TOKEN_BUCKET), Map.of(), List.of());
    }

    private static RateLimitFilter filterWith(RateLimiter limiter, boolean failOpen) {
        RateLimitProperties props = properties(failOpen);
        return new RateLimitFilter(new RuleResolver(props), List.of(limiter), props);
    }

    private static RateLimiter fakeLimiter() {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.algorithm()).thenReturn(Algorithm.TOKEN_BUCKET);
        return limiter;
    }

    private static MockHttpServletRequest apiRequest(String clientId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (clientId != null) {
            request.addHeader("X-Client-Id", clientId);
        }
        return request;
    }

    @Test
    void allowedRequestPassesThroughWithHeaders() throws Exception {
        RateLimiter limiter = fakeLimiter();
        when(limiter.check(anyString(), any())).thenReturn(new Decision(true, 4, 0));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWith(limiter, true).doFilter(apiRequest("devanshi"), response, chain);

        assertNotNull(chain.getRequest(), "request should reach the controller");
        assertEquals("5", response.getHeader("X-RateLimit-Limit"));
        assertEquals("4", response.getHeader("X-RateLimit-Remaining"));
        verify(limiter).check("rl:tb:devanshi:/api/orders", new RateLimitRule(5, 1));
    }

    @Test
    void deniedRequestGets429AndRetryAfterInWholeSeconds() throws Exception {
        RateLimiter limiter = fakeLimiter();
        when(limiter.check(anyString(), any())).thenReturn(new Decision(false, 0, 1500));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWith(limiter, true).doFilter(apiRequest("devanshi"), response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("2", response.getHeader("Retry-After"));   // 1500 ms rounds UP to 2 s
        assertNull(chain.getRequest(), "request must not reach the controller");
    }

    @Test
    void missingClientIdGets400AndNeverCallsTheLimiter() throws Exception {
        RateLimiter limiter = fakeLimiter();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWith(limiter, true).doFilter(apiRequest(null), response, new MockFilterChain());

        assertEquals(400, response.getStatus());
        verify(limiter, never()).check(anyString(), any());
    }

    @Test
    void redisDownWithFailOpenLetsTheRequestThrough() throws Exception {
        RateLimiter limiter = fakeLimiter();
        when(limiter.check(anyString(), any())).thenThrow(new RedisConnectionFailureException("down"));
        MockFilterChain chain = new MockFilterChain();

        filterWith(limiter, true).doFilter(apiRequest("devanshi"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void redisDownWithFailClosedReturns503() throws Exception {
        RateLimiter limiter = fakeLimiter();
        when(limiter.check(anyString(), any())).thenThrow(new RedisConnectionFailureException("down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWith(limiter, false).doFilter(apiRequest("devanshi"), response, chain);

        assertEquals(503, response.getStatus());
        assertNull(chain.getRequest());
    }
}
