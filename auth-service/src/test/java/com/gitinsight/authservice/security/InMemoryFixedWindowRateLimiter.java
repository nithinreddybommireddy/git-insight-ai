package com.gitinsight.authservice.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link FixedWindowRateLimiter} for tests — mirrors the Redis
 * semantics (per-key counter that resets after the window) without a server.
 */
public class InMemoryFixedWindowRateLimiter implements FixedWindowRateLimiter {

    private final long windowMs;
    private final Map<String, Entry> counters = new ConcurrentHashMap<>();

    public InMemoryFixedWindowRateLimiter(long windowMs) {
        this.windowMs = windowMs;
    }

    @Override
    public long increment(String key) {
        long now = System.currentTimeMillis();
        return counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMs) {
                return new Entry(now, 1);
            }
            return new Entry(existing.start, existing.count + 1);
        }).count;
    }

    private record Entry(long start, long count) {
    }
}
