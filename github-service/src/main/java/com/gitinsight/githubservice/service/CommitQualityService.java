package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse.RepoCommitStat;
import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse.WeeklyActivity;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 5 — Commit & Code Quality Analysis.
 * <p>
 * Fetches real commit history (message + author date + additions/deletions stats)
 * for the developer's own repositories and computes independent, deterministic metrics:
 * <ul>
 *   <li>commit message quality (conventional prefixes, descriptive length)</li>
 *   <li>conventional commit rate (feat/fix/docs/... prefixes)</li>
 *   <li>commit size balance (reviewable, not monolithic)</li>
 *   <li>commit frequency (commits per week)</li>
 *   <li>weekly activity distribution (last 12 weeks)</li>
 * </ul>
 * All metrics are combined only in {@link #analyze(String, java.util.List)}.
 * Missing data falls back gracefully without breaking the calculation.
 */
@Service
public class CommitQualityService {

    private static final Logger log = LoggerFactory.getLogger(CommitQualityService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private static final Set<String> CONVENTIONAL_TYPES = Set.of(
            "feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore", "revert"
    );

    private static final int MAX_REPOS = 15;          // cap API calls per request
    private static final int MAX_COMMITS_PER_REPO = 50;

    private final RestClient restClient;
    private final GitHubCacheService cacheService;

    record CommitSample(String message, String date, int additions, int deletions) {}

    @Autowired
    public CommitQualityService(
            @Value("${github.token:}") String githubToken,
            GitHubCacheService cacheService) {
        this(buildRestClient(githubToken), cacheService);
    }

    /**
     * Package-private constructor for unit tests — allows injecting a mock RestClient.
     */
    CommitQualityService(RestClient restClient, GitHubCacheService cacheService) {
        this.restClient = restClient;
        this.cacheService = cacheService;
    }

    private static RestClient buildRestClient(String githubToken) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0")
                // Explicit timeouts — never rely on the JDK/OS default.
                .requestFactory(com.gitinsight.githubservice.config.HttpClients.githubFactory());

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }
        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    public CommitAnalyticsResponse analyze(String username, List<RepositoryResponse> repos) {
        String cacheKey = "commit-quality:" + username;
        CommitAnalyticsResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        if (repos == null || repos.isEmpty()) {
            return CommitAnalyticsResponse.empty(username);
        }

        // Filter: only original, non-archived repositories (same rules as ScoringEngine)
        List<RepositoryResponse> effective = repos.stream()
                .filter(r -> !r.isFork())
                .filter(r -> !r.isArchived())
                .collect(Collectors.toList());
        if (effective.isEmpty()) {
            return CommitAnalyticsResponse.empty(username);
        }

        List<CommitSample> allCommits = new ArrayList<>();
        List<RepoCommitStat> repoStats = new ArrayList<>();
        int analyzed = 0;

        for (RepositoryResponse repo : effective) {
            if (analyzed >= MAX_REPOS) break;
            analyzed++;

            List<CommitSample> repoCommits = fetchCommits(username, repo);
            if (!repoCommits.isEmpty()) {
                allCommits.addAll(repoCommits);

                RepoCommitStat stat = new RepoCommitStat();
                stat.setRepoName(repo.getName());
                stat.setTotalCommits(repoCommits.size());
                stat.setAdditions(repoCommits.stream().mapToInt(CommitSample::additions).sum());
                stat.setDeletions(repoCommits.stream().mapToInt(CommitSample::deletions).sum());
                stat.setMessageQuality((int) Math.round(
                        repoCommits.stream().mapToInt(c -> scoreMessageQuality(c.message())).average().orElse(0)));
                repoStats.add(stat);
            }
        }

        CommitAnalyticsResponse response = buildResponse(username, allCommits, repoStats);
        cacheService.put(cacheKey, response, java.time.Duration.ofMinutes(10));
        return response;
    }

    // ═══════════════════════════════════════════════════════════════
    // GITHUB FETCH
    // ═══════════════════════════════════════════════════════════════

    private List<CommitSample> fetchCommits(String username, RepositoryResponse repo) {
        String fullName = repo.getFullName();
        if (fullName == null || !fullName.contains("/")) return List.of();
        String[] parts = fullName.split("/", 2);

        try {
            List<Map<String, Object>> commits = restClient.get()
                    .uri("/repos/{owner}/{repo}/commits?author={author}&per_page={perPage}",
                            parts[0], parts[1], username, MAX_COMMITS_PER_REPO)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (commits == null) return List.of();

            List<CommitSample> samples = new ArrayList<>();
            for (Map<String, Object> c : commits) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> commitInfo = (Map<String, Object>) c.get("commit");
                    String message = commitInfo != null ? (String) commitInfo.get("message") : "";
                    String date = commitInfo != null && commitInfo.get("author") instanceof Map<?, ?> a
                            ? (String) a.get("date") : "";

                    int additions = 0, deletions = 0;
                    Object statsObj = c.get("stats");
                    if (statsObj instanceof Map<?, ?> stats) {
                        additions = stats.get("additions") instanceof Number n ? n.intValue() : 0;
                        deletions = stats.get("deletions") instanceof Number n ? n.intValue() : 0;
                    }

                    if (StringUtils.hasText(message)) {
                        samples.add(new CommitSample(message, date, additions, deletions));
                    }
                } catch (Exception ignored) {
                    // skip malformed commit
                }
            }
            return samples;
        } catch (Exception e) {
            log.warn("Failed to fetch commits for {}/{}: {}", parts[0], parts[1], e.getMessage());
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // METRIC: COMMIT MESSAGE QUALITY (0-100)
    // ═══════════════════════════════════════════════════════════════

    int scoreMessageQuality(String message) {
        if (message == null || message.isBlank()) return 0;

        String firstLine = message.lines().findFirst().orElse("").trim();
        int score = 20; // base: a commit exists with a message

        // Conventional prefix (feat: / fix(scope): / chore: ...)
        if (hasConventionalPrefix(firstLine)) score += 30;

        // Descriptive length
        if (firstLine.length() >= 20) score += 20;
        else if (firstLine.length() >= 10) score += 10;

        // Sentence-like: has a verb or descriptive words (rough heuristic)
        if (firstLine.split(" ").length >= 4) score += 15;

        // Body present (multi-line message)
        if (message.lines().count() > 2) score += 15;

        return Math.max(0, Math.min(score, 100));
    }

    boolean hasConventionalPrefix(String firstLine) {
        // feat: / feat(scope): / feat!: / fix(scope):
        String lower = firstLine.toLowerCase();
        for (String type : CONVENTIONAL_TYPES) {
            if (lower.startsWith(type + ":") || lower.startsWith(type + "(") || lower.startsWith(type + "!")) {
                return true;
            }
        }
        return false;
    }

    String extractConventionalType(String firstLine) {
        String lower = firstLine.toLowerCase();
        for (String type : CONVENTIONAL_TYPES) {
            if (lower.startsWith(type + ":") || lower.startsWith(type + "(") || lower.startsWith(type + "!")) {
                return type;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // METRIC: COMMIT SIZE BALANCE (0-100)
    // ═══════════════════════════════════════════════════════════════

    int scoreCommitSize(List<CommitSample> commits) {
        if (commits.isEmpty()) return 0;
        double avgChanges = commits.stream()
                .mapToInt(c -> c.additions() + c.deletions())
                .average().orElse(0);

        // Small, reviewable commits (1-400 changed lines) score best;
        // monolithic mega-commits (>1000 lines) and empty commits score low.
        if (avgChanges >= 5 && avgChanges <= 400) return 100;
        if (avgChanges > 0 && avgChanges <= 800) return 70;
        if (avgChanges > 800 && avgChanges <= 1500) return 40;
        if (avgChanges > 1500) return 15;
        return 30; // near-empty commits
    }

    // ═══════════════════════════════════════════════════════════════
    // AGGREGATION LAYER
    // ═══════════════════════════════════════════════════════════════

    CommitAnalyticsResponse buildResponse(
            String username, List<CommitSample> allCommits, List<RepoCommitStat> repoStats) {

        CommitAnalyticsResponse res = new CommitAnalyticsResponse();
        res.setUsername(username);
        res.setReposAnalyzed(repoStats.size());

        if (allCommits.isEmpty()) {
            return CommitAnalyticsResponse.empty(username);
        }

        // ── Raw aggregates ──
        int total = allCommits.size();
        res.setTotalCommits(total);
        res.setTotalAdditions(allCommits.stream().mapToInt(CommitSample::additions).sum());
        res.setTotalDeletions(allCommits.stream().mapToInt(CommitSample::deletions).sum());

        // ── Commits per week (over the span of the sampled history) ──
        double spanWeeks = computeSpanWeeks(allCommits);
        res.setCommitsPerWeek(Math.round((total / Math.max(spanWeeks, 1.0)) * 10.0) / 10.0);

        // ── Commit message quality ──
        int avgMessageQuality = (int) Math.round(
                allCommits.stream().mapToInt(c -> scoreMessageQuality(c.message())).average().orElse(0));
        res.setCommitMessageQuality(avgMessageQuality);

        // ── Conventional commit rate (0-100) ──
        long conventional = allCommits.stream()
                .map(CommitSample::message)
                .map(m -> m.lines().findFirst().orElse("").trim())
                .filter(this::hasConventionalPrefix)
                .count();
        res.setConventionalCommitRate((int) Math.round(conventional * 100.0 / total));

        // ── Average message length ──
        res.setAverageMessageLength(Math.round(
                allCommits.stream().mapToInt(c -> c.message().trim().length()).average().orElse(0) * 10.0) / 10.0);

        // ── Commit size balance ──
        res.setCommitSizeScore(scoreCommitSize(allCommits));

        // ── Top commit types ──
        Map<String, Long> typeCounts = new HashMap<>();
        for (CommitSample c : allCommits) {
            String type = extractConventionalType(c.message().lines().findFirst().orElse("").trim());
            if (type != null) typeCounts.merge(type, 1L, Long::sum);
        }
        // Deterministic ordering: count desc, then name asc (stable across runs)
        res.setTopCommitTypes(typeCounts.entrySet().stream()
                .sorted((a, b) -> {
                    int byCount = Long.compare(b.getValue(), a.getValue());
                    return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
                })
                .map(Map.Entry::getKey)
                .limit(6)
                .collect(Collectors.toList()));

        // ── Weekly activity (last 12 ISO weeks) ──
        res.setWeeklyActivity(buildWeeklyActivity(allCommits));

        // ── Per-repo breakdown ──
        res.setRepoBreakdown(repoStats);

        // ── Normalized code quality score (0-100) ──
        // Weights: message quality 40% · conventional rate 20% · commit size 20% · frequency 20%
        double frequencyScore = Math.min(res.getCommitsPerWeek() / 5.0, 1.0) * 100;
        int qualityScore = (int) Math.round(
                avgMessageQuality * 0.40 +
                res.getConventionalCommitRate() * 0.20 +
                res.getCommitSizeScore() * 0.20 +
                frequencyScore * 0.20
        );
        res.setCodeQualityScore(Math.max(0, Math.min(100, qualityScore)));

        // ── Explainability ──
        res.setTrend(qualityScore >= 60 ? "up" : qualityScore >= 35 ? "stable" : "down");

        if (qualityScore >= 70) {
            res.setExplanation("Excellent commit hygiene: descriptive, conventional commit messages with reviewable, balanced commit sizes.");
            res.setImprovementSuggestion("Keep the momentum — add body details to larger commits and continue using conventional prefixes.");
        } else if (qualityScore >= 40) {
            res.setExplanation("Decent commit activity, but message quality and commit sizes can be improved for better reviewability.");
            res.setImprovementSuggestion("Adopt Conventional Commits (feat:, fix:, docs:) and split large changes into smaller logical commits.");
        } else {
            res.setExplanation("Commit history shows limited volume or low message quality, which makes code changes hard to review and audit.");
            res.setImprovementSuggestion("Commit more frequently with clear messages describing what and why, using conventional prefixes.");
        }

        return res;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    double computeSpanWeeks(List<CommitSample> commits) {
        try {
            List<Instant> dates = commits.stream()
                    .map(CommitSample::date)
                    .filter(StringUtils::hasText)
                    .map(d -> {
                        try { return ZonedDateTime.parse(d).toInstant(); }
                        catch (Exception e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (dates.isEmpty()) return 1;
            Instant min = Collections.min(dates);
            Instant max = Collections.max(dates);
            return Math.max(1, java.time.Duration.between(min, max).toDays() / 7.0);
        } catch (Exception e) {
            return 1;
        }
    }

    List<WeeklyActivity> buildWeeklyActivity(List<CommitSample> commits) {
        // Build a zero-filled map for the last 12 ISO weeks (ending this week)
        Map<String, Integer> byWeek = new LinkedHashMap<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime currentWeekStart = now.with(DayOfWeek.MONDAY)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        for (int i = 11; i >= 0; i--) {
            ZonedDateTime weekStart = currentWeekStart.minusWeeks(i);
            String label = weekStart.getYear() + "-W" +
                    String.format("%02d", weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            byWeek.put(label, 0);
        }

        for (CommitSample c : commits) {
            if (!StringUtils.hasText(c.date())) continue;
            try {
                ZonedDateTime d = ZonedDateTime.parse(c.date());
                ZonedDateTime weekStart = d.with(DayOfWeek.MONDAY)
                        .withHour(0).withMinute(0).withSecond(0).withNano(0);
                String label = weekStart.getYear() + "-W" +
                        String.format("%02d", weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                byWeek.merge(label, 1, Integer::sum);
            } catch (Exception ignored) {
                // skip unparseable dates
            }
        }

        List<WeeklyActivity> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byWeek.entrySet()) {
            WeeklyActivity wa = new WeeklyActivity();
            wa.setWeek(e.getKey());
            wa.setCommits(e.getValue());
            result.add(wa);
        }
        return result;
    }
}
