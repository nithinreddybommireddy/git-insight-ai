package com.gitinsight.gateway.security;

import com.gitinsight.common.security.AuthCookieNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that selectively removes the refresh-token cookie for non-auth
 * downstream services.
 *
 * <p>Spring Cloud Gateway (reactive Netty) preserves the browser's Cookie header
 * by default, so no reconstruction is needed. This filter only strips the
 * {@code gitinsight_refresh_token} cookie from the raw header string for routes
 * outside {@code /api/auth/**}, keeping the refresh token scoped to auth-service.
 *
 * <p>On auth routes the raw Cookie header is forwarded unchanged.
 */
@Component
public class CookieForwardingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CookieForwardingFilter.class);

    private static final String AUTH_ROUTE_PREFIX = "/api/auth/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Auth routes: forward all cookies unchanged (including refresh token).
        if (path.startsWith(AUTH_ROUTE_PREFIX)) {
            return chain.filter(exchange);
        }

        // Non-auth routes: strip the refresh token cookie from the raw header.
        String cookieHeader = request.getHeaders().getFirst("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return chain.filter(exchange);
        }

        String refreshName = AuthCookieNames.REFRESH;

        // Quick check: does the header contain the refresh cookie at all?
        if (!containsCookieName(cookieHeader, refreshName)) {
            return chain.filter(exchange);
        }

        // Remove the refresh cookie from the raw header string, preserving
        // all other cookies exactly as the browser sent them (including
        // encoded values, duplicates, etc.).
        String filtered = removeCookieByName(cookieHeader, refreshName);

        ServerHttpRequest mutatedRequest;
        if (filtered.isEmpty()) {
            mutatedRequest = request.mutate()
                    .headers(h -> h.remove("Cookie"))
                    .build();
        } else {
            mutatedRequest = request.mutate()
                    .headers(h -> {
                        h.remove("Cookie");
                        h.add("Cookie", filtered);
                    })
                    .build();
        }

        log.debug("Stripped refresh token cookie for non-auth path: {}", path);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run before the JWT filter so cookies are correctly scoped
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    // ── Raw cookie header manipulation ────────────────────────────

    /**
     * Check whether the raw Cookie header contains a cookie with the given name.
     * Handles both {@code name=value} and {@code name=; ...} patterns.
     */
    private static boolean containsCookieName(String header, String name) {
        // Match: "name=" at start, after "; ", or after ";"
        String needle = name + "=";
        int idx = header.indexOf(needle);
        if (idx < 0) return false;

        // Verify it's a full name match (not e.g. "foo_refresh_token=" matching "refresh_token=")
        if (idx > 0) {
            char before = header.charAt(idx - 1);
            if (before != ' ' && before != ';') {
                // Not a boundary — check further occurrences
                return containsCookieName(header.substring(idx + needle.length()), name);
            }
        }
        return true;
    }

    /**
     * Remove all occurrences of a cookie with the given name from the raw header,
     * preserving the rest of the string exactly.
     *
     * <p>A cookie entry is defined as starting at a name boundary (start of string,
     * after "; " or after ";") and ending at the next ";" or end of string. Any
     * leading space after the removed semicolon is also consumed.
     */
    private static String removeCookieByName(String header, String name) {
        String needle = name + "=";
        StringBuilder result = new StringBuilder(header);

        int searchFrom = 0;
        while (searchFrom < result.length()) {
            int idx = result.indexOf(needle, searchFrom);
            if (idx < 0) break;

            // Verify boundary: character before must be start-of-string, ';', or ' '
            if (idx > 0) {
                char before = result.charAt(idx - 1);
                if (before != ' ' && before != ';') {
                    searchFrom = idx + needle.length();
                    continue;
                }
            }

            // Find the end of this cookie entry (next ';' or end of string)
            int start = (idx > 0 && result.charAt(idx - 1) == ' ') ? idx - 1 : idx;
            int end = result.indexOf(";", idx + needle.length());
            if (end < 0) {
                // Last cookie in the header — remove from start to end
                result.delete(start, result.length());
                break;
            } else {
                // Remove including the trailing ';'
                result.delete(start, end + 1);
                // Don't advance searchFrom — the string shifted
            }
        }

        return result.toString().trim();
    }
}
