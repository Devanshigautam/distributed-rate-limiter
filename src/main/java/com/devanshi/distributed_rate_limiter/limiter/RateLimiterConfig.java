package com.devanshi.distributed_rate_limiter.limiter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Wiring: turns on the "ratelimit:" settings and loads the Lua scripts once at startup.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiterConfig {

    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> tokenBucketScript() {
        return script("scripts/token_bucket.lua");
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> slidingWindowScript() {
        return script("scripts/sliding_window.lua");
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
