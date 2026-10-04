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
 *   /score, /commits/analytics, /commits/diffs   10/min (default)
 *   /org/&lt;org&gt;/overview                           5/min (default)
 *   everything else (profile, repos, ...)        60/min (default)
 * </pre>
 *
 * <p>Budgets are configuration, not constants: set
 * {@code GITHUB_SCORE_RATE_LIMIT_PER_MINUTE},
 * {@code GITHUB_ORG_RATE_LIMIT_PER_MINUTE} and
 * {@code GITHUB_GENERAL_RATE_LIMIT_PER_MINUTE} (via application.yml) to tune
 * them per environment without a code change. A recruiter job match scores
 * every candidate, so production commonly raises the score budget.
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

    /** Budgets are configuration (application.yml → env), not hardcoded numbers. */
    private final int scorePerMinute;
    private final int orgPerMinute;
    private final int generalPerMinute;

    public GitHubRateLimitFilter(ObjectMapper objectMapper,
                                 RedisRateLimiter redisRateLimiter,
                                 InMemoryRateLimiter inMemoryRateLimiter,
                                 @Value("${app.security.github-score-rate-limit-per-minute:10}") int scorePerMinute,
                                 @Value("${app.security.github-org-rate-limit-per-minute:5}") int orgPerMinute,
                                 @Value("${app.security.github-general-rate-limit-per-minute:60}") int generalPerMinute) {
        this.objectMapper = objectMapper;
        this.redisRateLimiter = redisRateLimiter;
        this.inMemoryRateLimiter = inMemoryRateLimiter;
        this.scorePerMinute = scorePerMinute;
        this.orgPerMinute = orgPerMinute;
        this.generalPerMinute = generalPerMinute;
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

    private int budgetFor(String uri) {
        if (uri.contains("/score") || uri.contains("/commits/analytics") || uri.contains("/commits/diffs")) {
            return scorePerMinute;
        }
        if (uri.contains("/org/") && uri.endsWith("/overview")) {
            return orgPerMinute;
        }
        return generalPerMinute;
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
        return List.of("general (60/min default)", "score (10/min default)", "commits/analytics + diffs (10/min default)", "org overview (5/min default)");
    }
}
