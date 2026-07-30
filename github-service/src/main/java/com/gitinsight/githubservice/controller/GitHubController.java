package com.gitinsight.githubservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.service.GitHubService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/profile/{username}")
    public ApiResponse<GitHubProfileResponse> getProfile(
            @PathVariable String username) {

        GitHubProfileResponse profile = gitHubService.getProfile(username);

        return new ApiResponse<>(
                true,
                "GitHub profile fetched successfully.",
                profile
        );
    }

    @GetMapping("/{username}/repos")
    public ApiResponse<List<RepositoryResponse>> getRepositories(
            @PathVariable String username) {

        List<RepositoryResponse> repos = gitHubService.getRepositories(username);

        return new ApiResponse<>(
                true,
                "Repositories fetched successfully.",
                repos
        );
    }

    @GetMapping("/{username}/score")
    public ApiResponse<DeveloperScoreResponse> getDeveloperScore(
            @PathVariable String username) {

        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        DeveloperScoreResponse score = calculateDeveloperScore(username, repos);

        return new ApiResponse<>(
                true,
                "Developer score calculated successfully.",
                score
        );
    }

    private DeveloperScoreResponse calculateDeveloperScore(String username, List<RepositoryResponse> repos) {
        if (repos.isEmpty()) {
            return DeveloperScoreResponse.empty(username);
        }

        // Filter out forks for a better representation of original work
        List<RepositoryResponse> originalRepos = repos.stream()
                .filter(r -> !r.isFork())
                .collect(java.util.stream.Collectors.toList());

        List<RepositoryResponse> effectiveRepos = originalRepos.isEmpty() ? repos : originalRepos;

        int repoCount = effectiveRepos.size();

        // ═══════════════════════════════════════════════
        // 1. REPOSITORY HEALTH SCORE (avg of all health scores)
        // ═══════════════════════════════════════════════
        double avgHealth = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getHealthScore)
                .average()
                .orElse(0);

        // ═══════════════════════════════════════════════
        // 2. CONTRIBUTION RECENCY — what % of repos updated recently
        // ═══════════════════════════════════════════════
        long reposActiveLast30 = effectiveRepos.stream()
                .filter(r -> {
                    try {
                        java.time.Instant pushed = java.time.ZonedDateTime.parse(r.getPushedAt()).toInstant();
                        return java.time.Duration.between(pushed, java.time.Instant.now()).toDays() < 30;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

        long reposActiveLast90 = effectiveRepos.stream()
                .filter(r -> {
                    try {
                        java.time.Instant pushed = java.time.ZonedDateTime.parse(r.getPushedAt()).toInstant();
                        return java.time.Duration.between(pushed, java.time.Instant.now()).toDays() < 90;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

        double activeLast30Ratio = repoCount > 0 ? (double) reposActiveLast30 / repoCount : 0;
        double activeLast90Ratio = repoCount > 0 ? (double) reposActiveLast90 / repoCount : 0;

        // Contribution Recency Score (0-100)
        int contributionRecencyScore = (int) Math.round(
                activeLast30Ratio * 60 +
                activeLast90Ratio * 40
        );

        // ═══════════════════════════════════════════════
        // 3. COMMIT FREQUENCY — how regularly does this dev push
        // ═══════════════════════════════════════════════
        double avgDaysSincePush = effectiveRepos.stream()
                .mapToLong(r -> {
                    try {
                        java.time.Instant pushed = java.time.ZonedDateTime.parse(r.getPushedAt()).toInstant();
                        return java.time.Duration.between(pushed, java.time.Instant.now()).toDays();
                    } catch (Exception e) {
                        return 365;
                    }
                })
                .average()
                .orElse(365);

        // Commit Frequency Score (0-100) — lower avg days = higher score
        int commitFrequencyScore;
        if (avgDaysSincePush <= 7) commitFrequencyScore = 90;
        else if (avgDaysSincePush <= 14) commitFrequencyScore = 80;
        else if (avgDaysSincePush <= 30) commitFrequencyScore = 65;
        else if (avgDaysSincePush <= 60) commitFrequencyScore = 50;
        else if (avgDaysSincePush <= 90) commitFrequencyScore = 35;
        else if (avgDaysSincePush <= 180) commitFrequencyScore = 20;
        else commitFrequencyScore = 5;

        // ═══════════════════════════════════════════════
        // 4. CONTRIBUTION CONSISTENCY — repos evenly maintained
        // ═══════════════════════════════════════════════
        int reposWithRecentActivity = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getActivityScore)
                .filter(s -> s >= 30)
                .toArray()
                .length;

        double consistencyRatio = repoCount > 0 ? (double) reposWithRecentActivity / repoCount : 0;
        int consistencyScore = (int) Math.round(consistencyRatio * 100);

        // ═══════════════════════════════════════════════
        // 5. LANGUAGE DIVERSITY
        // ═══════════════════════════════════════════════
        long languageCount = effectiveRepos.stream()
                .map(RepositoryResponse::getLanguage)
                .filter(l -> l != null && !l.isEmpty())
                .distinct()
                .count();

        int languageScore = (int) Math.min(languageCount * 12, 100);

        // ═══════════════════════════════════════════════
        // 6. POPULARITY (stars) — still counts, but less dominant
        // ═══════════════════════════════════════════════
        int totalStars = effectiveRepos.stream().mapToInt(RepositoryResponse::getStars).sum();
        double avgPopularity = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getPopularityScore)
                .average()
                .orElse(0);

        // ═══════════════════════════════════════════════
        // 7. MAINTAINED CODE QUALITY
        // ═══════════════════════════════════════════════
        double avgMaintenance = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getMaintenanceScore)
                .average()
                .orElse(0);

        // ═══════════════════════════════════════════════
        // FINAL SCORE — rebalanced to favor contributions
        // ═══════════════════════════════════════════════
        int overallScore = (int) Math.round(
                avgHealth * 0.15 +          // Repository quality
                contributionRecencyScore * 0.20 +  // Recently active repos ← NEW!
                commitFrequencyScore * 0.20 +      // How often they push ← NEW!
                consistencyScore * 0.10 +           // Evenly maintained ← NEW!
                languageScore * 0.10 +              // Language diversity
                Math.min(totalStars / 10.0, 100) * 0.10 +  // Stars (reduced)
                avgPopularity * 0.05 +              // Popularity (reduced)
                avgMaintenance * 0.10               // Maintenance
        );

        overallScore = Math.min(overallScore, 100);

        DeveloperScoreResponse score = new DeveloperScoreResponse();
        score.setUsername(username);
        score.setOverallScore(overallScore);
        score.setTotalStars(totalStars);
        score.setTotalForks(effectiveRepos.stream().mapToInt(RepositoryResponse::getForks).sum());
        score.setTotalRepositories(repoCount);
        score.setLanguageCount((int) languageCount);
        score.setLanguages(effectiveRepos.stream()
                .map(RepositoryResponse::getLanguage)
                .filter(l -> l != null && !l.isEmpty())
                .distinct()
                .toArray(String[]::new));
        score.setAvgHealthScore((int) Math.round(avgHealth));
        score.setAvgPopularityScore((int) Math.round(avgPopularity));
        score.setAvgMaintenanceScore((int) Math.round(avgMaintenance));

        // New contribution metrics in the response
        score.setContributionRecencyScore(contributionRecencyScore);
        score.setCommitFrequencyScore(commitFrequencyScore);
        score.setConsistencyScore(consistencyScore);

        // Determine level with adjusted thresholds
        if (overallScore >= 80) score.setLevel("Expert");
        else if (overallScore >= 60) score.setLevel("Advanced");
        else if (overallScore >= 40) score.setLevel("Intermediate");
        else if (overallScore >= 20) score.setLevel("Beginner");
        else score.setLevel("Newcomer");

        return score;
    }
}
