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
 * Global filter that ensures authentication and CSRF cookies are forwarded
 * to downstream services.
 *
 * <p>Spring Cloud Gateway (reactive Netty) does not automatically forward
 * cookies from the client to the proxied service. This filter reads the
 * relevant cookies from the incoming request and re-adds them as request
 * headers/cookies so downstream services receive them.
 *
 * <p>Cookies forwarded:
 * <ul>
 *   <li>{@code gitinsight_access_token} — JWT access token (HttpOnly)</li>
 *   <li>{@code gitinsight_refresh_token} — JWT refresh token (HttpOnly)</li>
 *   <li>{@code XSRF-TOKEN} — CSRF double-submit cookie (non-HttpOnly)</li>
 * </ul>
 */
@Component
public class CookieForwardingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CookieForwardingFilter.class);

    private static final List<String> COOKIES_TO_FORWARD = List.of(
            AuthCookieNames.ACCESS,
            AuthCookieNames.REFRESH,
            "XSRF-TOKEN"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Check if cookies are already forwarded (e.g. by a previous filter)
        if (request.getHeaders().containsKey("Cookie")) {
            return chain.filter(exchange);
        }

        List<String> cookieParts = new ArrayList<>();
        for (String cookieName : COOKIES_TO_FORWARD) {
            HttpCookie cookie = request.getCookies().getFirst(cookieName);
            if (cookie != null && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                cookieParts.add(cookie.getName() + "=" + cookie.getValue());
            }
        }

        if (!cookieParts.isEmpty()) {
            String cookieHeader = String.join("; ", cookieParts);
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("Cookie", cookieHeader)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run before the JWT filter so cookies are available
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
