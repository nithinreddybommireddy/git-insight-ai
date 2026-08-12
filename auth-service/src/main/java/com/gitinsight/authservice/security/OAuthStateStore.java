package com.gitinsight.authservice.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-use OAuth "state" store (CSRF protection for the OAuth redirect).
 *
 * <p>When a user starts GitHub login we generate a random state, remember what
 * the browser should be redirected to afterwards, and hand GitHub the state.
 * The callback must present the same state, which we consume exactly once.
 * States expire after {@link #TTL} so a leaked/stale state cannot be reused.
 *
 * <p>In-memory is fine for a single auth-service instance. If auth-service is
 * ever scaled out, replace this with a shared store (e.g. Redis) keyed by the
 * same state value.
 */
@Component
public class OAuthStateStore {

    private static final long TTL_SECONDS = 600; // 10 minutes
    private static final int STATE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Entry> states = new ConcurrentHashMap<>();

    /** Create a new random state that later resolves to {@code redirectUri}. */
    public String create(String redirectUri) {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        states.put(state, new Entry(redirectUri, Instant.now().plusSeconds(TTL_SECONDS)));
        return state;
    }

    /**
     * Validate and consume a state. Returns the redirect target, or {@code null}
     * when the state is unknown, expired, or already used.
     */
    public String consume(String state) {
        if (state == null) {
            return null;
        }
        Entry entry = states.remove(state);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            return null;
        }
        return entry.redirectUri;
    }

    private record Entry(String redirectUri, Instant expiresAt) {
    }
}
