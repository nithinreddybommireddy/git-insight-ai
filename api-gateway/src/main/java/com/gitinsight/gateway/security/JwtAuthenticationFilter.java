package com.gitinsight.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.security.AuthCookieNames;
import com.gitinsight.common.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global gateway filter that validates JWT tokens and enforces role-based
 * authorization at the gateway level.
 *
 * <p>Security flow:
 * <ol>
 *   <li>Strip ALL spoofable identity headers from the client request immediately.</li>
 *   <li>If the path is public, forward without JWT validation.</li>
 *   <li>For protected paths, extract and validate the JWT.</li>
 *   <li>Safely validate claims.</li>
 *   <li>Enforce role-based authorization for recruiter/admin paths.</li>
 *   <li>Add JWT-derived trusted identity headers before forwarding.</li>
 * </ol>
 *
 * <p>Token sources:
 * <ol>
 *   <li>{@code Authorization: Bearer <token>} header</li>
 *   <li>{@code gitinsight_access_token} HttpOnly cookie</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> TRUSTED_HEADERS = List.of(
            "X-User-Id", "X-User-Email", "X-User-Role", "X-Token-Type"
    );

    /**
     * Public route prefixes. Matching uses exact-segment logic:
     * {@code path.equals(prefix) || path.startsWith(prefix + "/")}
     * so that {@code /api/githubFake} does NOT match prefix {@code /api/github}.
     */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/oauth",
            "/api/github",
            "/api/ai",
            "/actuator"
    );

    /** POST AI endpoints that require JWT — not public like GET AI analysis. */
    private static final Set<String> PROTECTED_AI_POST_PREFIXES = Set.of(
            "/api/ai/commit-diff-review"
    );

    private static final String RECRUITER_PREFIX = "/api/recruiter/";
    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final Set<String> VALID_ROLES = Set.of("USER", "RECRUITER", "ADMIN");

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 1: Strip spoofable identity headers on ALL routes.
        ServerHttpRequest strippedRequest = request.mutate()
                .headers(h -> TRUSTED_HEADERS.forEach(h::remove))
                .build();

        // Step 2: Public routes — but POST to protected AI endpoints still needs JWT.
        if (isPublicRoute(path) && !isProtectedAiPost(path, request)) {
            return chain.filter(exchange.mutate().request(strippedRequest).build());
        }

        // Step 3: Extract token.
        String token = extractToken(strippedRequest);
        if (token == null) {
            return unauthorized(exchange, "Authentication required");
        }

        // Step 4: Validate token.
        if (!jwtUtil.validateToken(token)) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        // Step 5: Validate claims.
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

        if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            return unauthorized(exchange, "Invalid token type");
        }
        if (userId == null || userId <= 0) {
            return unauthorized(exchange, "Invalid token claims");
        }
        if (role == null || !VALID_ROLES.contains(role)) {
            return unauthorized(exchange, "Invalid token claims");
        }

        // Step 6: Role-based authorization.
        if (isAdminPath(path) && !"ADMIN".equals(role)) {
            return forbidden(exchange, "Access denied");
        }
        if (isRecruiterPath(path) && !"RECRUITER".equals(role) && !"ADMIN".equals(role)) {
            return forbidden(exchange, "Access denied");
        }

        // Step 7: Add trusted headers from JWT.
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
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    // ── Route classification ────────────────────────────────────────

    private boolean isPublicRoute(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /**
     * POST to specific AI endpoints (like commit-diff-review) requires JWT
     * even though GET /api/ai/** is public for developer analysis.
     */
    private boolean isProtectedAiPost(String path, ServerHttpRequest request) {
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        if (!"POST".equals(method)) {
            return false;
        }
        return PROTECTED_AI_POST_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isRecruiterPath(String path) {
        return path.startsWith(RECRUITER_PREFIX);
    }

    private boolean isAdminPath(String path) {
        return path.startsWith(ADMIN_PREFIX);
    }

    // ── Token extraction ────────────────────────────────────────────

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        HttpCookie cookie = request.getCookies().getFirst(AuthCookieNames.ACCESS);
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            return cookie.getValue();
        }
        return null;
    }

    // ── Error responses — safe JSON via ObjectMapper ────────────────

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, 401, message);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code);
        body.put("message", message);

        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
        } catch (Exception e) {
            String fallback = "{\"status\":" + code + ",\"message\":\"Error\"}";
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(fallback.getBytes(StandardCharsets.UTF_8))
            ));
        }
    }
}
