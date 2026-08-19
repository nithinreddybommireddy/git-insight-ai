package com.gitinsight.gateway.security;

import com.gitinsight.common.security.AuthCookieNames;
import com.gitinsight.common.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global gateway filter that validates JWT tokens on protected routes and
 * forwards authenticated user identity to downstream services via headers.
 *
 * <p>Public routes (auth endpoints, OAuth, health) are excluded — they do
 * not require a valid token at the gateway level.
 *
 * <p>When a valid token is present, the filter extracts the user ID, email,
 * role, and token type, then adds them as forwarded headers so downstream
 * services can trust the gateway-authenticated identity.
 *
 * <p>Token sources (in priority order):
 * <ol>
 *   <li>{@code Authorization: Bearer <token>} header (API clients)</li>
 *   <li>{@code gitinsight_access_token} HttpOnly cookie (browser sessions)</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_TOKEN_TYPE = "X-Token-Type";

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/oauth",
            "/api/health",
            "/actuator"
    );

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Public routes — pass through without JWT validation
        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        // Extract token from Authorization header or cookie
        String token = extractToken(request);
        if (token == null) {
            // No token — let downstream services decide if auth is required.
            // Some endpoints (e.g. public GitHub analysis) don't need a token.
            return chain.filter(exchange);
        }

        // Validate token
        if (!jwtUtil.validateToken(token)) {
            log.debug("Invalid JWT for path: {}", path);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(
                            "{\"status\":401,\"message\":\"Invalid or expired token\"}".getBytes()
                    )
            ));
        }

        // Check token type — only access tokens authorize API requests
        String tokenType = jwtUtil.getTokenType(token);
        if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            log.debug("Non-access token rejected for path: {}", path);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(
                            "{\"status\":401,\"message\":\"Invalid token type\"}".getBytes()
                    )
            ));
        }

        // Extract claims and forward to downstream services
        Long userId = jwtUtil.getUserIdFromToken(token);
        String email = jwtUtil.getEmailFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(HEADER_USER_ID, String.valueOf(userId))
                .header(HEADER_USER_EMAIL, email != null ? email : "")
                .header(HEADER_USER_ROLE, role != null ? role : "")
                .header(HEADER_TOKEN_TYPE, tokenType)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run early in the filter chain, after CORS but before routing
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isPublicRoute(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractToken(ServerHttpRequest request) {
        // 1. Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. HttpOnly cookie
        HttpCookie cookie = request.getCookies().getFirst(AuthCookieNames.ACCESS);
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            return cookie.getValue();
        }

        return null;
    }
}
