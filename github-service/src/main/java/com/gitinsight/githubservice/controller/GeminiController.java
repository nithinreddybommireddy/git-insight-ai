package com.gitinsight.githubservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.request.CommitDiffReviewRequest;
import com.gitinsight.githubservice.dto.request.JobMatchRequest;
import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffReviewResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.JobMatchAiResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.service.CommitQualityService;
import com.gitinsight.githubservice.service.DeveloperScoreService;
import com.gitinsight.githubservice.service.GeminiService;
import com.gitinsight.githubservice.service.GitHubService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class GeminiController {

    private final GeminiService geminiService;
    private final GitHubService gitHubService;
    private final DeveloperScoreService developerScoreService;
    private final CommitQualityService commitQualityService;

    public GeminiController(GeminiService geminiService, GitHubService gitHubService,
                            DeveloperScoreService developerScoreService,
                            CommitQualityService commitQualityService) {
        this.geminiService = geminiService;
        this.gitHubService = gitHubService;
        this.developerScoreService = developerScoreService;
        this.commitQualityService = commitQualityService;
    }

    /**
     * Health check - indicates whether AI is enabled.
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        return new ApiResponse<>(true, "AI service status",
                Map.of("enabled", geminiService.isEnabled(),
                       "provider", "Google Gemini",
                       "model", "gemini-3.1-flash-lite"));
    }

    /**
     * Generate a comprehensive developer summary.
     */
    @GetMapping("/summary/{username}")
    public ApiResponse<String> getDeveloperSummary(@PathVariable String username) {
        DeveloperScoreResponse score = developerScoreService.getScore(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        String summary = geminiService.generateDeveloperSummary(username, score, profile, repos);
        return new ApiResponse<>(true, "AI developer summary generated", summary);
    }

    /**
     * Review a specific repository.
     */
    @GetMapping("/review/{username}/{repoName}")
    public ApiResponse<String> getRepositoryReview(
            @PathVariable String username,
            @PathVariable String repoName) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        DeveloperScoreResponse score = developerScoreService.getScore(username);

        RepositoryResponse repo = repos.stream()
                .filter(r -> r.getName().equalsIgnoreCase(repoName))
                .findFirst()
                .orElse(null);

        if (repo == null) {
            return new ApiResponse<>(false, "Repository not found", null);
        }

        String review = geminiService.generateRepositoryReview(username, repo, score);
        return new ApiResponse<>(true, "AI repository review generated", review);
    }

    /**
     * Detect and analyze developer skills.
     */
    @GetMapping("/skills/{username}")
    public ApiResponse<String> getSkillAnalysis(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        DeveloperScoreResponse score = developerScoreService.getScore(username);

        String analysis = geminiService.generateSkillAnalysis(username, score, repos);
        return new ApiResponse<>(true, "AI skill analysis generated", analysis);
    }

    /**
     * Generate a personalized career roadmap.
     */
    @GetMapping("/roadmap/{username}")
    public ApiResponse<String> getCareerRoadmap(@PathVariable String username) {
        DeveloperScoreResponse score = developerScoreService.getScore(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        String roadmap = geminiService.generateCareerRoadmap(username, score, profile, repos);
        return new ApiResponse<>(true, "AI career roadmap generated", roadmap);
    }

    /**
     * Interview readiness assessment.
     */
    @GetMapping("/interview/{username}")
    public ApiResponse<String> getInterviewReadiness(@PathVariable String username) {
        DeveloperScoreResponse score = developerScoreService.getScore(username);
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        String assessment = geminiService.generateInterviewReadiness(username, score, repos);
        return new ApiResponse<>(true, "AI interview readiness assessment generated", assessment);
    }

    /**
     * AI comparison between two developers.
     */
    @GetMapping("/compare/{user1}/{user2}")
    public ApiResponse<String> getAComparison(
            @PathVariable String user1,
            @PathVariable String user2) {
        DeveloperScoreResponse score1 = developerScoreService.getScore(user1);
        GitHubProfileResponse profile1 = gitHubService.getProfile(user1);
        DeveloperScoreResponse score2 = developerScoreService.getScore(user2);
        GitHubProfileResponse profile2 = gitHubService.getProfile(user2);

        String comparison = geminiService.generateComparison(
                user1, score1, profile1, user2, score2, profile2);
        return new ApiResponse<>(true, "AI comparison generated", comparison);
    }

    /**
     * Phase 5 — AI code quality review based on real commit history.
     */
    @GetMapping("/code-quality/{username}")
    public ApiResponse<Map<String, Object>> getCodeQualityReview(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        DeveloperScoreResponse score = developerScoreService.getScore(username);
        CommitAnalyticsResponse analytics = commitQualityService.analyze(username, repos);

        String review = geminiService.generateCodeQualityReview(username, analytics, score);

        return new ApiResponse<>(true, "AI code quality review generated",
                Map.of("analytics", analytics,
                       "aiReview", review != null ? review : "AI review not available"));
    }

    /**
     * Phase 6 — AI commit-diff code-quality review (per-file).
     * Body: {@code {username, commits: [{sha, message, repoName, files: [{filename, status, additions, deletions, patch}]}]}}
     */
    @PostMapping("/commit-diff-review")
    public ApiResponse<CommitDiffReviewResponse> getCommitDiffReview(@RequestBody CommitDiffReviewRequest request) {
        CommitDiffReviewResponse review = geminiService.generateCommitDiffReview(request);
        return new ApiResponse<>(true, "AI commit-diff review generated", review);
    }

    /**
     * AI job-match: per-candidate fit explanations for a job description.
     * Called by auth-service's recruiter flow with the deterministic match data.
     */
    @PostMapping("/job-match")
    public ApiResponse<JobMatchAiResponse> getJobMatch(@RequestBody JobMatchRequest request) {
        JobMatchAiResponse result = geminiService.generateJobMatchExplanations(request);
        return new ApiResponse<>(true, "AI job-match explanations generated", result);
    }

    /**
     * Get enhanced AI-powered insights to enrich the developer score.
     */
    @GetMapping("/insights/{username}")
    public ApiResponse<Map<String, Object>> getEnhancedInsights(@PathVariable String username) {
        DeveloperScoreResponse score = developerScoreService.getScore(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);

        String aiRaw = geminiService.generateEnhancedInsights(username, score, profile, repos);

        return new ApiResponse<>(true, "Enhanced AI insights generated",
                Map.of("score", score, "aiInsight", aiRaw != null ? aiRaw : "AI insights not available"));
    }
}
