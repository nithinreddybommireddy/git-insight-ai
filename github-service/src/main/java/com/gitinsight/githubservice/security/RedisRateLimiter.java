package com.gitinsight.githubservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis-backed fixed-window rate limiter used to protect the AI endpoints.
 *
 * <p>One key per client (IP + endpoint) with a 60-second TTL: the Lua script
 * increments atomically and sets the expiry on first use, so budgets are shared
 * across github-service instances.
 *
 * <p>Availability policy: {@link #tryIncrement} returns {@code null} when
 * Redis is unreachable so callers can fall back to a local in-memory limiter
 * instead of silently allowing unlimited traffic. Gemini spend protection must
 * never fail open — {@code increment} (used by callers without a fallback)
 * keeps the historical {@code 0} behavior for availability-sensitive paths.
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "rl:";
    private static final long WINDOW_SECONDS = 60L;

    private static final RedisScript<Long> INCR_WINDOW = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
            + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
            + "return c",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Returns the request count in the current window for {@code key} (1 = first request). */
    public long increment(String key) {
        Long count = tryIncrement(key);
        return count == null ? 0L : count;
    }

    /**
     * Like {@link #increment} but reports Redis failure as {@code null} so the
     * caller can enforce a local fallback budget instead of failing open.
     */
    public Long tryIncrement(String key) {
        try {
            Long count = redisTemplate.execute(
                    INCR_WINDOW, List.of(KEY_PREFIX + key), String.valueOf(WINDOW_SECONDS));
            return count;
        } catch (Exception e) {
            log.error("Redis rate limiter unavailable — caller must enforce a local fallback: {}", e.getMessage());
            return null;
        }
    }
}
