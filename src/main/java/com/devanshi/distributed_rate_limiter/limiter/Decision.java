package com.devanshi.distributed_rate_limiter.limiter;

/**
 * The answer for one request.
 *
 * @param allowed      true = let the request through, false = reply 429
 * @param remaining    tokens left in the bucket after this request
 * @param retryAfterMs if denied, how many milliseconds until a token is back
 */
public record Decision(boolean allowed, long remaining, long retryAfterMs) {
}
