package com.gitinsight.authservice.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link OAuthStateStore} for tests — mirrors the Redis semantics
 * (single-use consume, expiry) without a server.
 */
public class InMemoryOAuthStateStore implements OAuthStateStore {

    private static final long TTL_SECONDS = 600;

    private final Map<String, Entry> states = new ConcurrentHashMap<>();

    @Override
    public String create(String redirectUri) {
        String state = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID();
        states.put(state, new Entry(redirectUri, Instant.now().plusSeconds(TTL_SECONDS)));
        return state;
    }

    @Override
    public String consume(String state) {
        if (state == null) {
            return null;
        }
        Entry entry = states.remove(state);
        if (entry == null || entry.expiresAt.isBefore(Instant.now())) {
            return null;
        }
        return entry.redirectUri;
    }

    private record Entry(String redirectUri, Instant expiresAt) {
    }
}
