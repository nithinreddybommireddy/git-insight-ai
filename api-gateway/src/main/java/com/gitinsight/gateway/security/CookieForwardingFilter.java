package com.gitinsight.gateway.security;

import com.gitinsight.common.security.AuthCookieNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Global filter that selectively controls cookie forwarding to downstream services.
 *
 * <p>Spring Cloud Gateway (reactive Netty) preserves the browser's Cookie header
 * by default. However, for security we must NOT forward the refresh token cookie
 * to non-auth downstream services — the refresh token should only be readable
 * by auth-service (for {@code /api/auth/refresh}).
 *
 * <p>This filter:
 * <ul>
 *   <li>On auth routes ({@code /api/auth/**}): forwards all cookies unchanged.</li>
 *   <li>On non-auth routes: rebuilds the Cookie header excluding the refresh token
 *       cookie, preventing accidental exposure to GitHub Service, Analytics, etc.</li>
 * </ul>
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

        // Non-auth routes: check if the refresh token cookie is present and remove it.
        String refreshCookieName = AuthCookieNames.REFRESH;
        List<HttpCookie> allCookies = request.getCookies().values().stream()
                .flatMap(List::stream)
                .toList();

        boolean hasRefreshCookie = allCookies.stream()
                .anyMatch(c -> refreshCookieName.equals(c.getName()));

        if (!hasRefreshCookie) {
            // No refresh cookie present — nothing to do.
            return chain.filter(exchange);
        }

        // Rebuild the Cookie header excluding the refresh token.
        List<String> cookieParts = new ArrayList<>();
        for (HttpCookie cookie : allCookies) {
            if (!refreshCookieName.equals(cookie.getName()) && cookie.getValue() != null) {
                cookieParts.add(cookie.getName() + "=" + cookie.getValue());
            }
        }

        ServerHttpRequest mutatedRequest;
        if (!cookieParts.isEmpty()) {
            String cookieHeader = String.join("; ", cookieParts);
            mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("Cookie");
                        headers.add("Cookie", cookieHeader);
                    })
                    .build();
        } else {
            // All cookies were the refresh token — remove the Cookie header entirely.
            mutatedRequest = request.mutate()
                    .headers(headers -> headers.remove("Cookie"))
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
}
