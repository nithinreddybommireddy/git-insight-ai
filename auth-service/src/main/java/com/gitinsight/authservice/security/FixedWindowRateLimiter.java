package com.gitinsight.authservice.security;

/**
 * Fixed-window counter used to blunt brute-force password guessing on the
 * credential endpoints ({@code /api/auth/login}, {@code /api/auth/register}).
 *
 * <p>Production uses the Redis-backed implementation so counts survive
 * restarts and are shared across auth-service instances behind a load
 * balancer. Tests inject an in-memory fake.
 */
public interface FixedWindowRateLimiter {

    /**
     * Atomically increments the counter for the current window of {@code key}
     * and returns the new count. The window rolls over automatically (TTL).
     *
     * @return the count within the current window; {@code 0} if the store is
     *         unavailable (callers treat this as "no usage recorded")
     */
    long increment(String key);
}
