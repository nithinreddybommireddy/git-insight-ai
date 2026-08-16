package com.gitinsight.authservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis-backed {@link FixedWindowRateLimiter}.
 *
 * <p>One key per client (IP + endpoint) with a 60-second TTL: the Lua script
 * increments atomically and sets the expiry on first use, so the window is
 * shared across auth-service instances and survives restarts.
 *
 * <p>Availability policy: if Redis is unreachable the limiter returns
 * {@code 0} ("no usage recorded") so the credential endpoints stay usable and
 * the outage is loud in the logs — a DoS guard must never become a
 * self-inflicted availability outage. Network-level rate limiting at the
 * gateway is still recommended for production.
 */
@Component
public class RedisFixedWindowRateLimiter implements FixedWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisFixedWindowRateLimiter.class);
    private static final String KEY_PREFIX = "rl:";
    private static final long WINDOW_SECONDS = 60L;

    private static final RedisScript<Long> INCR_WINDOW = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
            + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
            + "return c",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
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
