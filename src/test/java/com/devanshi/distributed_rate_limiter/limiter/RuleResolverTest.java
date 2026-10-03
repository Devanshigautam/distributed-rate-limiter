package com.devanshi.distributed_rate_limiter.limiter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit tests: no Spring, no Redis. Just "does the right rule get picked?"
 */
class RuleResolverTest {

    private final RuleResolver resolver = new RuleResolver(new RateLimitProperties(
            true,
            "free",
            new RateLimitProperties.RuleConfig(5, 1),
            Map.of("devanshi", "free", "priya", "premium"),
            List.of(
                    new RateLimitProperties.EndpointRule("free", "/api/orders", 10, 1),
                    new RateLimitProperties.EndpointRule("premium", "/api/orders", 100, 50))));

    @Test
    void knownClientGetsTheirTiersRule() {
        assertEquals(new RateLimitRule(100, 50), resolver.resolve("priya", "/api/orders"));
    }

    @Test
    void unknownClientFallsBackToDefaultTier() {
        assertEquals("free", resolver.tierFor("stranger"));
        assertEquals(new RateLimitRule(10, 1), resolver.resolve("stranger", "/api/orders"));
    }

    @Test
    void unknownEndpointUsesDefaultRule() {
        assertEquals(new RateLimitRule(5, 1), resolver.resolve("devanshi", "/api/unknown"));
    }
}
