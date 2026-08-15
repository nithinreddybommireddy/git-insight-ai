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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter for the credential endpoints
 * ({@code /api/auth/login}, {@code /api/auth/register}) to blunt brute-force
 * password guessing and mass account creation.
 *
 * <p>Budget: {@code app.security.auth-rate-limit-per-minute} requests per client
 * IP per 60-second window (default 20). The client IP honors the first
 * {@code X-Forwarded-For} hop when present (proxied deployments), falling back
 * to the socket address. This is a best-effort, per-instance guard — a
 * production deployment behind a load balancer should also apply network-level
 * rate limiting at the gateway.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxRequestsPerWindow;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.security.auth-rate-limit-per-minute:20}") int maxRequestsPerWindow,
            ObjectMapper objectMapper) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.endsWith("/api/auth/login") && !uri.endsWith("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientKey(request);
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= WINDOW_MS) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });

        if (window.count > maxRequestsPerWindow) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ApiResponse<>(false, "Too many attempts. Please try again in a minute.", null)));
            return;
        }

        // Opportunistic cleanup so a burst of distinct IPs cannot grow the map forever.
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MS);
        }

        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip + "|" + request.getRequestURI();
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
