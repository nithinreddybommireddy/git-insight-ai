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
 * <p>Availability policy: if Redis is unreachable the limiter returns
 * {@code 0} ("no usage recorded") so the AI surface stays usable and the
 * outage is loud in the logs. AI spend protection is best-effort here — a
 * production gateway should also apply network-level rate limiting. This
 * mirrors the auth-service limiter's fail-open policy so a Redis blip never
 * becomes a self-inflicted outage.
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
        try {
            Long count = redisTemplate.execute(
                    INCR_WINDOW, List.of(KEY_PREFIX + key), String.valueOf(WINDOW_SECONDS));
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.error("Redis rate limiter unavailable — allowing request (fail open): {}", e.getMessage());
            return 0L;
        }
    }
}
