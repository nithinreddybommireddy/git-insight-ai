package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.request.CommitDiffReviewRequest;
import com.gitinsight.githubservice.dto.request.JobMatchRequest;
import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffReviewResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.JobMatchAiResponse;
import com.gitinsight.githubservice.dto.response.OrganizationAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered analysis service using Google Gemini API.
 * <p>
 * Provides prompt templates for:
 * - Developer summary & assessment
 * - Repository code review
 * - Skill detection & analysis
 * - Career roadmap generation
 * - Interview readiness assessment
 * - Comparison between two developers
 * <p>
 * Gracefully falls back to template-based responses if the API key is not configured
 * or if the API call fails.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";
    private static final String MODEL_NAME = "gemini-3.1-flash-lite";
    private static final int MAX_OUTPUT_TOKENS = 800;
    private static final int JOB_MATCH_MAX_OUTPUT_TOKENS = 1500;
    private static final int MAX_JOB_DESCRIPTION_CHARS = 3500;
    private static final int COMMIT_DIFF_MAX_OUTPUT_TOKENS = 2000;
    private static final int MAX_COMMIT_DIFF_COMMITS = 3;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String apiKey;
    private final boolean enabled;

    public GeminiService(@Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.enabled = StringUtils.hasText(apiKey);

        this.restClient = RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
        if (enabled) {
            log.info("Gemini AI service initialized with API key");
        } else {
            log.info("Gemini AI service running in fallback mode (no API key configured). Set GEMINI_API_KEY env var for AI-powered insights.");
        }
    }
    // ═══════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate a comprehensive AI developer summary.
     */
    public String generateDeveloperSummary(
            String username,
            DeveloperScoreResponse score,
            GitHubProfileResponse profile,
            List<RepositoryResponse> repos
    ) {
        String prompt = buildDeveloperSummaryPrompt(username, score, profile, repos);
        return callGemini(prompt, "Developer Summary");
    }

    /**
     * Generate a detailed repository review.
     */
    public String generateRepositoryReview(
            String username,
            RepositoryResponse repo,
            DeveloperScoreResponse score
    ) {
        String prompt = buildRepoReviewPrompt(username, repo, score);
        return callGemini(prompt, "Repository Review");
    }

    /**
     * Detect and analyze developer skills.
     */
    public String generateSkillAnalysis(
            String username,
            DeveloperScoreResponse score,
            List<RepositoryResponse> repos
    ) {
        String prompt = buildSkillAnalysisPrompt(username, score, repos);
        return callGemini(prompt, "Skill Analysis");
    }

    /**
     * Generate a personalized career roadmap.
     */
    public String generateCareerRoadmap(
            String username,
            DeveloperScoreResponse score,
            GitHubProfileResponse profile,
            List<RepositoryResponse> repos
    ) {
        String prompt = buildCareerRoadmapPrompt(username, score, profile, repos);
        return callGemini(prompt, "Career Roadmap");
    }

    /**
     * Generate interview readiness assessment.
     */
    public String generateInterviewReadiness(
            String username,
            DeveloperScoreResponse score,
            List<RepositoryResponse> repos
    ) {
        String prompt = buildInterviewPrompt(username, score, repos);
        return callGemini(prompt, "Interview Readiness");
    }

    /**
     * Generate a comparative analysis between two developers.
     */
    public String generateComparison(
            String user1, DeveloperScoreResponse score1, GitHubProfileResponse profile1,
            String user2, DeveloperScoreResponse score2, GitHubProfileResponse profile2
    ) {
        String prompt = buildComparisonPrompt(user1, score1, profile1, user2, score2, profile2);
        return callGemini(prompt, "Developer Comparison");
    }

    /**
     * Enhanced insights that can replace the rule-based ones in ScoringEngine.
     */
    public String generateEnhancedInsights(
            String username,
            DeveloperScoreResponse score,
            GitHubProfileResponse profile,
            List<RepositoryResponse> repos
    ) {
        String prompt = buildEnhancedInsightsPrompt(username, score, profile, repos);
        return callGemini(prompt, "Enhanced Insights");
    }

    /**
     * Phase 5 — AI code quality review based on real commit history.
     */
    public String generateCodeQualityReview(
            String username,
            CommitAnalyticsResponse commitAnalytics,
            DeveloperScoreResponse score
    ) {
        String prompt = buildCodeQualityPrompt(username, commitAnalytics, score);
        return callGemini(prompt, "Code Quality Review");
    }

    /**
     * Organization / team-level review: Gemini summarizes an org's public repos,
     * language stack, and contributors with strengths and recommendations.
     */
    public String generateOrganizationReview(String login, OrganizationAnalyticsResponse org) {
        String prompt = buildOrganizationReviewPrompt(login, org);
        return callGemini(prompt, "Organization Review");
    }

    /**
     * Phase 6 — AI commit-diff code-quality review: Gemini reads the actual
     * per-file patches of a commit and returns an overall verdict plus per-file
     * findings. Falls back to a rule-based review when no API key is configured
     * or the response cannot be parsed.
     */
    public CommitDiffReviewResponse generateCommitDiffReview(CommitDiffReviewRequest request) {
        if (!enabled) {
            log.info("Gemini commit-diff review skipped (no API key configured)");
            return CommitDiffReviewResponse.deterministic(request);
        }
        if (request == null || request.commits() == null || request.commits().isEmpty()) {
            return CommitDiffReviewResponse.deterministic(request == null
                    ? new CommitDiffReviewRequest("", List.of()) : request);
        }
        String prompt = buildCommitDiffReviewPrompt(request);
        String raw = callGemini(prompt, "Commit Diff Review", COMMIT_DIFF_MAX_OUTPUT_TOKENS);
        if (raw == null || raw.isBlank()) {
            return CommitDiffReviewResponse.deterministic(request);
        }
        CommitDiffReviewResponse parsed = parseCommitDiffReview(raw);
        if (parsed == null || parsed.getFileReviews() == null || parsed.getFileReviews().isEmpty()) {
            log.info("Commit-diff review fell back to deterministic review (unparseable AI output)");
            return CommitDiffReviewResponse.deterministic(request);
        }
        parsed.setAiEnabled(true);
        parsed.setAiModel(MODEL_NAME);
        return parsed;
    }

    /**
     * AI job-match: review a job description plus the deterministically matched
     * candidates and return per-candidate fit explanations.
     */
    public JobMatchAiResponse generateJobMatchExplanations(JobMatchRequest request) {
        if (!enabled) {
            log.info("Gemini job-match skipped (no API key configured)");
            return JobMatchAiResponse.disabled();
        }
        String prompt = buildJobMatchPrompt(request);
        String raw = callGemini(prompt, "Job Match", JOB_MATCH_MAX_OUTPUT_TOKENS);
        if (raw == null || raw.isBlank()) {
            return JobMatchAiResponse.disabled();
        }
        List<JobMatchAiResponse.Explanation> explanations = parseJobMatchExplanations(raw);
        if (explanations.isEmpty()) {
            return JobMatchAiResponse.disabled();
        }
        return new JobMatchAiResponse(true, MODEL_NAME, explanations);
    }

    // ═══════════════════════════════════════════════════════════════
    // GEMINI API CALL
    // ═══════════════════════════════════════════════════════════════

    private String callGemini(String prompt, String taskName) {
        return callGemini(prompt, taskName, MAX_OUTPUT_TOKENS);
    }

    private String callGemini(String prompt, String taskName, int maxOutputTokens) {
        if (!enabled) {
            return getFallbackResponse(taskName);
        }

        try {
            Map<String, Object> requestBody = buildGeminiRequest(prompt, maxOutputTokens);

            Map<String, Object> response = restClient.post()
                    .uri(GEMINI_API_URL + "?key=" + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            return extractText(response);

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            return getFallbackResponse(taskName);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        if (response == null) return null;
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) return null;

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) return null;

            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildGeminiRequest(String userPrompt) {
        return buildGeminiRequest(userPrompt, MAX_OUTPUT_TOKENS);
    }

    private Map<String, Object> buildGeminiRequest(String userPrompt, int maxOutputTokens) {
        Map<String, Object> systemPart = new HashMap<>();
        systemPart.put("text", getSystemInstruction());

        Map<String, Object> systemInstruction = new HashMap<>();
        systemInstruction.put("parts", List.of(systemPart));

        Map<String, Object> userPart = new HashMap<>();
        userPart.put("text", userPrompt);

        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", List.of(userPart));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        generationConfig.put("topP", 0.9);

        Map<String, Object> request = new HashMap<>();
        request.put("systemInstruction", systemInstruction);
        request.put("contents", List.of(userContent));
        request.put("generationConfig", generationConfig);

        return request;
    }

    // ═══════════════════════════════════════════════════════════════
    // SYSTEM INSTRUCTION
    // ═══════════════════════════════════════════════════════════════

    private String getSystemInstruction() {
        return """
                You are GitInsight AI, an expert developer assessment and analytics engine.
                Your purpose is to analyze GitHub developer profiles and provide:
                - Objective, data-driven assessments based on actual GitHub activity
                - Specific, actionable recommendations for improvement
                - Career guidance tailored to the developer's current skill level
                - Honest but constructive feedback
                                
                Rules:
                1. Base all analysis strictly on the provided GitHub data. Do not make assumptions about data not provided.
                2. Be specific - reference actual numbers, languages, and repositories when possible.
                3. Be constructive - every criticism should be paired with an actionable suggestion.
                4. Keep responses concise (2-4 paragraphs unless more detail is requested).
                5. Use natural, conversational language - avoid marketing fluff.
                6. If data is limited, acknowledge the limitation rather than guessing.
                """;
    }

    // ═══════════════════════════════════════════════════════════════
    // PROMPT BUILDERS
    // ═══════════════════════════════════════════════════════════════

    private String buildDeveloperSummaryPrompt(
            String username, DeveloperScoreResponse score, GitHubProfileResponse profile,
            List<RepositoryResponse> repos
    ) {
        String langs = score.getLanguages() != null && score.getLanguages().length > 0
                ? String.join(", ", score.getLanguages())
                : "Unknown";
        return String.format("""
                Provide a concise developer assessment for the following GitHub profile:
                                
                **Profile:** %s (%s)
                **Bio:** %s
                **Overall Score:** %d/100 (%s)
                **Repositories:** %d
                **Stars:** %d | **Forks:** %d | **Languages:** %s
                                
                **10 Scoring Metrics:**
                - Contribution Recency: %d/100
                - Commit Frequency: %d/100
                - Repository Health: %d/100
                - Repository Quality: %d/100
                - Contribution Consistency: %d/100
                - Language Diversity: %d/100
                - Collaboration: %d/100
                - Open Source Impact: %d/100
                - Popularity: %d/100
                - Maintenance: %d/100
                                
                **Top Repositories:**
                %s
                                
                Based on this data, provide:
                1. A one-paragraph overall assessment
                2. Their strongest technical area with evidence
                3. Their weakest area with specific improvement suggestions
                4. 2-3 specific, actionable recommendations to improve their score
                """,
                profile != null ? profile.getName() : username, username,
                profile != null ? profile.getBio() != null ? profile.getBio() : "N/A" : "N/A",
                score.getOverallScore(), score.getLevel(),
                score.getTotalRepositories(),
                score.getTotalStars(), score.getTotalForks(), langs,
                score.getContributionRecency(), score.getCommitFrequency(),
                score.getRepositoryHealth(), score.getRepositoryQuality(),
                score.getContributionConsistency(), score.getLanguageDiversity(),
                score.getCollaboration(), score.getOpenSourceImpact(),
                score.getPopularity(), score.getMaintenance(),
                repos.stream()
                        .filter(r -> !r.isFork())
                        .sorted(Comparator.comparingInt(RepositoryResponse::getStars).reversed())
                        .limit(5)
                        .map(r -> String.format("  - %s (%s, ★%d, 🍴%d)", r.getName(), r.getLanguage(), r.getStars(), r.getForks()))
                        .collect(Collectors.joining("\n"))
        );
    }

    private String buildRepoReviewPrompt(String username, RepositoryResponse repo, DeveloperScoreResponse score) {
        return String.format("""
                Review the following GitHub repository:
                                
                **Repository:** %s
                **Owner:** %s
                **Language:** %s
                **Stars:** %d | **Forks:** %d | **Issues:** %d
                **Description:** %s
                **Topics:** %s
                **Has License:** %b
                **Size:** %d KB
                                
                Provide:
                1. A brief assessment of this repository's quality and completeness
                2. What it does well
                3. Specific suggestions for improvement
                4. How it contributes to the developer's overall profile
                """,
                repo.getName(), username,
                repo.getLanguage() != null ? repo.getLanguage() : "N/A",
                repo.getStars(), repo.getForks(), repo.getOpenIssues(),
                repo.getDescription() != null ? repo.getDescription() : "N/A",
                repo.getTopics() != null && repo.getTopics().length > 0
                        ? String.join(", ", repo.getTopics()) : "None",
                repo.isHasLicense(), repo.getSize()
        );
    }

    private String buildSkillAnalysisPrompt(
            String username, DeveloperScoreResponse score, List<RepositoryResponse> repos
    ) {
        String langBreakdown = repos.stream()
                .filter(r -> r.getLanguage() != null)
                .collect(Collectors.groupingBy(RepositoryResponse::getLanguage, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> String.format("  - %s: %d repos", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                Analyze the skills of developer %s based on their GitHub data:
                                
                **Score:** %d/100 (%s)
                **Total Languages:** %d
                **Language Breakdown:**
                %s
                                
                **Metric Scores:**
                - Repo Quality: %d/100
                - Language Diversity: %d/100
                - Collaboration: %d/100
                                
                Provide:
                1. Top technical skills identified from their repositories
                2. Skill level for each (Beginner/Intermediate/Advanced/Expert)
                3. Technologies they should learn next based on their current stack
                4. Overall skill assessment (frontend, backend, full-stack, or specialist)
                """,
                username, score.getOverallScore(), score.getLevel(),
                score.getLanguageCount(), langBreakdown,
                score.getRepositoryQuality(), score.getLanguageDiversity(),
                score.getCollaboration()
        );
    }

    private String buildCareerRoadmapPrompt(
            String username, DeveloperScoreResponse score, GitHubProfileResponse profile,
            List<RepositoryResponse> repos
    ) {
        String topLangs = score.getLanguages() != null && score.getLanguages().length > 0
                ? String.join(", ", Arrays.copyOf(score.getLanguages(), Math.min(score.getLanguages().length, 5)))
                : "N/A";

        return String.format("""
                Create a personalized career development roadmap for GitHub user %s:
                                
                **Current Level:** %s (Score: %d/100)
                **Role:** %s
                **Primary Languages:** %s
                **Total Repos:** %d | **Stars:** %d
                **Key Strengths:** Highest metric is %s (%d/100)
                **Key Weaknesses:** Lowest metric is %s (%d/100)
                                
                Based on their current profile and assuming they want to advance in their software engineering career, provide:
                1. Their current career stage (Junior/Mid/Senior/Staff/Principal)
                2. 3-month, 6-month, and 12-month goals with specific GitHub actions
                3. Technologies they should prioritize learning
                4. Types of projects they should build
                5. Open-source contribution strategies
                6. Portfolio optimization tips
                """,
                username, score.getLevel(), score.getOverallScore(),
                profile != null && profile.getCompany() != null ? profile.getCompany() : "Not specified",
                topLangs, score.getTotalRepositories(), score.getTotalStars(),
                "Contribution Recency", score.getContributionRecency(),
                "Maintenance", score.getMaintenance()
        );
    }

    private String buildInterviewPrompt(
            String username, DeveloperScoreResponse score, List<RepositoryResponse> repos
    ) {
        return String.format("""
                Assess the interview readiness of GitHub developer %s:
                                
                **Score:** %d/100 (%s)
                **Languages:** %d
                **Top Repos starred:** %d repos with 5+ stars
                                
                Provide:
                1. Interview readiness level (Low/Medium/High) with reasoning
                2. What types of companies/roles would be the best fit
                3. Specific gaps in their profile that might come up in interviews
                4. 2-3 projects they should highlight/improve for interviews
                """,
                username, score.getOverallScore(), score.getLevel(),
                score.getLanguageCount(),
                (int) repos.stream().filter(r -> r.getStars() >= 5).count()
        );
    }

    private String buildComparisonPrompt(
            String user1, DeveloperScoreResponse score1, GitHubProfileResponse profile1,
            String user2, DeveloperScoreResponse score2, GitHubProfileResponse profile2
    ) {
        return String.format("""
                Compare the following two GitHub developers:
                                
                **Developer 1:** %s (Score: %d/100 - %s)
                **Developer 2:** %s (Score: %d/100 - %s)
                                
                **Developer 1 Metrics:**
                - Recency: %d | Freq: %d | Health: %d | Quality: %d | Consist: %d
                - Lang: %d | Collab: %d | OSS: %d | Popular: %d | Maint: %d
                                
                **Developer 2 Metrics:**
                - Recency: %d | Freq: %d | Health: %d | Quality: %d | Consist: %d
                - Lang: %d | Collab: %d | OSS: %d | Popular: %d | Maint: %d
                                
                Provide:
                1. Who has the stronger overall profile and why
                2. Key differentiating factors between them
                3. Specific areas where each developer excels
                4. Which developer would be better suited for what types of roles
                """,
                profile1 != null && profile1.getName() != null ? profile1.getName() : user1,
                score1.getOverallScore(), score1.getLevel(),
                profile2 != null && profile2.getName() != null ? profile2.getName() : user2,
                score2.getOverallScore(), score2.getLevel(),
                score1.getContributionRecency(), score1.getCommitFrequency(),
                score1.getRepositoryHealth(), score1.getRepositoryQuality(),
                score1.getContributionConsistency(), score1.getLanguageDiversity(),
                score1.getCollaboration(), score1.getOpenSourceImpact(),
                score1.getPopularity(), score1.getMaintenance(),
                score2.getContributionRecency(), score2.getCommitFrequency(),
                score2.getRepositoryHealth(), score2.getRepositoryQuality(),
                score2.getContributionConsistency(), score2.getLanguageDiversity(),
                score2.getCollaboration(), score2.getOpenSourceImpact(),
                score2.getPopularity(), score2.getMaintenance()
        );
    }

    private String buildCodeQualityPrompt(
            String username, CommitAnalyticsResponse a, DeveloperScoreResponse score
    ) {
        String weekly = a.getWeeklyActivity() == null || a.getWeeklyActivity().isEmpty()
                ? "No weekly activity data"
                : a.getWeeklyActivity().stream()
                        .limit(12)
                        .map(w -> w.getWeek() + ": " + w.getCommits() + " commits")
                        .collect(Collectors.joining(", "));

        String repos = a.getRepoBreakdown() == null || a.getRepoBreakdown().isEmpty()
                ? "No repository data"
                : a.getRepoBreakdown().stream()
                        .limit(8)
                        .map(r -> String.format("  - %s: %d commits (+%d/-%d)",
                                r.getRepoName(), r.getTotalCommits(), r.getAdditions(), r.getDeletions()))
                        .collect(Collectors.joining("\n"));

        return String.format("""
                Provide a concise AI code-quality review for GitHub developer %s based on their real commit history.
                                
                **Developer Score:** %d/100 (%s)
                                
                **Commit Analytics:**
                - Total Commits: %d
                - Commits per Week: %.1f
                - Additions: %d | Deletions: %d
                - Code Quality Score: %d/100
                - Commit Message Quality: %d/100
                - Conventional Commit Rate: %d%%
                - Average Message Length: %.0f chars
                - Commit Size Score: %d/100
                - Top Commit Types: %s
                                
                **Weekly Activity (last 12 weeks):** %s
                                
                **Per-Repository Breakdown:**
                %s
                                
                Provide:
                1. Overall commit-hygiene assessment (2-3 sentences)
                2. Specific strengths in how they commit code
                3. Specific weaknesses (message quality, commit size, consistency)
                4. 3 actionable recommendations to improve code quality and reviewability
                """,
                username, score.getOverallScore(), score.getLevel(),
                a.getTotalCommits(), a.getCommitsPerWeek(),
                a.getTotalAdditions(), a.getTotalDeletions(),
                a.getCodeQualityScore(), a.getCommitMessageQuality(),
                a.getConventionalCommitRate(), a.getAverageMessageLength(),
                a.getCommitSizeScore(),
                a.getTopCommitTypes() != null ? String.join(", ", a.getTopCommitTypes()) : "none",
                weekly, repos
        );
    }

    private String buildEnhancedInsightsPrompt(
            String username, DeveloperScoreResponse score, GitHubProfileResponse profile,
            List<RepositoryResponse> repos
    ) {
        String langs = score.getLanguages() != null && score.getLanguages().length > 0
                ? String.join(", ", score.getLanguages())
                : "Unknown";
        String topRepos = repos.stream()
                .filter(r -> !r.isFork())
                .sorted(Comparator.comparingInt(RepositoryResponse::getStars).reversed())
                .limit(3)
                .map(r -> String.format("%s (%s, ★%d)", r.getName(), r.getLanguage(), r.getStars()))
                .collect(Collectors.joining(", "));

        return String.format("""
                Provide a brief AI-powered developer insight summary for the following GitHub profile. Keep it to 2-3 paragraphs maximum.
                                
                **User:** %s (%s)
                **Score:** %d/100 - %s
                **Repos:** %d | **Stars:** %d | **Forks:** %d | **Languages:** %s
                **Top Repos:** %s
                **Followers:** %d
                                
                **10 Metrics:**
                Recency:%d Freq:%d Health:%d Quality:%d Consist:%d Lang:%d Collab:%d OSS:%d Popular:%d Maint:%d
                                
                Provide:
                - Overall assessment (1 sentence)
                - Key strength (1 sentence)
                - Key weakness (1 sentence)
                - 2 specific improvement suggestions
                Format as a JSON-like object with keys: assessment, strength, weakness, suggestions (array).
                """,
                profile != null && profile.getName() != null ? profile.getName() : username,
                username, score.getOverallScore(), score.getLevel(),
                score.getTotalRepositories(), score.getTotalStars(),
                score.getTotalForks(), langs, topRepos,
                profile != null && profile.getFollowers() != null ? profile.getFollowers() : 0,
                score.getContributionRecency(), score.getCommitFrequency(),
                score.getRepositoryHealth(), score.getRepositoryQuality(),
                score.getContributionConsistency(), score.getLanguageDiversity(),
                score.getCollaboration(), score.getOpenSourceImpact(),
                score.getPopularity(), score.getMaintenance()
        );
    }

    private String buildOrganizationReviewPrompt(String login, OrganizationAnalyticsResponse org) {
        String languages = org.getLanguages() == null || org.getLanguages().isEmpty()
                ? "No language data"
                : org.getLanguages().stream()
                        .limit(10)
                        .map(l -> String.format("  - %s: %.1f%% (%d repos)",
                                l.getLanguage(), l.getPercentage(), l.getRepos()))
                        .collect(Collectors.joining("\n"));

        String topRepos = org.getTopRepos() == null || org.getTopRepos().isEmpty()
                ? "No repository data"
                : org.getTopRepos().stream()
                        .limit(8)
                        .map(r -> String.format("  - %s (%s, ★%d, 🍴%d)",
                                r.getName(), nz(r.getLanguage(), "unknown"), r.getStars(), r.getForks()))
                        .collect(Collectors.joining("\n"));

        String contributors = org.getTopContributors() == null || org.getTopContributors().isEmpty()
                ? "No contributor data"
                : org.getTopContributors().stream()
                        .limit(10)
                        .map(c -> String.format("  - %s (%d contributions, %.1f%% of sampled share)",
                                nz(c.getLogin(), "unknown"), c.getContributions(), c.getContributionPercent()))
                        .collect(Collectors.joining("\n"));

        String teamActivity = org.getTeamActivity() == null
                ? "No activity data"
                : String.format(
                        "  - Commits: %d (30d) / %d (90d)%n  - Pull requests: %d (30d) / %d (90d)%n  - Issues: %d (30d) / %d (90d)",
                        org.getTeamActivity().getCommits30d(), org.getTeamActivity().getCommits90d(),
                        org.getTeamActivity().getPullRequests30d(), org.getTeamActivity().getPullRequests90d(),
                        org.getTeamActivity().getIssues30d(), org.getTeamActivity().getIssues90d());

        return String.format("""
                Provide a concise organization / team-level review for the GitHub organization %s (%s).
                                
                **Org Profile:**
                - Name: %s
                - Description: %s
                - Public Repos: %d | Followers: %d | Location: %s
                                
                **Repository Stats (sampled):**
                - Total Repos: %d
                - Total Stars: %d | Total Forks: %d
                - Average Stars per Repo: %.1f
                - Active Repos (pushed in 90 days): %d
                - Languages: %d
                                
                **Language Stack:**
                %s
                                
                **Top Repositories:**
                %s
                                
                **Top Contributors:**
                %s
                                
                **Team Activity (sampled repos, 30/90-day windows):**
                %s
                                
                Based strictly on this data, provide:
                1. A one-paragraph overall assessment of the team/organization
                2. Its strongest areas (languages, activity, repo quality) with evidence
                3. Weaknesses or risks (low activity, narrow stack, low stars)
                4. 2-3 actionable recommendations to grow the organization's public presence
                """,
                login, nz(org.getName(), login),
                nz(org.getName(), "N/A"), nz(org.getDescription(), "N/A"),
                org.getPublicRepos(), org.getFollowers(), nz(org.getLocation(), "N/A"),
                org.getTotalRepos(), org.getTotalStars(), org.getTotalForks(),
                org.getAverageStars(), org.getActiveRepos(), org.getLanguagesCount(),
                languages, topRepos, contributors, teamActivity
        );
    }

    private String buildJobMatchPrompt(JobMatchRequest request) {
        String candidates = request.candidates() == null || request.candidates().isEmpty()
                ? "None provided"
                : request.candidates().stream()
                        .limit(10)
                        .map(c -> String.format(
                                "- %s (score %d/100, %s) | languages: %s | matched: %s | missing: %s | top repos: %s | bio: %s",
                                c.username(), c.developerScore(), nz(c.level(), "N/A"),
                                joinList(c.languages()), joinList(c.matchedSkills()), joinList(c.missingSkills()),
                                joinList(c.topRepos()), nz(c.bio(), "N/A")))
                        .collect(Collectors.joining("\n"));

        return String.format("""
                You are helping a recruiter shortlist candidates for a job opening.
                A deterministic engine has already matched each candidate's skills against the job's required skills
                and computed a developer score. Your job is to explain WHY each candidate does or does not fit.
                                
                **Job Title:** %s
                **Job Description:** %s
                **Required Skills:** %s
                                
                **Candidates:**
                %s
                                
                Return STRICT JSON only — an array of objects, one per candidate you can reason about, with keys:
                - username (string)
                - fitLabel (one of: "Strong fit", "Good fit", "Partial fit", "Weak fit")
                - explanation (2-3 sentences referencing their actual languages, repositories, score, and matched/missing skills)
                - strengths (array of 2-3 short strings)
                - gaps (array of 1-3 short strings)
                - recommendation (one short sentence: "Interview", "Consider", or "Skip", plus a brief reason)
                                
                Rules: base everything strictly on the provided data; never invent repositories, skills, or experience;
                if a candidate's data is too thin to judge, still give your best assessment from what is shown.
                """,
                nz(request.jobTitle(), "N/A"),
                truncate(nz(request.jobDescription(), "N/A"), MAX_JOB_DESCRIPTION_CHARS),
                joinList(request.requiredSkills()),
                candidates
        );
    }

    @SuppressWarnings("unchecked")
    private List<JobMatchAiResponse.Explanation> parseJobMatchExplanations(String raw) {
        try {
            String json = raw.trim();
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```$", "").trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            List<Map<String, Object>> items = OBJECT_MAPPER.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});

            List<JobMatchAiResponse.Explanation> out = new ArrayList<>();
            int rank = 0;
            for (Map<String, Object> item : items) {
                rank++;
                String username = str(item.get("username"));
                if (username == null || username.isBlank()) continue;
                out.add(new JobMatchAiResponse.Explanation(
                        username,
                        rank,
                        nz(str(item.get("fitLabel")), "Partial fit"),
                        nz(str(item.get("explanation")), ""),
                        strList(item.get("strengths")),
                        strList(item.get("gaps")),
                        nz(str(item.get("recommendation")), "")
                ));
            }
            return out;
        } catch (Exception e) {
            log.warn("Failed to parse Gemini job-match output: {}", e.getMessage());
            return List.of();
        }
    }

    private static String str(Object o) {
        return o instanceof String s ? s : (o != null ? String.valueOf(o) : null);
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    private static String joinList(List<String> list) {
        return list == null || list.isEmpty() ? "none" : String.join(", ", list);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (o instanceof List<?> list) {
            return (List<String>) list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }

    private String buildCommitDiffReviewPrompt(CommitDiffReviewRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are reviewing a developer's actual code changes (commit diffs) as a senior code reviewer.
                For each file's patch, evaluate correctness risks, code quality, security, and maintainability.
                Base everything strictly on the patches shown — never invent code that is not present.
                                
                """);
        if (request.username() != null && !request.username().isBlank()) {
            sb.append("**Developer:** ").append(request.username()).append("\n");
        }

        List<CommitDiffResponse> commits = request.commits().stream()
                .limit(MAX_COMMIT_DIFF_COMMITS)
                .collect(Collectors.toList());
        sb.append("**Commits to review:** ").append(commits.size()).append("\n\n");

        for (int i = 0; i < commits.size(); i++) {
            CommitDiffResponse c = commits.get(i);
            sb.append("### Commit ").append(i + 1).append("\n");
            sb.append("- sha: ").append(nz(c.getSha(), "?")).append("\n");
            sb.append("- repo: ").append(nz(c.getRepoName(), "?")).append("\n");
            sb.append("- message: ").append(nz(c.getMessage(), "(no message)")).append("\n");
            sb.append("- stats: +").append(c.getAdditions()).append(" / -").append(c.getDeletions())
                    .append(" across ").append(c.getChangedFiles()).append(" file(s)\n");

            List<CommitDiffResponse.FileDiff> files = c.getFiles();
            if (files == null || files.isEmpty()) {
                sb.append("- files: none (merge/empty commit)\n\n");
                continue;
            }
            for (CommitDiffResponse.FileDiff f : files) {
                sb.append("\nFile: ").append(f.getFilename())
                        .append(" [").append(nz(f.getStatus(), "modified"))
                        .append(", +").append(f.getAdditions())
                        .append("/-").append(f.getDeletions()).append("]\n");
                sb.append("```diff\n");
                sb.append(nz(f.getPatch(), "(no patch content)")).append("\n```\n");
            }
            sb.append("\n");
        }

        sb.append("""
                Return STRICT JSON only (no markdown fences), matching this exact shape:
                {
                  "overallScore": <int 0-100>,
                  "overallSummary": "<2-3 sentence verdict of the whole change>",
                  "keyIssues": ["<most important problems across all files>", ...],
                  "strengths": ["<what was done well>", ...],
                  "recommendations": ["<actionable next steps>", ...],
                  "fileReviews": [
                    {
                      "filename": "<exact filename from the diff>",
                      "score": <int 0-100>,
                      "summary": "<1-2 sentences about this file's change>",
                      "issues": ["<specific issue, referencing code shown in the patch>", ...],
                      "suggestions": ["<specific, actionable suggestion>", ...]
                    }
                  ]
                }
                Rules: one fileReviews entry per file shown; scores must be integers 0-100;
                keep issues/suggestions specific to the actual patch content; if a patch is
                truncated or has no content, note that limitation instead of guessing.
                """);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private CommitDiffReviewResponse parseCommitDiffReview(String raw) {
        try {
            String json = raw.trim();
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```$", "").trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            Map<String, Object> root = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            CommitDiffReviewResponse res = new CommitDiffReviewResponse();
            res.setOverallScore(clampScore(num(root.get("overallScore"))));
            res.setOverallSummary(nz(str(root.get("overallSummary")), ""));
            res.setKeyIssues(strList(root.get("keyIssues")));
            res.setStrengths(strList(root.get("strengths")));
            res.setRecommendations(strList(root.get("recommendations")));

            List<CommitDiffReviewResponse.FileReview> fileReviews = new ArrayList<>();
            if (root.get("fileReviews") instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> rawItem)) continue;
                    Map<String, Object> m = (Map<String, Object>) rawItem;
                    CommitDiffReviewResponse.FileReview fr = new CommitDiffReviewResponse.FileReview();
                    fr.setFilename(nz(str(m.get("filename")), "unknown"));
                    fr.setScore(clampScore(num(m.get("score"))));
                    fr.setSummary(nz(str(m.get("summary")), ""));
                    fr.setIssues(strList(m.get("issues")));
                    fr.setSuggestions(strList(m.get("suggestions")));
                    fileReviews.add(fr);
                }
            }
            res.setFileReviews(fileReviews);
            return res;
        } catch (Exception e) {
            log.warn("Failed to parse Gemini commit-diff review output: {}", e.getMessage());
            return null;
        }
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    // ═══════════════════════════════════════════════════════════════
    // FALLBACK RESPONSES (when API key is not configured)
    // ═══════════════════════════════════════════════════════════════

    private String getFallbackResponse(String taskName) {
        return switch (taskName) {
            case "Developer Summary" ->
                    "AI-powered developer summary requires a Gemini API key. " +
                    "Set the GEMINI_API_KEY environment variable to enable AI-generated summaries. " +
                    "In the meantime, the rule-based scoring engine provides detailed metric analysis.";
            case "Repository Review" ->
                    "AI-powered repository review requires a Gemini API key. " +
                    "The repository health score and quality metrics in the scoring engine provide detailed automated analysis.";
            case "Skill Analysis" ->
                    "AI-powered skill analysis requires a Gemini API key. " +
                    "The language diversity and repository quality metrics provide automated skill assessment.";
            case "Career Roadmap" ->
                    "AI-powered career roadmap requires a Gemini API key. " +
                    "Set GEMINI_API_KEY to get personalized career guidance based on your GitHub profile.";
            case "Interview Readiness" ->
                    "AI-powered interview readiness assessment requires a Gemini API key.";
            case "Developer Comparison" ->
                    "AI-powered developer comparison requires a Gemini API key. " +
                    "The comparison page already provides detailed side-by-side metric analysis.";
            case "Enhanced Insights" -> null;
            case "Code Quality Review" ->
                    "AI-powered code quality review requires a Gemini API key. " +
                    "The commit analytics module already provides rule-based code quality metrics " +
                    "(message quality, conventional commit rate, commit size, and weekly activity).";
            case "Job Match" ->
                    "AI-powered job match requires a Gemini API key. " +
                    "The deterministic match engine already ranks candidates by skill fit and developer score.";
            case "Organization Review" ->
                    "AI-powered organization review requires a Gemini API key. " +
                    "The organization analytics module already provides rule-based team metrics " +
                    "(repo stats, language stack, contributors, activity).";
            default -> null;
        };
    }

    /**
     * Check if the Gemini API is configured and operational.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
