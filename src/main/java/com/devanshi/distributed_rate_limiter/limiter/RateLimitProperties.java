package com.devanshi.distributed_rate_limiter.limiter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * Everything under "ratelimit:" in application.yaml, loaded into Java.
 */
@Validated
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean failOpen,
        @DefaultValue("free") String defaultTier,
        @NotNull @Valid RuleConfig defaultRule,
        Map<String, String> clients,
        List<@Valid EndpointRule> rules) {

    public RateLimitProperties {
        clients = clients == null ? Map.of() : clients;
        rules = rules == null ? List.of() : rules;
    }

    /** A capacity + refill pair, as written in the YAML. */
    public record RuleConfig(@Positive long capacity, @Positive double refillPerSecond) {

        public RateLimitRule toRule() {
            return new RateLimitRule(capacity, refillPerSecond);
        }
    }

    /** One line of the "rules:" list: tier + endpoint + limit. */
    public record EndpointRule(
            @NotBlank String tier,
            @NotBlank String endpoint,
            @Positive long capacity,
            @Positive double refillPerSecond) {

        public RateLimitRule toRule() {
            return new RateLimitRule(capacity, refillPerSecond);
        }
    }
}
