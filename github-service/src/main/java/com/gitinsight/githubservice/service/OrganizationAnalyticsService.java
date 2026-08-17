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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final int MAX_TEAM_ACTIVITY_REPOS = 8; // repos sampled for 30/90-day activity
    private static final int ACTIVITY_PER_PAGE = 100;     // count cap per window per repo
    private static final Duration PROFILE_TTL = Duration.ofHours(1);
    private static final Duration OVERVIEW_TTL = Duration.ofMinutes(15);

    /**
     * Team-activity fetches (commits + PRs/issues per repo, per window) run on
     * virtual threads so N round-trips collapse to ~1; per-repo failures degrade
     * to zeros instead of failing the overview.
     */
    private static final ExecutorService PARALLEL_FETCHER = Executors.newVirtualThreadPerTaskExecutor();

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
                .defaultHeader("User-Agent", "GitInsight-AI/1.0")
                // Explicit timeouts — never rely on the JDK/OS default.
                .requestFactory(com.gitinsight.githubservice.config.HttpClients.githubFactory());

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

        // ── Repository health (over the fetched set — the 100 most-recently-pushed repos) ──
        long archivedCount = repos.stream().filter(r -> Boolean.TRUE.equals(r.get("archived"))).count();
        long inactiveCount = repos.stream()
                .filter(r -> !Boolean.TRUE.equals(r.get("fork")))
                .filter(r -> !Boolean.TRUE.equals(r.get("archived")))
                .filter(r -> !recentlyPushed(r))
                .count();
        long forkCount = repos.stream().filter(r -> Boolean.TRUE.equals(r.get("fork"))).count();
        res.setArchivedRepos((int) archivedCount);
        res.setInactiveRepos((int) inactiveCount);
        res.setForkRatio(repos.isEmpty() ? 0 : Math.round(forkCount * 1000.0 / repos.size()) / 10.0);

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
        res.setTopContributors(toContributorStats(contributors));
        res.setActiveContributors(contributors.size());

        // ── Team activity (parallel, best-effort; commits authored in window,
        //    PRs/issues last updated in window per GitHub's `since` filter) ──
        res.setTeamActivity(fetchTeamActivity(effective));

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

    // ═══════════════════════════════════════════════════════════════
    // TEAM ACTIVITY (commits / PRs / issues over 30 & 90 days)
    // ═══════════════════════════════════════════════════════════════

    private OrganizationAnalyticsResponse.TeamActivity fetchTeamActivity(List<Map<String, Object>> topRepos) {
        OrganizationAnalyticsResponse.TeamActivity total = new OrganizationAnalyticsResponse.TeamActivity();
        List<Map<String, Object>> sampled = topRepos.stream()
                .limit(MAX_TEAM_ACTIVITY_REPOS)
                .collect(Collectors.toList());
        if (sampled.isEmpty()) return total;

        List<CompletableFuture<OrganizationAnalyticsResponse.TeamActivity>> futures = sampled.stream()
                .map(r -> CompletableFuture.supplyAsync(() -> repoTeamActivity(r), PARALLEL_FETCHER))
                .collect(Collectors.toList());

        for (CompletableFuture<OrganizationAnalyticsResponse.TeamActivity> f : futures) {
            OrganizationAnalyticsResponse.TeamActivity t = f.join(); // repoTeamActivity never throws
            total.setCommits30d(total.getCommits30d() + t.getCommits30d());
            total.setCommits90d(total.getCommits90d() + t.getCommits90d());
            total.setPullRequests30d(total.getPullRequests30d() + t.getPullRequests30d());
            total.setPullRequests90d(total.getPullRequests90d() + t.getPullRequests90d());
            total.setIssues30d(total.getIssues30d() + t.getIssues30d());
            total.setIssues90d(total.getIssues90d() + t.getIssues90d());
        }
        return total;
    }

    /**
     * Per-repo window counts; PRs and issues are split from one {@code /issues}
     * call (PRs appear with a {@code pull_request} key). Best-effort: any failure
     * returns a zeroed TeamActivity so the overview still renders.
     */
    private OrganizationAnalyticsResponse.TeamActivity repoTeamActivity(Map<String, Object> repo) {
        OrganizationAnalyticsResponse.TeamActivity t = new OrganizationAnalyticsResponse.TeamActivity();
        String fullName = str(repo.get("full_name"));
        if (fullName == null || !fullName.contains("/")) return t;
        String[] parts = fullName.split("/", 2);
        try {
            t.setCommits30d(countCommitsSince(parts[0], parts[1], 30));
            t.setCommits90d(countCommitsSince(parts[0], parts[1], 90));
            int[] pr30 = countPrsAndIssuesSince(parts[0], parts[1], 30);
            int[] pr90 = countPrsAndIssuesSince(parts[0], parts[1], 90);
            t.setPullRequests30d(pr30[0]);
            t.setIssues30d(pr30[1]);
            t.setPullRequests90d(pr90[0]);
            t.setIssues90d(pr90[1]);
        } catch (Exception e) {
            log.warn("Team activity fetch failed for {}: {}", fullName, e.getMessage());
        }
        return t;
    }

    private int countCommitsSince(String owner, String repo, int days) {
        List<Map<String, Object>> commits = restClient.get()
                .uri("/repos/{owner}/{repo}/commits?since={since}&per_page={perPage}",
                        owner, repo, Instant.now().minus(Duration.ofDays(days)).toString(), ACTIVITY_PER_PAGE)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return commits == null ? 0 : commits.size();
    }

    private int[] countPrsAndIssuesSince(String owner, String repo, int days) {
        List<Map<String, Object>> items = restClient.get()
                .uri("/repos/{owner}/{repo}/issues?state=all&since={since}&per_page={perPage}",
                        owner, repo, Instant.now().minus(Duration.ofDays(days)).toString(), ACTIVITY_PER_PAGE)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        int prs = 0;
        int issues = 0;
        if (items != null) {
            for (Map<String, Object> item : items) {
                if (Boolean.TRUE.equals(item.get("pull_request"))) prs++;
                else issues++;
            }
        }
        return new int[]{prs, issues};
    }

    private List<OrganizationAnalyticsResponse.ContributorStat> toContributorStats(
            List<GitHubIntegrationService.GitHubContributor> contributors) {
        int total = contributors.stream().mapToInt(GitHubIntegrationService.GitHubContributor::contributions).sum();
        return contributors.stream()
                .map(c -> {
                    OrganizationAnalyticsResponse.ContributorStat s = new OrganizationAnalyticsResponse.ContributorStat();
                    s.setLogin(c.login());
                    s.setContributions(c.contributions());
                    s.setAvatarUrl(c.avatarUrl());
                    s.setContributionPercent(total == 0 ? 0 : Math.round(c.contributions() * 1000.0 / total) / 10.0);
                    return s;
                })
                .collect(Collectors.toList());
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
        OrganizationAnalyticsResponse.TeamActivity ta = res.getTeamActivity();
        if (ta != null && ta.getCommits30d() + ta.getPullRequests30d() + ta.getIssues30d() > 0) {
            sb.append(String.format(
                    "Across the top sampled repos the team logged %d commits, %d pull requests and %d issues in the last 30 days. ",
                    ta.getCommits30d(), ta.getPullRequests30d(), ta.getIssues30d()));
        }
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
