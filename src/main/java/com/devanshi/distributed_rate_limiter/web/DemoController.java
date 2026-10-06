package com.devanshi.distributed_rate_limiter.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;

/**
 * A pretend API to protect. The rate limiter sits in front of it.
 * "instance" shows which running copy answered (useful when several copies run behind a load balancer).
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    private static final String INSTANCE = instanceName();

    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestHeader("X-Client-Id") String clientId) {
        return Map.of(
                "client", clientId,
                "orders", 3,
                "instance", INSTANCE,
                "time", Instant.now().toString());
    }

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestHeader("X-Client-Id") String clientId) {
        return Map.of("message", "Hello " + clientId, "instance", INSTANCE);
    }

    /** In Docker this is the container id; on a laptop it's the computer name. */
    private static String instanceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
