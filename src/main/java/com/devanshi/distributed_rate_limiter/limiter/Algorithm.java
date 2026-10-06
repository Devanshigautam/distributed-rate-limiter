package com.devanshi.distributed_rate_limiter.limiter;

/**
 * The rate limiting algorithms available. Chosen per rule in application.yaml
 * (algorithm: token-bucket | sliding-window).
 */
public enum Algorithm {

    TOKEN_BUCKET("tb"),
    SLIDING_WINDOW("sw");

    private final String keyPrefix;

    Algorithm(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /** Short tag used in Redis keys, e.g. rl:tb:devanshi:/api/orders */
    public String keyPrefix() {
        return keyPrefix;
    }
}
