package com.gitinsight.authservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FilterChain chain = mock(FilterChain.class);

    private RateLimitFilter filter(int budget) {
        return new RateLimitFilter(budget, objectMapper, new InMemoryFixedWindowRateLimiter(60_000L));
    }

    private MockHttpServletRequest request(String uri, String ip, String forwardedFor) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        if (forwardedFor != null) {
            req.addHeader("X-Forwarded-For", forwardedFor);
        }
        return req;
    }

    @Test
    void blocksRequestsBeyondBudgetPerIpAndPath() throws Exception {
        RateLimitFilter filter = filter(3);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/auth/login", "10.0.0.1", null), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/login", "10.0.0.1", null), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        // A different IP has its own budget.
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/login", "10.0.0.2", null), other, chain);
        assertThat(other.getStatus()).isEqualTo(200);

        // register is a separate budget from login.
        MockHttpServletResponse register = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/register", "10.0.0.1", null), register, chain);
        assertThat(register.getStatus()).isEqualTo(200);
    }

    @Test
    void honorsXForwardedForFirstHop() throws Exception {
        RateLimitFilter filter = filter(1);

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/login", "127.0.0.1", "203.0.113.9, 10.0.0.1"), first, chain);
        assertThat(first.getStatus()).isEqualTo(200);

        // Same socket, same forwarded client → blocked.
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/login", "127.0.0.1", "203.0.113.9, 10.0.0.2"), second, chain);
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotRateLimitNonCredentialEndpoints() throws Exception {
        RateLimitFilter filter = filter(1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/me", "10.0.0.1", null), response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════
    // Password reset rate limiting
    // ═══════════════════════════════════════════════════════

    @Test
    void rateLimitsForgotPasswordEndpoint() throws Exception {
        RateLimitFilter filter = filter(2);

        // First two requests allowed.
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "10.0.0.1", null), r1, chain);
        assertThat(r1.getStatus()).isEqualTo(200);

        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "10.0.0.1", null), r2, chain);
        assertThat(r2.getStatus()).isEqualTo(200);

        // Third request blocked.
        MockHttpServletResponse r3 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "10.0.0.1", null), r3, chain);
        assertThat(r3.getStatus()).isEqualTo(429);
    }

    @Test
    void rateLimitsResetPasswordEndpoint() throws Exception {
        RateLimitFilter filter = filter(2);

        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/reset-password", "10.0.0.1", null), r1, chain);
        assertThat(r1.getStatus()).isEqualTo(200);

        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/reset-password", "10.0.0.1", null), r2, chain);
        assertThat(r2.getStatus()).isEqualTo(200);

        MockHttpServletResponse r3 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/reset-password", "10.0.0.1", null), r3, chain);
        assertThat(r3.getStatus()).isEqualTo(429);
    }

    @Test
    void forgotPasswordAndResetPasswordHaveSeparateBudgets() throws Exception {
        RateLimitFilter filter = filter(1);

        // Use the budget on forgot-password.
        MockHttpServletResponse fp = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "10.0.0.1", null), fp, chain);
        assertThat(fp.getStatus()).isEqualTo(200);

        // forgot-password is now blocked.
        MockHttpServletResponse fp2 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "10.0.0.1", null), fp2, chain);
        assertThat(fp2.getStatus()).isEqualTo(429);

        // But reset-password has its own budget.
        MockHttpServletResponse rp = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/reset-password", "10.0.0.1", null), rp, chain);
        assertThat(rp.getStatus()).isEqualTo(200);
    }

    @Test
    void spoofedForwardedForFromUntrustedPeerDoesNotBypassRateLimit() throws Exception {
        RateLimitFilter filter = filter(1);

        // First request from a public IP (not a trusted proxy).
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "203.0.113.50", null), r1, chain);
        assertThat(r1.getStatus()).isEqualTo(200);

        // Attacker spoofs X-Forwarded-For from a public IP — not trusted because
        // the direct peer (203.0.113.50) is not a known proxy.
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "203.0.113.50", "1.2.3.4"), r2, chain);
        assertThat(r2.getStatus()).isEqualTo(429);
    }

    @Test
    void trustedProxyForwardedForIsUsed() throws Exception {
        RateLimitFilter filter = filter(1);

        // Request from a private proxy (Docker nginx gateway) with X-Forwarded-For.
        // The proxy IP is trusted, so the forwarded client IP is used for rate limiting.
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "172.17.0.1", "203.0.113.50"), r1, chain);
        assertThat(r1.getStatus()).isEqualTo(200);

        // Same forwarded client → blocked.
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "172.17.0.1", "203.0.113.50"), r2, chain);
        assertThat(r2.getStatus()).isEqualTo(429);

        // Different forwarded client → allowed (different budget).
        MockHttpServletResponse r3 = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password", "172.17.0.1", "10.0.0.99"), r3, chain);
        assertThat(r3.getStatus()).isEqualTo(200);
    }
}
