package com.gitinsight.common.security;

/**
 * Cookie names for the HttpOnly token transport.
 *
 * <p>auth-service SETS these cookies on login/register/refresh and the GitHub
 * OAuth callback; github-service and auth-service both READ the access cookie
 * through the shared {@link JwtAuthFilter}. Keeping the names in one place
 * guarantees the two services can never drift apart.
 *
 * <p>Tokens travel only in HttpOnly cookies (never in URL query strings, which
 * leak through history/referrers/proxy logs), and the frontend stores nothing
 * in localStorage.
 */
public final class AuthCookieNames {

    /** HttpOnly cookie carrying the short-lived access JWT. */
    public static final String ACCESS = "gitinsight_access_token";

    /** HttpOnly cookie carrying the long-lived refresh JWT (exchanged at /refresh only). */
    public static final String REFRESH = "gitinsight_refresh_token";

    private AuthCookieNames() {
    }
}
