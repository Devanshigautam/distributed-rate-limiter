package com.devanshi.distributed_rate_limiter.limiter;

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

/**
 * Runs before every /api/** request and decides: let it through, or reply 429.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    static final String CLIENT_HEADER = "X-Client-Id";

    private final RuleResolver ruleResolver;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RuleResolver ruleResolver, RateLimiter rateLimiter, RateLimitProperties properties) {
        this.ruleResolver = ruleResolver;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
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
        String key = "rl:tb:" + clientId + ":" + endpoint;

        // 3. Ask Redis
        Decision decision;
        try {
            decision = rateLimiter.check(key, rule);
        } catch (DataAccessException e) {
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

        if (decision.allowed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            writeJson(response, 429,
                    "{\"error\":\"Too Many Requests\",\"retryAfterMs\":" + decision.retryAfterMs() + "}");
        }
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }
}
