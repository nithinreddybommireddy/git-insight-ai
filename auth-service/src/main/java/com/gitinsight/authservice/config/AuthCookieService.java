package com.gitinsight.authservice.config;

import com.gitinsight.authservice.dto.AuthResponse;
import com.gitinsight.common.security.AuthCookieNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Sets the HttpOnly session cookies for the browser auth flow.
 *
 * <p>Tokens are delivered exclusively via {@code HttpOnly} cookies (never in
 * URL query strings — those leak through browser history, referrer headers,
 * proxy/access logs, and analytics). The access cookie lives for the access
 * token's lifetime, the refresh cookie for the refresh token's lifetime, and
 * both are scoped to the site root.
 *
 * <p>{@code SameSite=Lax} keeps the cookies out of cross-site requests while
 * preserving the top-level navigation that the GitHub OAuth redirect needs.
 * The {@code Secure} flag is off by default because local development runs
 * over http://localhost; production deployments behind HTTPS must set
 * {@code AUTH_COOKIE_SECURE=true}.
 */
@Component
public class AuthCookieService {

    private final boolean secure;

    public AuthCookieService(@Value("${app.auth.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    /** Build a {@code Set-Cookie} header value for the access token. */
    public String accessCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AuthCookieNames.ACCESS, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    /** Build a {@code Set-Cookie} header value for the refresh token. */
    public String refreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AuthCookieNames.REFRESH, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    /** Build a {@code Set-Cookie} header value that expires both session cookies. */
    public String clearAccessCookie() {
        return ResponseCookie.from(AuthCookieNames.ACCESS, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build()
                .toString();
    }

    public String clearRefreshCookie() {
        return ResponseCookie.from(AuthCookieNames.REFRESH, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build()
                .toString();
    }

    /** Both Set-Cookie values for a freshly minted session (header values). */
    public String[] sessionCookies(AuthResponse response, long accessMaxAgeSeconds, long refreshMaxAgeSeconds) {
        return new String[]{accessCookie(response.getToken(), accessMaxAgeSeconds),
                refreshCookie(response.getRefreshToken(), refreshMaxAgeSeconds)};
    }

    /** Both Set-Cookie values that clear a session. */
    public String[] clearSessionCookies() {
        return new String[]{clearAccessCookie(), clearRefreshCookie()};
    }
}
