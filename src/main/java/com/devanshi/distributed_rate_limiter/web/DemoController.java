package com.devanshi.distributed_rate_limiter.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * A pretend API to protect. The rate limiter sits in front of it.
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestHeader("X-Client-Id") String clientId) {
        return Map.of(
                "client", clientId,
                "orders", 3,
                "time", Instant.now().toString());
    }

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestHeader("X-Client-Id") String clientId) {
        return Map.of("message", "Hello " + clientId);
    }
}
