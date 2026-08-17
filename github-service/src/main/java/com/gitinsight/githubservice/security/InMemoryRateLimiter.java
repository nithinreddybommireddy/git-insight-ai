package com.gitinsight.githubservice.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-instance fixed-window rate limiter (60-second windows) used as the
 * fallback when the shared Redis limiter is unreachable.
 *
 * <p>Unlike the Redis limiter, this can never fail open: if the map itself is
 * unavailable the request is conservatively rejected. It is per-process, so
 * multi-instance deployments see per-instance budgets during a Redis outage —
 * a deliberate trade-off: a brief Redis blip must not become an unlimited
 * Gemini-spend window.
 */
@Component
public class InMemoryRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Returns the request count in the current window for {@code key} (1 = first request). */
    public long increment(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAt >= WINDOW_MILLIS) {
                return new Window(now, 1L);
            }
            existing.count++;
            return existing;
        });
        // Opportunistic cleanup so a flood of unique keys cannot grow the map forever.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startedAt >= WINDOW_MILLIS);
        }
        return w.count;
    }

    private static final class Window {
        final long startedAt;
        long count;

        Window(long startedAt, long count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
