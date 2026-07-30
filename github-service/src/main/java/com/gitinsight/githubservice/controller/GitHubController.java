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

        // 1. Repository Health Score (avg of all health scores)
        double avgHealth = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getHealthScore)
                .average()
                .orElse(0);

        // 2. Total Stars
        int totalStars = effectiveRepos.stream().mapToInt(RepositoryResponse::getStars).sum();

        // 3. Language Diversity
        long languageCount = effectiveRepos.stream()
                .map(RepositoryResponse::getLanguage)
                .filter(l -> l != null && !l.isEmpty())
                .distinct()
                .count();

        // 4. Popularity Score
        double avgPopularity = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getPopularityScore)
                .average()
                .orElse(0);

        // 5. Maintenance Score
        double avgMaintenance = effectiveRepos.stream()
                .mapToInt(RepositoryResponse::getMaintenanceScore)
                .average()
                .orElse(0);

        // Calculate weighted overall score (0-100)
        int overallScore = (int) Math.round(
                avgHealth * 0.30 +
                Math.min(totalStars / 10.0, 100) * 0.20 +
                Math.min(languageCount * 10, 100) * 0.15 +
                avgPopularity * 0.20 +
                avgMaintenance * 0.15
        );

        DeveloperScoreResponse score = new DeveloperScoreResponse();
        score.setUsername(username);
        score.setOverallScore(Math.min(overallScore, 100));
        score.setTotalStars(totalStars);
        score.setTotalForks(effectiveRepos.stream().mapToInt(RepositoryResponse::getForks).sum());
        score.setTotalRepositories(effectiveRepos.size());
        score.setLanguageCount((int) languageCount);
        score.setLanguages(effectiveRepos.stream()
                .map(RepositoryResponse::getLanguage)
                .filter(l -> l != null && !l.isEmpty())
                .distinct()
                .toArray(String[]::new));
        score.setAvgHealthScore((int) Math.round(avgHealth));
        score.setAvgPopularityScore((int) Math.round(avgPopularity));
        score.setAvgMaintenanceScore((int) Math.round(avgMaintenance));

        // Determine level
        if (overallScore >= 80) score.setLevel("Expert");
        else if (overallScore >= 60) score.setLevel("Advanced");
        else if (overallScore >= 40) score.setLevel("Intermediate");
        else if (overallScore >= 20) score.setLevel("Beginner");
        else score.setLevel("Newcomer");

        return score;
    }
}
