package com.gitinsight.gateway.security;

import com.gitinsight.common.security.AuthCookieNames;
import com.gitinsight.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter} and {@link CookieForwardingFilter}.
 *
 * <p>Tests the filter logic directly without booting the full Spring context.
 * Uses a real JwtUtil (validation-only) with a known test secret.
 */
class GatewayFilterTests {

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-32bytes!";

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter jwtFilter;
    private CookieForwardingFilter cookieFilter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        // Use the full constructor so we can generate tokens in tests
        jwtUtil = new JwtUtil(TEST_SECRET, 900_000, 2_592_000_000L);
        jwtFilter = new JwtAuthenticationFilter(jwtUtil);
        cookieFilter = new CookieForwardingFilter();

        // Default chain that records whether it was called
        chain = exchange -> {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };
    }

    /** Build a MockServerHttpRequest with a raw Cookie header string (browser-style). */
    private static MockServerHttpRequest rawCookieRequest(String path, String cookieHeaderValue) {
        return MockServerHttpRequest.get(path)
                .header("Cookie", cookieHeaderValue)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // JwtAuthenticationFilter tests
    // ════════════════════════════════════════════════════════════════

    @Test
    void publicRoute_withoutJwt_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    @Test
    void publicRoute_withSpoofedHeaders_stripsHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/auth/register")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "999")
                .header("X-User-Email", "evil@example.com")
                .header("X-Token-Type", "access")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Capture the mutated request passed to the chain
        GatewayFilterChain captureChain = ex -> {
            ServerHttpRequest mutated = ex.getRequest();
            assertNull(mutated.getHeaders().getFirst("X-User-Role"));
            assertNull(mutated.getHeaders().getFirst("X-User-Id"));
            assertNull(mutated.getHeaders().getFirst("X-User-Email"));
            assertNull(mutated.getHeaders().getFirst("X-Token-Type"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(jwtFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void protectedRoute_withoutJwt_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/github/profile/torvalds")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedRoute_withInvalidJwt_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/github/profile/torvalds")
                .header("Authorization", "Bearer invalid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedRoute_withMalformedJwt_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/github/profile/torvalds")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.invalid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedRoute_withRefreshToken_returns401() {
        String refreshToken = jwtUtil.generateRefreshToken(1L);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/github/profile/torvalds")
                .header("Authorization", "Bearer " + refreshToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void recruiterRoute_withUserRole_returns403() {
        String token = jwtUtil.generateToken(1L, "user@test.com", "USER");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/recruiter/candidates")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void adminRoute_withUserRole_returns403() {
        String token = jwtUtil.generateToken(1L, "user@test.com", "USER");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void adminRoute_withRecruiterRole_returns403() {
        String token = jwtUtil.generateToken(1L, "recruiter@test.com", "RECRUITER");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void recruiterRoute_withRecruiterRole_addsTrustedHeaders() {
        String token = jwtUtil.generateToken(42L, "recruiter@test.com", "RECRUITER");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/recruiter/candidates")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            ServerHttpRequest mutated = ex.getRequest();
            assertEquals("42", mutated.getHeaders().getFirst("X-User-Id"));
            assertEquals("recruiter@test.com", mutated.getHeaders().getFirst("X-User-Email"));
            assertEquals("RECRUITER", mutated.getHeaders().getFirst("X-User-Role"));
            assertEquals("access", mutated.getHeaders().getFirst("X-Token-Type"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(jwtFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void protectedRoute_withSpoofedHeaders_replacesWithJwtValues() {
        String token = jwtUtil.generateToken(1L, "real@test.com", "USER");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/github/profile/torvalds")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "999")
                .header("X-User-Email", "evil@example.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            ServerHttpRequest mutated = ex.getRequest();
            assertEquals("1", mutated.getHeaders().getFirst("X-User-Id"));
            assertEquals("real@test.com", mutated.getHeaders().getFirst("X-User-Email"));
            assertEquals("USER", mutated.getHeaders().getFirst("X-User-Role"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(jwtFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void recruiterRoute_withAdminRole_succeeds() {
        String token = jwtUtil.generateToken(1L, "admin@test.com", "ADMIN");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/recruiter/candidates")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    @Test
    void adminRoute_withAdminRole_succeeds() {
        String token = jwtUtil.generateToken(1L, "admin@test.com", "ADMIN");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    // ════════════════════════════════════════════════════════════════
    // CookieForwardingFilter tests — use raw Cookie headers (browser-style)
    // ════════════════════════════════════════════════════════════════

    @Test
    void cookieFilter_authRoute_forwardsAllCookies() {
        MockServerHttpRequest request = rawCookieRequest(
                "/api/auth/refresh",
                AuthCookieNames.ACCESS + "=access-token; " +
                AuthCookieNames.REFRESH + "=refresh-token; XSRF-TOKEN=csrf-value");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            ServerHttpRequest mutated = ex.getRequest();
            // Auth routes: all cookies forwarded unchanged
            String cookieHeader = mutated.getHeaders().getFirst("Cookie");
            assertNotNull(cookieHeader);
            assertTrue(cookieHeader.contains(AuthCookieNames.ACCESS));
            assertTrue(cookieHeader.contains(AuthCookieNames.REFRESH));
            assertTrue(cookieHeader.contains("XSRF-TOKEN"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(cookieFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void cookieFilter_nonAuthRoute_removesRefreshCookie() {
        MockServerHttpRequest request = rawCookieRequest(
                "/api/github/profile/torvalds",
                AuthCookieNames.ACCESS + "=access-token; " +
                AuthCookieNames.REFRESH + "=refresh-token; XSRF-TOKEN=csrf-value");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            ServerHttpRequest mutated = ex.getRequest();
            String cookieHeader = mutated.getHeaders().getFirst("Cookie");
            assertNotNull(cookieHeader);
            assertFalse(cookieHeader.contains(AuthCookieNames.REFRESH),
                    "Refresh token should not be forwarded to non-auth routes");
            assertTrue(cookieHeader.contains(AuthCookieNames.ACCESS));
            assertTrue(cookieHeader.contains("XSRF-TOKEN"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(cookieFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void cookieFilter_nonAuthRoute_noCookies_doesNothing() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/github/profile/torvalds")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            assertNull(ex.getRequest().getHeaders().getFirst("Cookie"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(cookieFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void cookieFilter_nonAuthRoute_onlyRefreshCookie_removesCookieHeader() {
        MockServerHttpRequest request = rawCookieRequest(
                "/api/github/profile/torvalds",
                AuthCookieNames.REFRESH + "=refresh-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            // Only cookie was the refresh token — Cookie header should be removed
            assertNull(ex.getRequest().getHeaders().getFirst("Cookie"));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(cookieFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    @Test
    void cookieFilter_nonAuthRoute_accessOnly_passesThrough() {
        MockServerHttpRequest request = rawCookieRequest(
                "/api/github/profile/torvalds",
                AuthCookieNames.ACCESS + "=access-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain captureChain = ex -> {
            String cookieHeader = ex.getRequest().getHeaders().getFirst("Cookie");
            assertNotNull(cookieHeader);
            assertTrue(cookieHeader.contains(AuthCookieNames.ACCESS));
            assertFalse(cookieHeader.contains(AuthCookieNames.REFRESH));
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(cookieFilter.filter(exchange, captureChain))
                .verifyComplete();
    }

    // ════════════════════════════════════════════════════════════════
    // Filter order tests
    // ════════════════════════════════════════════════════════════════

    @Test
    void cookieFilter_runsBeforeJwtFilter() {
        assertTrue(cookieFilter.getOrder() < jwtFilter.getOrder(),
                "CookieForwardingFilter must run before JwtAuthenticationFilter");
    }
}
