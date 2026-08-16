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
}
