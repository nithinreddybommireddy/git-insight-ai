package com.gitinsight.authservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Redis-backed {@link OAuthStateStore}.
 *
 * <p>{@code create} stores the redirect target under the random state with a
 * 10-minute TTL; {@code consume} uses {@code GETDEL} so the state is removed
 * atomically — a replay of the same state is always rejected, even across
 * concurrent requests or multiple auth-service instances.
 *
 * <p>Fail-closed by design: if Redis is unreachable, {@code consume} returns
 * {@code null} and the OAuth callback rejects the attempt (the login simply
 * fails rather than proceeding without CSRF protection).
 */
@Component
public class RedisOAuthStateStore implements OAuthStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisOAuthStateStore.class);
    private static final String KEY_PREFIX = "oauth:state:";
    private static final long TTL_SECONDS = 600; // 10 minutes
    private static final int STATE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;

    public RedisOAuthStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String create(String redirectUri) {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        try {
            redisTemplate.opsForValue()
                    .set(KEY_PREFIX + state, redirectUri, Duration.ofSeconds(TTL_SECONDS));
            return state;
        } catch (Exception e) {
            log.error("Redis unavailable — OAuth state cannot be stored, login will fail closed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String consume(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
        } catch (Exception e) {
            log.error("Redis unavailable — OAuth state cannot be validated, login will fail closed: {}", e.getMessage());
            return null;
        }
    }
}
