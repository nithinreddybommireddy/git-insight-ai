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
import java.util.Set;

/**
 * Global gateway filter that validates JWT tokens and enforces role-based
 * authorization at the gateway level.
 *
 * <p>Security flow:
 * <ol>
 *   <li>Strip ALL spoofable identity headers from the client request immediately.
 *       This prevents header injection even on public routes.</li>
 *   <li>If the path is public, forward the stripped request without JWT validation.</li>
 *   <li>For protected paths, extract and validate the JWT.</li>
 *   <li>Safely validate claims (subject must be numeric, role must be valid,
 *       token type must be "access").</li>
 *   <li>For role-protected paths ({@code /api/recruiter/**}, {@code /api/admin/**}),
 *       enforce the required role.</li>
 *   <li>Add JWT-derived trusted identity headers before forwarding.</li>
 * </ol>
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

    /** Headers the gateway derives from JWT — strip any client-supplied values first. */
    private static final List<String> TRUSTED_HEADERS = List.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Role",
            "X-Token-Type"
    );

    /**
     * Public route prefixes. Each entry must end with "/" or be a complete path
     * segment to prevent prefix confusion (e.g. "/api/auth/me" must NOT match
     * "/api/auth/logout").
     *
     * <p>The matching uses {@code path.startsWith(prefix)} so:
     * <ul>
     *   <li>{@code /api/auth/login} matches prefix {@code /api/auth/login}</li>
     *   <li>{@code /api/auth/login/foo} also matches (explicitly intentional for OAuth sub-paths)</li>
     *   <li>{@code /api/auth/me} does NOT match any of the login/register/refresh/logout prefixes</li>
     * </ul>
     */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/oauth",
            "/api/github",          // public GitHub analysis (search results, org analytics, dev score)
            "/api/ai",              // public AI status
            "/actuator"
    );

    /** Paths that require the RECRUITER or ADMIN role. */
    private static final String RECRUITER_PREFIX = "/api/recruiter/";

    /** Paths that require the ADMIN role. */
    private static final String ADMIN_PREFIX = "/api/admin/";

    /** Valid roles accepted from JWT claims. */
    private static final Set<String> VALID_ROLES = Set.of("USER", "RECRUITER", "ADMIN");

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 1: ALWAYS strip spoofable identity headers, even on public routes.
        ServerHttpRequest strippedRequest = request.mutate()
                .headers(headers -> {
                    for (String header : TRUSTED_HEADERS) {
                        headers.remove(header);
                    }
                })
                .build();

        // Step 2: Public routes — forward the stripped request without JWT validation.
        if (isPublicRoute(path)) {
            return chain.filter(exchange.mutate().request(strippedRequest).build());
        }

        // Step 3: Extract token from Authorization header or cookie.
        String token = extractToken(strippedRequest);
        if (token == null) {
            log.debug("No JWT token for protected path: {}", path);
            return unauthorized(exchange, "Authentication required");
        }

        // Step 4: Validate token signature and expiry.
        if (!jwtUtil.validateToken(token)) {
            log.debug("Invalid JWT for path: {}", path);
            return unauthorized(exchange, "Invalid or expired token");
        }

        // Step 5: Safely extract and validate claims.
        String tokenType;
        String role;
        Long userId;
        String email;
        try {
            tokenType = jwtUtil.getTokenType(token);
            role = jwtUtil.getRoleFromToken(token);
            userId = jwtUtil.getUserIdFromToken(token);
            email = jwtUtil.getEmailFromToken(token);
        } catch (Exception e) {
            log.warn("Malformed JWT claims for path {}: {}", path, e.getMessage());
            return unauthorized(exchange, "Invalid token claims");
        }

        // Token type must be "access" — refresh tokens cannot authorize API requests.
        if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            log.debug("Non-access token rejected for path: {}", path);
            return unauthorized(exchange, "Invalid token type");
        }

        // Subject must be a valid numeric user ID.
        if (userId == null || userId <= 0) {
            log.warn("Invalid subject in JWT for path: {}", path);
            return unauthorized(exchange, "Invalid token claims");
        }

        // Role must exist and be a known role.
        if (role == null || !VALID_ROLES.contains(role)) {
            log.warn("Invalid or missing role in JWT for path: {}", path);
            return unauthorized(exchange, "Invalid token claims");
        }

        // Step 6: Enforce role-based authorization for protected paths.
        if (isAdminPath(path) && !"ADMIN".equals(role)) {
            log.debug("Non-ADMIN role '{}' rejected for admin path: {}", role, path);
            return forbidden(exchange, "Access denied");
        }
        if (isRecruiterPath(path) && !"RECRUITER".equals(role) && !"ADMIN".equals(role)) {
            log.debug("Non-recruiter role '{}' rejected for recruiter path: {}", role, path);
            return forbidden(exchange, "Access denied");
        }

        // Step 7: Add JWT-derived trusted identity headers.
        ServerHttpRequest authenticatedRequest = strippedRequest.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Email", email != null ? email : "")
                .header("X-User-Role", role)
                .header("X-Token-Type", tokenType)
                .build();

        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run early — after CORS but before routing
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    // ── Route classification ────────────────────────────────────────

    private boolean isPublicRoute(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isRecruiterPath(String path) {
        return path.startsWith(RECRUITER_PREFIX);
    }

    private boolean isAdminPath(String path) {
        return path.startsWith(ADMIN_PREFIX);
    }

    // ── Token extraction ────────────────────────────────────────────

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

    // ── Error responses ─────────────────────────────────────────────

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(
                        String.format("{\"status\":401,\"message\":\"%s\"}", message).getBytes()
                )
        ));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json");
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(
                        String.format("{\"status\":403,\"message\":\"%s\"}", message).getBytes()
                )
        ));
    }
}
