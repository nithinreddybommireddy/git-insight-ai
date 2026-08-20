package com.gitinsight.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.security.AuthCookieNames;
import com.gitinsight.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter} and {@link CookieForwardingFilter}.
 *
 * <p>Covers all 18 required security scenarios plus boundary/cookie tests.
 * Tests the filter logic directly without booting the full Spring context.
 */
class GatewayFilterTests {

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-32bytes!";

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter jwtFilter;
    private CookieForwardingFilter cookieFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, 900_000, 2_592_000_000L);
        jwtFilter = new JwtAuthenticationFilter(jwtUtil, objectMapper);
        cookieFilter = new CookieForwardingFilter();
    }

    /** Chain that always returns 200 OK. */
    private final GatewayFilterChain okChain = exchange -> {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return Mono.empty();
    };

    /** Chain that captures the mutated request for inspection. */
    private GatewayFilterChain captureChain(java.util.function.Consumer<ServerHttpRequest> assertor) {
        return exchange -> {
            assertor.accept(exchange.getRequest());
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

    /** Build a MockServerHttpRequest with parsed cookies (for JWT cookie extraction). */
    private static MockServerHttpRequest parsedCookieRequest(String path, String name, String value) {
        return MockServerHttpRequest.get(path)
                .cookie(new HttpCookie(name, value))
                .build();
    }

    private MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    // ════════════════════════════════════════════════════════════════
    // 1. PUBLIC ROUTES — no JWT required
    // ════════════════════════════════════════════════════════════════

    @Nested
    class PublicRoutes {

        // Scenario 8: anonymous /api/github/profile/x -> allowed
        @Test void githubProfile_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/github/profile/torvalds").build()), okChain))
                    .verifyComplete();
        }

        // Scenario 9: anonymous /api/ai/status -> allowed
        @Test void aiStatus_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/ai/status").build()), okChain))
                    .verifyComplete();
        }

        @Test void authLogin_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/auth/login").build()), okChain))
                    .verifyComplete();
        }

        @Test void authRegister_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/auth/register").build()), okChain))
                    .verifyComplete();
        }

        @Test void authRefresh_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/auth/refresh").build()), okChain))
                    .verifyComplete();
        }

        @Test void authLogout_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/auth/logout").build()), okChain))
                    .verifyComplete();
        }

        @Test void authOAuth_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/auth/oauth/github").build()), okChain))
                    .verifyComplete();
        }

        @Test void authOAuthCallback_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/auth/oauth/github/callback").build()), okChain))
                    .verifyComplete();
        }

        @Test void aiAnalysisEndpoint_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/api/ai/summary/torvalds").build()), okChain))
                    .verifyComplete();
        }

        @Test void actuatorHealth_public() {
            StepVerifier.create(jwtFilter.filter(
                    exchange(MockServerHttpRequest.get("/actuator/health").build()), okChain))
                    .verifyComplete();
        }

        @Test void authMe_protectedNotPublic() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/auth/me").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        // Scenario 17: /api/githubFake does NOT become public
        @Test void githubFake_notPublic() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/githubFake").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        // Scenario 18: /api/auth/loginFake does NOT become public
        @Test void authLoginFake_notPublic() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/auth/loginFake").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void aifake_notPublic() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/aifake").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 2. PROTECTED POST AI ENDPOINTS
    // ════════════════════════════════════════════════════════════════

    @Nested
    class ProtectedAiPost {

        // Scenario 10: anonymous POST /api/ai/commit-diff-review -> 401
        @Test void commitDiffReview_anonymous_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .post("/api/ai/commit-diff-review").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        // Scenario 11: authenticated POST /api/ai/commit-diff-review -> allowed
        @Test void commitDiffReview_authenticated_succeeds() {
            String token = jwtUtil.generateToken(1L, "user@test.com", "USER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .post("/api/ai/commit-diff-review")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }

        // GET /api/ai/status still public even for POST path prefix
        @Test void aiStatus_post_stillPublic() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .post("/api/ai/status").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            // /api/ai/status is not in PROTECTED_AI_POST_PREFIXES, so GET/POST both public
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 3. PROTECTED ROUTES — require JWT
    // ════════════════════════════════════════════════════════════════

    @Nested
    class ProtectedRoutes {

        @Test void recruiterWithoutJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/recruiter/candidates").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void adminWithoutJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/admin/users").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void recruiterSaveWithoutJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .post("/api/recruiter/candidates/save").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void recruiterBookmarkWithoutJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .put("/api/recruiter/candidates/torvalds/bookmark").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void recruiterDeleteWithoutJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .delete("/api/recruiter/candidates/torvalds").build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 4. JWT VALIDATION — invalid/expired/malformed
    // ════════════════════════════════════════════════════════════════

    @Nested
    class JwtValidation {

        // Scenario 1: malformed signed JWT -> 401
        @Test void malformedSignedJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/reports/latest/torvalds")
                    .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.invalidsignature")
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        // Scenario 2: invalid role in JWT -> 401
        @Test void invalidRole_returns401() {
            String badToken = jwtUtil.generateToken(1L, "user@test.com", "SUPERUSER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/recruiter/candidates")
                    .header("Authorization", "Bearer " + badToken)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void invalidJwt_returns401() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/reports/latest/torvalds")
                    .header("Authorization", "Bearer invalid.jwt.token")
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void refreshToken_returns401() {
            String refreshToken = jwtUtil.generateRefreshToken(1L);
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/reports/latest/torvalds")
                    .header("Authorization", "Bearer " + refreshToken)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        // Scenario 3: valid USER -> protected normal route allowed
        @Test void validUser_passesProtectedRoute() {
            String token = jwtUtil.generateToken(1L, "user@test.com", "USER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/reports/latest/torvalds")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }

        @Test void expiredToken_returns401() {
            JwtUtil shortLivedJwt = new JwtUtil(TEST_SECRET, 0, 0);
            String expiredToken = shortLivedJwt.generateToken(1L, "user@test.com", "USER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/reports/latest/torvalds")
                    .header("Authorization", "Bearer " + expiredToken)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        }

        @Test void cookieToken_passes() {
            String token = jwtUtil.generateToken(1L, "user@test.com", "USER");
            MockServerWebExchange ex = exchange(parsedCookieRequest(
                    "/api/reports/latest/torvalds", AuthCookieNames.ACCESS, token));
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 5. ROLE AUTHORIZATION
    // ════════════════════════════════════════════════════════════════

    @Nested
    class RoleAuthorization {

        // Scenario 4: USER -> recruiter route -> 403
        @Test void userOnRecruiterRoute_returns403() {
            String token = jwtUtil.generateToken(1L, "user@test.com", "USER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/recruiter/candidates")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
        }

        // Scenario 5: RECRUITER -> recruiter route -> allowed
        @Test void recruiterOnRecruiterRoute_succeeds() {
            String token = jwtUtil.generateToken(2L, "recruiter@test.com", "RECRUITER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/recruiter/candidates")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }

        @Test void adminOnRecruiterRoute_succeeds() {
            String token = jwtUtil.generateToken(3L, "admin@test.com", "ADMIN");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/recruiter/candidates")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }

        // Scenario 6: USER -> admin -> 403
        @Test void userOnAdminRoute_returns403() {
            String token = jwtUtil.generateToken(1L, "user@test.com", "USER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/admin/users")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
        }

        // Scenario 7: ADMIN -> admin -> allowed
        @Test void adminOnAdminRoute_succeeds() {
            String token = jwtUtil.generateToken(3L, "admin@test.com", "ADMIN");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/admin/users")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
        }

        @Test void recruiterOnAdminRoute_returns403() {
            String token = jwtUtil.generateToken(2L, "recruiter@test.com", "RECRUITER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/admin/users")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, okChain)).verifyComplete();
            assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 6. HEADER STRIPPING — spoofed headers always removed
    // ════════════════════════════════════════════════════════════════

    @Nested
    class HeaderStripping {

        // Scenario 14: spoofed X-User-Role stripped
        @Test void spoofedHeaders_onPublicRoute_stripped() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/auth/register")
                    .header("X-User-Role", "ADMIN")
                    .header("X-User-Id", "999")
                    .header("X-User-Email", "evil@example.com")
                    .header("X-Token-Type", "access")
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, captureChain(req -> {
                assertNull(req.getHeaders().getFirst("X-User-Role"));
                assertNull(req.getHeaders().getFirst("X-User-Id"));
                assertNull(req.getHeaders().getFirst("X-User-Email"));
                assertNull(req.getHeaders().getFirst("X-Token-Type"));
            }))).verifyComplete();
        }

        @Test void spoofedHeaders_onProtectedRoute_replacedWithJwtValues() {
            String token = jwtUtil.generateToken(1L, "real@test.com", "USER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/reports/latest/torvalds")
                    .header("Authorization", "Bearer " + token)
                    .header("X-User-Role", "ADMIN")
                    .header("X-User-Id", "999")
                    .header("X-User-Email", "evil@example.com")
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, captureChain(req -> {
                assertEquals("1", req.getHeaders().getFirst("X-User-Id"));
                assertEquals("real@test.com", req.getHeaders().getFirst("X-User-Email"));
                assertEquals("USER", req.getHeaders().getFirst("X-User-Role"));
                assertEquals("access", req.getHeaders().getFirst("X-Token-Type"));
            }))).verifyComplete();
        }

        @Test void validJwt_addsTrustedHeaders() {
            String token = jwtUtil.generateToken(42L, "recruiter@test.com", "RECRUITER");
            MockServerWebExchange ex = exchange(MockServerHttpRequest
                    .get("/api/recruiter/candidates")
                    .header("Authorization", "Bearer " + token)
                    .build());
            StepVerifier.create(jwtFilter.filter(ex, captureChain(req -> {
                assertEquals("42", req.getHeaders().getFirst("X-User-Id"));
                assertEquals("recruiter@test.com", req.getHeaders().getFirst("X-User-Email"));
                assertEquals("RECRUITER", req.getHeaders().getFirst("X-User-Role"));
                assertEquals("access", req.getHeaders().getFirst("X-Token-Type"));
            }))).verifyComplete();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 7. COOKIE FORWARDING — refresh token isolation
    // ════════════════════════════════════════════════════════════════

    @Nested
    class CookieForwarding {

        // Scenario 16: refresh cookie preserved on Auth requests
        @Test void authRoute_forwardsAllCookies() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/auth/refresh",
                    AuthCookieNames.ACCESS + "=a; " + AuthCookieNames.REFRESH + "=r; XSRF-TOKEN=x"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertTrue(cookie.contains(AuthCookieNames.ACCESS));
                assertTrue(cookie.contains(AuthCookieNames.REFRESH));
                assertTrue(cookie.contains("XSRF-TOKEN"));
            }))).verifyComplete();
        }

        // Scenario 15: refresh cookie removed on GitHub requests
        @Test void githubRoute_removesRefreshCookie() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/github/profile/torvalds",
                    AuthCookieNames.ACCESS + "=a; " + AuthCookieNames.REFRESH + "=r; XSRF-TOKEN=x"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertFalse(cookie.contains(AuthCookieNames.REFRESH),
                        "Refresh cookie must NOT be forwarded to github-service");
                assertTrue(cookie.contains(AuthCookieNames.ACCESS));
                assertTrue(cookie.contains("XSRF-TOKEN"));
            }))).verifyComplete();
        }

        // Scenario 15: refresh cookie removed on Analytics requests
        @Test void analyticsRoute_removesRefreshCookie() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/analytics/health",
                    AuthCookieNames.ACCESS + "=a; " + AuthCookieNames.REFRESH + "=r"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertFalse(cookie.contains(AuthCookieNames.REFRESH));
            }))).verifyComplete();
        }

        @Test void reportsRoute_removesRefreshCookie() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/reports/latest/torvalds",
                    AuthCookieNames.ACCESS + "=a; " + AuthCookieNames.REFRESH + "=r; XSRF-TOKEN=x"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertFalse(cookie.contains(AuthCookieNames.REFRESH));
                assertTrue(cookie.contains(AuthCookieNames.ACCESS));
            }))).verifyComplete();
        }

        @Test void recruiterRoute_removesRefreshCookie() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/recruiter/candidates",
                    AuthCookieNames.ACCESS + "=a; " + AuthCookieNames.REFRESH + "=r; XSRF-TOKEN=x"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertFalse(cookie.contains(AuthCookieNames.REFRESH));
                assertTrue(cookie.contains(AuthCookieNames.ACCESS));
            }))).verifyComplete();
        }

        @Test void refreshOnly_noSpaces_removesRefreshCookie() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/github/profile/torvalds",
                    AuthCookieNames.REFRESH + "=r"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                assertNull(req.getHeaders().getFirst("Cookie"));
            }))).verifyComplete();
        }

        @Test void refreshFirst_refreshLast_handledCorrectly() {
            MockServerWebExchange ex1 = exchange(rawCookieRequest(
                    "/api/github/test",
                    AuthCookieNames.REFRESH + "=r; " + AuthCookieNames.ACCESS + "=a"));
            StepVerifier.create(cookieFilter.filter(ex1, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertTrue(cookie.contains(AuthCookieNames.ACCESS));
                assertFalse(cookie.contains(AuthCookieNames.REFRESH));
            }))).verifyComplete();

            MockServerWebExchange ex2 = exchange(rawCookieRequest(
                    "/api/github/test",
                    AuthCookieNames.ACCESS + "=a; " + AuthCookieNames.REFRESH + "=r"));
            StepVerifier.create(cookieFilter.filter(ex2, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertTrue(cookie.contains(AuthCookieNames.ACCESS));
                assertFalse(cookie.contains(AuthCookieNames.REFRESH));
            }))).verifyComplete();
        }

        @Test void unrelatedCookieContainingRefreshToken_substring_notRemoved() {
            MockServerWebExchange ex = exchange(rawCookieRequest(
                    "/api/github/test",
                    AuthCookieNames.ACCESS + "=a; foo_gitinsight_refresh_token_extra=keep"));
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                String cookie = req.getHeaders().getFirst("Cookie");
                assertNotNull(cookie);
                assertTrue(cookie.contains("foo_gitinsight_refresh_token_extra=keep"),
                        "Unrelated cookie with similar name must not be removed");
            }))).verifyComplete();
        }

        @Test void noCookies_doesNothing() {
            MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/github/test").build());
            StepVerifier.create(cookieFilter.filter(ex, captureChain(req -> {
                assertNull(req.getHeaders().getFirst("Cookie"));
            }))).verifyComplete();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 8. FILTER ORDER
    // ════════════════════════════════════════════════════════════════

    @Test
    void cookieFilterRunsBeforeJwtFilter() {
        assertTrue(cookieFilter.getOrder() < jwtFilter.getOrder(),
                "CookieForwardingFilter must run before JwtAuthenticationFilter");
    }
}
