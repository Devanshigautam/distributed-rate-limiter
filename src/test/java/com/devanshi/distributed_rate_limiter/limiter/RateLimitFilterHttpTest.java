package com.devanshi.distributed_rate_limiter.limiter;

import com.devanshi.distributed_rate_limiter.RedisTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end over real HTTP: the filter, the rules from application.yaml and Redis together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFilterHttpTest extends RedisTestBase {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String clientId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (clientId != null) {
            request.header("X-Client-Id", clientId);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void freeClientGets429AfterBurstWithHeaders() throws Exception {
        String client = "http-test-" + System.nanoTime();   // unknown client -> free tier -> bucket of 10

        for (int i = 1; i <= 10; i++) {
            HttpResponse<String> ok = get("/api/orders", client);
            assertEquals(200, ok.statusCode(), "request " + i);
            assertEquals("10", ok.headers().firstValue("X-RateLimit-Limit").orElseThrow());
        }

        HttpResponse<String> blocked = get("/api/orders", client);
        assertEquals(429, blocked.statusCode());
        assertEquals("0", blocked.headers().firstValue("X-RateLimit-Remaining").orElseThrow());
        assertTrue(blocked.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void premiumClientUsesSlidingWindowWithHigherLimit() throws Exception {
        for (int i = 1; i <= 15; i++) {
            HttpResponse<String> ok = get("/api/orders", "priya");   // premium -> sliding window, 100 per 2 s
            assertEquals(200, ok.statusCode(), "request " + i);
            assertEquals("100", ok.headers().firstValue("X-RateLimit-Limit").orElseThrow());
        }
    }

    @Test
    void missingClientIdIsRejected() throws Exception {
        assertEquals(400, get("/api/orders", null).statusCode());
    }

    @Test
    void healthEndpointIsNeverLimited() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertEquals(200, get("/actuator/health", null).statusCode());
        }
    }
}
