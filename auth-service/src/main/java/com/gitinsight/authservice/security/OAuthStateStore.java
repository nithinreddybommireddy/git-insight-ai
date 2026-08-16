package com.gitinsight.authservice.security;

/**
 * Single-use OAuth "state" store (CSRF protection for the GitHub OAuth
 * redirect). When a user starts GitHub login we generate a random state,
 * remember where the browser should be redirected afterwards, and hand GitHub
 * the state. The callback must present the same state, which we consume
 * exactly once. States expire after a short TTL so a leaked/stale state
 * cannot be reused.
 *
 * <p>Production uses the Redis-backed implementation so states survive
 * restarts and are shared across auth-service instances. Tests inject an
 * in-memory fake.
 */
public interface OAuthStateStore {

    /** Create a new random state that later resolves to {@code redirectUri}. */
    String create(String redirectUri);

    /**
     * Validate and consume a state. Returns the redirect target, or {@code null}
     * when the state is unknown, expired, or already used.
     */
    String consume(String state);
}
