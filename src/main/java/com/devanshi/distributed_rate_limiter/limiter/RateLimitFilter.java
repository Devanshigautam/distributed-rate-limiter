package com.devanshi.distributed_rate_limiter.limiter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs before every /api/** request and decides: let it through, or reply 429.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    static final String CLIENT_HEADER = "X-Client-Id";

    private final RuleResolver ruleResolver;
    private final Map<Algorithm, RateLimiter> limiters = new EnumMap<>(Algorithm.class);
    private final RateLimitProperties properties;
    private final MeterRegistry meters;

    /** Spring passes in every RateLimiter bean; we index them by algorithm. */
    public RateLimitFilter(RuleResolver ruleResolver, List<RateLimiter> limiters,
                           RateLimitProperties properties, MeterRegistry meters) {
        this.ruleResolver = ruleResolver;
        this.properties = properties;
        this.meters = meters;
        for (RateLimiter limiter : limiters) {
            this.limiters.put(limiter.algorithm(), limiter);
        }
    }

    /** Only limit the API; health checks (/actuator) are never limited. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 1. Who is calling?
        String clientId = request.getHeader(CLIENT_HEADER);
        if (clientId == null || clientId.isBlank()) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"error\":\"Missing X-Client-Id header\"}");
            return;
        }

        // 2. Which rule applies?
        String endpoint = request.getRequestURI();
        RateLimitRule rule = ruleResolver.resolve(clientId, endpoint);
        String key = "rl:" + rule.algorithm().keyPrefix() + ":" + clientId + ":" + endpoint;
        RateLimiter rateLimiter = limiters.get(rule.algorithm());
        if (rateLimiter == null) {
            throw new IllegalStateException("No limiter registered for " + rule.algorithm());
        }

        String tier = ruleResolver.tierFor(clientId);
        String algorithm = rule.algorithm().name().toLowerCase();

        // 3. Ask Redis, and time how long the check takes
        Decision decision;
        Timer.Sample sample = Timer.start(meters);
        try {
            decision = rateLimiter.check(key, rule);
            sample.stop(checkTimer(algorithm, "ok"));
        } catch (DataAccessException e) {
            sample.stop(checkTimer(algorithm, "error"));
            countDecision(properties.failOpen() ? "fail_open" : "fail_closed", tier, algorithm);
            if (properties.failOpen()) {
                log.warn("Redis unavailable, failing OPEN for {}", key);
                chain.doFilter(request, response);
            } else {
                log.warn("Redis unavailable, failing CLOSED for {}", key);
                writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "{\"error\":\"Rate limiter unavailable\"}");
            }
            return;
        }

        // 4. Tell the client their limit on every response
        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        countDecision(decision.allowed() ? "allowed" : "denied", tier, algorithm);

        if (decision.allowed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            writeJson(response, 429,
                    "{\"error\":\"Too Many Requests\",\"retryAfterMs\":" + decision.retryAfterMs() + "}");
        }
    }

    /**
     * ratelimit_check_seconds: how long the Redis/Lua check took.
     * The percentile histogram lets Prometheus compute p50/p95/p99 across all instances.
     */
    private Timer checkTimer(String algorithm, String outcome) {
        return Timer.builder("ratelimit.check")
                .description("Time spent running the rate limit check in Redis")
                .tag("algorithm", algorithm)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meters);
    }

    /** ratelimit_decisions_total{result, tier, algorithm}: every allow / deny / fail-open / fail-closed. */
    private void countDecision(String result, String tier, String algorithm) {
        Counter.builder("ratelimit.decisions")
                .description("Rate limit decisions")
                .tag("result", result)
                .tag("tier", tier)
                .tag("algorithm", algorithm)
                .register(meters)
                .increment();
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }
}
