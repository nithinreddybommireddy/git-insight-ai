package com.gitinsight.authservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Fixed-window rate limiter for the credential endpoints
 * ({@code /api/auth/login}, {@code /api/auth/register},
 * {@code /api/auth/forgot-password}, {@code /api/auth/reset-password})
 * to blunt brute-force password guessing, mass account creation, and
 * email-abuse via password reset requests. Counting is delegated to
 * {@link FixedWindowRateLimiter} — Redis in production, so windows are shared
 * across instances and survive restarts.
 *
 * <p>Budget: {@code app.security.auth-rate-limit-per-minute} requests per client
 * IP per 60-second window (default 20). The client IP honors the first
 * {@code X-Forwarded-For} hop ONLY when the request came through a proxy
 * (loopback or RFC1918 private peer — i.e. the Vite dev proxy / nginx gateway),
 * and falls back to the socket address otherwise. This prevents an attacker who
 * can reach the service directly from spoofing the header to bypass the limiter
 * (an {@code X-Forwarded-For} header can always be forged; only the direct peer
 * address is trustworthy). This is still a best-effort guard — a production
 * deployment behind a load balancer should also apply network-level rate
 * limiting at the gateway.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequestsPerWindow;
    private final ObjectMapper objectMapper;
    private final FixedWindowRateLimiter rateLimiter;

    public RateLimitFilter(
            @Value("${app.security.auth-rate-limit-per-minute:20}") int maxRequestsPerWindow,
            ObjectMapper objectMapper,
            FixedWindowRateLimiter rateLimiter) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.endsWith("/api/auth/login")
                && !uri.endsWith("/api/auth/register")
                && !uri.endsWith("/api/auth/forgot-password")
                && !uri.endsWith("/api/auth/reset-password");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long count = rateLimiter.increment(clientKey(request));

        if (count > maxRequestsPerWindow) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ApiResponse<>(false, "Too many attempts. Please try again in a minute.", null)));
            return;
        }

        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        return com.gitinsight.common.web.ClientAddress.resolve(request) + "|" + request.getRequestURI();
    }
}
