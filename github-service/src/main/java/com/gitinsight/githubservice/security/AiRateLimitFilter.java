package com.gitinsight.githubservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.common.web.ClientAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Fixed-window rate limiter for the AI endpoints ({@code /api/ai/**}).
 *
 * <p>Every AI call can fan out into Gemini invocations that consume quota and
 * money, so unlike the GitHub analysis surface these get a per-client budget
 * (default 30 requests/minute, {@code AI_RATE_LIMIT_PER_MINUTE}). The trivial
 * {@code /api/ai/status} health check is exempt. Budgets are Redis-backed
 * (shared across instances); when Redis is down the filter falls back to a
 * per-instance in-memory limiter ({@link InMemoryRateLimiter}) so spend
 * protection NEVER fails open.
 *
 * <p>The client key is the real client IP — {@link ClientAddress} only honors
 * {@code X-Forwarded-For} when the direct peer is a proxy this project deploys
 * behind, so direct callers cannot spoof their way around the budget.
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    private final int maxRequestsPerWindow;
    private final ObjectMapper objectMapper;
    private final RedisRateLimiter redisRateLimiter;
    private final InMemoryRateLimiter inMemoryRateLimiter;

    public AiRateLimitFilter(
            @Value("${app.security.ai-rate-limit-per-minute:30}") int maxRequestsPerWindow,
            ObjectMapper objectMapper,
            RedisRateLimiter redisRateLimiter,
            InMemoryRateLimiter inMemoryRateLimiter) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.objectMapper = objectMapper;
        this.redisRateLimiter = redisRateLimiter;
        this.inMemoryRateLimiter = inMemoryRateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.contains("/api/ai/") || uri.endsWith("/api/ai/status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = ClientAddress.resolve(request);
        String key = "ai:" + client + ":" + request.getRequestURI();
        Long count = redisRateLimiter.tryIncrement(key);
        long current = count != null ? count : inMemoryRateLimiter.increment(key);

        if (current > maxRequestsPerWindow) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ApiResponse<>(false,
                            "AI request limit reached for this minute. Please wait and try again.", null)));
            return;
        }

        chain.doFilter(request, response);
    }
}
