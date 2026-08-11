package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.config.GitHubRateLimitInterceptor;
import com.gitinsight.githubservice.dto.response.OrganizationAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Organization / team-level analytics.
 * <p>
 * Fetches a GitHub organization's public profile and repositories, then reuses
 * the cached, parallel per-repo enrichment from {@link GitHubIntegrationService}
 * (byte-weighted languages + top contributors) to produce a team overview.
 * Everything is cached; repeat views are cheap.
 */
@Service
public class OrganizationAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationAnalyticsService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private static final int MAX_REPOS = 30;          // repos considered for stats/top-repos
    private static final int MAX_TOP_REPOS = 8;
    private static final Duration PROFILE_TTL = Duration.ofHours(1);
    private static final Duration OVERVIEW_TTL = Duration.ofMinutes(15);

    private final RestClient restClient;
    private final GitHubCacheService cacheService;
    private final GitHubIntegrationService integrationService;

    @Autowired
    public OrganizationAnalyticsService(
            @Value("${github.token:}") String githubToken,
            GitHubCacheService cacheService,
            GitHubIntegrationService integrationService,
            GitHubRateLimitInterceptor rateLimitInterceptor) {
        this(buildRestClient(githubToken, rateLimitInterceptor), cacheService, integrationService);
    }

    /**
     * Package-private constructor for unit tests — allows injecting a mock RestClient.
     */
    OrganizationAnalyticsService(RestClient restClient,
                                 GitHubCacheService cacheService,
                                 GitHubIntegrationService integrationService) {
        this.restClient = restClient;
        this.cacheService = cacheService;
        this.integrationService = integrationService;
    }

    private static RestClient buildRestClient(String githubToken, GitHubRateLimitInterceptor rateLimitInterceptor) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0");

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }
        builder.requestInterceptor(rateLimitInterceptor);
        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    public OrganizationAnalyticsResponse getOverview(String login) {
        String cacheKey = "org-overview:" + login;
        OrganizationAnalyticsResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Object> profile = fetchOrgProfile(login);
        if (profile.isEmpty()) {
            OrganizationAnalyticsResponse empty = OrganizationAnalyticsResponse.empty(login);
            cacheService.put(cacheKey, empty, Duration.ofMinutes(5));
            return empty;
        }

        List<Map<String, Object>> repos = fetchOrgRepos(login);
        OrganizationAnalyticsResponse response = build(login, profile, repos);
        cacheService.put(cacheKey, response, OVERVIEW_TTL);
        return response;
    }

    // ═══════════════════════════════════════════════════════════════
    // GITHUB FETCH
    // ═══════════════════════════════════════════════════════════════

    private Map<String, Object> fetchOrgProfile(String login) {
        String cacheKey = "org-profile:" + login;
        Map<String, Object> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            Map<String, Object> profile = restClient.get()
                    .uri("/orgs/{login}", login)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (profile != null) {
                cacheService.put(cacheKey, profile, PROFILE_TTL);
            }
            return profile == null ? Map.of() : profile;
        } catch (Exception e) {
            log.warn("Failed to fetch org profile for {}: {}", login, e.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> fetchOrgRepos(String login) {
        String cacheKey = "org-repos:" + login;
        List<Map<String, Object>> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> repos = restClient.get()
                    .uri("/orgs/{login}/repos?per_page=100&sort=pushed", login)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (repos != null) {
                cacheService.put(cacheKey, repos, Duration.ofMinutes(10));
            }
            return repos == null ? List.of() : repos;
        } catch (Exception e) {
            log.warn("Failed to fetch org repos for {}: {}", login, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AGGREGATION
    // ═══════════════════════════════════════════════════════════════

    OrganizationAnalyticsResponse build(String login,
                                        Map<String, Object> profile,
                                        List<Map<String, Object>> repos) {
        OrganizationAnalyticsResponse res = new OrganizationAnalyticsResponse();

        // ── Profile ──
        res.setLogin(login);
        res.setName(str(profile.get("name")));
        res.setDescription(str(profile.get("description")));
        res.setAvatarUrl(nz(str(profile.get("avatar_url")), ""));
        res.setBlog(str(profile.get("blog")));
        res.setLocation(str(profile.get("location")));
        res.setPublicRepos(num(profile.get("public_repos")));
        res.setFollowers(num(profile.get("followers")));
        res.setCreatedAt(str(profile.get("created_at")));

        // ── Effective repos (non-fork, non-archived), newest-first then by stars ──
        List<Map<String, Object>> effective = repos.stream()
                .filter(r -> !Boolean.TRUE.equals(r.get("fork")))
                .filter(r -> !Boolean.TRUE.equals(r.get("archived")))
                .sorted(Comparator.comparingInt((Map<String, Object> r) -> num(r.get("stargazers_count"))).reversed())
                .collect(Collectors.toList());

        List<Map<String, Object>> sampled = effective.stream().limit(MAX_REPOS).collect(Collectors.toList());

        int stars = sampled.stream().mapToInt(r -> num(r.get("stargazers_count"))).sum();
        int forks = sampled.stream().mapToInt(r -> num(r.get("forks_count"))).sum();
        int active = (int) sampled.stream().filter(r -> recentlyPushed(r)).count();

        res.setTotalRepos(sampled.size());
        res.setTotalStars(stars);
        res.setTotalForks(forks);
        res.setAverageStars(sampled.isEmpty() ? 0 : Math.round(stars * 10.0 / sampled.size()) / 10.0);
        res.setActiveRepos(active);

        // ── Top repos (by stars) ──
        res.setTopRepos(effective.stream()
                .limit(MAX_TOP_REPOS)
                .map(r -> {
                    OrganizationAnalyticsResponse.OrgRepoStat s = new OrganizationAnalyticsResponse.OrgRepoStat();
                    s.setName(str(r.get("name")));
                    s.setDescription(str(r.get("description")));
                    s.setLanguage(str(r.get("language")));
                    s.setStars(num(r.get("stargazers_count")));
                    s.setForks(num(r.get("forks_count")));
                    s.setPushedAt(str(r.get("pushed_at")));
                    return s;
                })
                .collect(Collectors.toList()));

        if (sampled.isEmpty()) {
            res.setLanguagesCount(0);
            res.setSummary("No public repository data available for this organization.");
            res.setInsight("The organization has no public repos to analyze, or the name could not be resolved.");
            return res;
        }

        // ── Language stack (parallel, cached, byte-weighted — reuse integration service) ──
        List<RepositoryResponse> repoResponses = sampled.stream()
                .map(this::toRepoResponse)
                .collect(Collectors.toList());

        List<GitHubIntegrationService.LanguageBreakdown> languages =
                integrationService.getWeightedLanguageBreakdown(repoResponses);
        List<GitHubIntegrationService.GitHubContributor> contributors =
                integrationService.getAggregateContributors(repoResponses);

        res.setLanguages(languages.stream()
                .map(l -> {
                    OrganizationAnalyticsResponse.LanguageStat ls = new OrganizationAnalyticsResponse.LanguageStat();
                    ls.setLanguage(l.language());
                    ls.setPercentage(l.percentage());
                    ls.setRepos(l.repos());
                    return ls;
                })
                .collect(Collectors.toList()));
        res.setLanguagesCount(res.getLanguages().size());
        res.setTopContributors(contributors);

        // ── Deterministic team summary ──
        res.setSummary(buildSummary(res));
        res.setInsight(buildInsight(res));
        return res;
    }

    private RepositoryResponse toRepoResponse(Map<String, Object> r) {
        RepositoryResponse repo = new RepositoryResponse();
        repo.setName(str(r.get("name")));
        repo.setFullName(str(r.get("full_name")));
        repo.setStars(num(r.get("stargazers_count")));
        repo.setForks(num(r.get("forks_count")));
        repo.setFork(Boolean.TRUE.equals(r.get("fork")));
        repo.setArchived(Boolean.TRUE.equals(r.get("archived")));
        return repo;
    }

    private boolean recentlyPushed(Map<String, Object> r) {
        String pushed = str(r.get("pushed_at"));
        if (pushed == null) return false;
        try {
            Instant pushedAt = ZonedDateTime.parse(pushed).toInstant();
            return Duration.between(pushedAt, Instant.now()).toDays() <= 90;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildSummary(OrganizationAnalyticsResponse res) {
        if (res.getTotalRepos() == 0) {
            return "No public repository data available for this organization.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "This organization maintains %d public %s with %d stars and %d forks across %d languages. ",
                res.getTotalRepos(), res.getTotalRepos() == 1 ? "repository" : "repositories",
                res.getTotalStars(), res.getTotalForks(), res.getLanguagesCount()));
        sb.append(String.format(
                "%.0f%% of sampled repositories were pushed to within the last 90 days — the team is %s. ",
                res.getTotalRepos() == 0 ? 0 : res.getActiveRepos() * 100.0 / res.getTotalRepos(),
                res.getActiveRepos() > res.getTotalRepos() / 2 ? "actively maintained" : "less active recently"));
        return sb.toString();
    }

    private String buildInsight(OrganizationAnalyticsResponse res) {
        if (res.getTotalRepos() == 0) return "";
        String topLang = res.getLanguages().isEmpty()
                ? "mixed"
                : res.getLanguages().get(0).getLanguage();
        if (res.getAverageStars() >= 100) {
            return "High-impact team: average repo popularity is strong, led by " + topLang + ".";
        } else if (res.getAverageStars() >= 10) {
            return "Growing team with a healthy public footprint, led by " + topLang + ".";
        }
        return "Early-stage or internal-first team — public repos are modest in size, led by " + topLang + ".";
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
