package com.devanshi.distributed_rate_limiter.limiter;

import org.springframework.stereotype.Component;

/**
 * Picks the rule for a request: client id -> tier, then tier + endpoint -> rule.
 */
@Component
public class RuleResolver {

    private final RateLimitProperties properties;

    public RuleResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** Unknown clients get the default tier. */
    public String tierFor(String clientId) {
        return properties.clients().getOrDefault(clientId, properties.defaultTier());
    }

    /** Finds the matching rule, or falls back to the default rule. */
    public RateLimitRule resolve(String clientId, String endpoint) {
        String tier = tierFor(clientId);
        return properties.rules().stream()
                .filter(r -> r.tier().equals(tier) && r.endpoint().equals(endpoint))
                .findFirst()
                .map(RateLimitProperties.EndpointRule::toRule)
                .orElse(properties.defaultRule().toRule());
    }
}
