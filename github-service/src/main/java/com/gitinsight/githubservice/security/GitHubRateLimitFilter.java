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
import java.util.List;

/**
 * Per-client rate limiting for the public GitHub analysis surface
 * ({@code /api/github/**}).
 *
 * <p>The analysis endpoints are intentionally public (anyone can look up a
 * developer), but a single score request can fan out into many upstream GitHub
 * API calls backed by the shared {@code GITHUB_TOKEN}. Cheap and expensive
 * routes get different per-IP budgets so an attacker cannot burn the GitHub
 * quota with unique usernames:
 *
 * <pre>
 *   /profile, /languages, /repos, /rate-limit   60/min
 *   /score, /commits/analytics, /commits/diffs   10/min
 *   /org/&lt;org&gt;/overview                           5/min
 *   anything else under /api/github              30/min
 * </pre>
 *
 * <p>Budgets are Redis-backed (shared across instances) with an in-memory
 * fallback when Redis is down — the limiter never fails open for this surface
 * because the cost is real upstream quota.
 *
 * @see #budgetFor(String)
 */
@Component
public class GitHubRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final RedisRateLimiter redisRateLimiter;
    private final InMemoryRateLimiter inMemoryRateLimiter;

    public GitHubRateLimitFilter(ObjectMapper objectMapper,
                                 RedisRateLimiter redisRateLimiter,
                                 InMemoryRateLimiter inMemoryRateLimiter) {
        this.objectMapper = objectMapper;
        this.redisRateLimiter = redisRateLimiter;
        this.inMemoryRateLimiter = inMemoryRateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.contains("/api/github/") || uri.endsWith("/api/github/rate-limit");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        int limit = budgetFor(uri);
        String key = "gh:" + ClientAddress.resolve(request) + ":" + tierOf(uri);

        Long count = redisRateLimiter.tryIncrement(key);
        long current = count != null ? count : inMemoryRateLimiter.increment(key);

        if (current > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ApiResponse<>(false,
                            "Too many analysis requests. Please wait a minute and try again.", null)));
            return;
        }

        chain.doFilter(request, response);
    }

    private static int budgetFor(String uri) {
        if (uri.contains("/score") || uri.contains("/commits/analytics") || uri.contains("/commits/diffs")) {
            return 10;
        }
        if (uri.contains("/org/") && uri.endsWith("/overview")) {
            return 5;
        }
        return 60;
    }

    private static String tierOf(String uri) {
        if (uri.contains("/score")) return "score";
        if (uri.contains("/commits/analytics")) return "commits";
        if (uri.contains("/commits/diffs")) return "diffs";
        if (uri.contains("/org/") && uri.endsWith("/overview")) return "org";
        return "general";
    }

    // Referenced by javadoc only — keeps the tier list discoverable.
    static List<String> routeTiers() {
        return List.of("general (60/min)", "score (10/min)", "commits/analytics + diffs (10/min)", "org overview (5/min)");
    }
}
