package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
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
    private static final int MAX_OUTPUT_TOKENS = 800;

    private final RestClient restClient;
    private final String apiKey;
    private final boolean enabled;

//    public GeminiService(
//            @Value("${gemini.api.key:}") String apiKey
//    ) {
//        this.apiKey = apiKey;
//        this.enabled = StringUtils.hasText(apiKey);
//        this.restClient = RestClient.builder()
//                .defaultHeader("Content-Type", "application/json")
//                .build();
//        if (enabled) {
//            log.info("Gemini AI service initialized with API key");
//        } else {
//            log.info("Gemini AI service running in fallback mode (no API key configured). Set GEMINI_API_KEY env var for AI-powered insights.");
//        }
//    }
    public GeminiService(@Value("${gemini.api.key:}") String apiKey) {
        System.out.println("API Key = " + apiKey);
        System.out.println("Enabled = " + StringUtils.hasText(apiKey));

        this.apiKey = apiKey;
        this.enabled = StringUtils.hasText(apiKey);

        this.restClient = RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
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

    // ═══════════════════════════════════════════════════════════════
    // GEMINI API CALL
    // ═══════════════════════════════════════════════════════════════

    private String callGemini(String prompt, String taskName) {
        if (!enabled) {
            return getFallbackResponse(taskName);
        }

        try {
            Map<String, Object> requestBody = buildGeminiRequest(prompt);

            Map<String, Object> response = restClient.post()
                    .uri(GEMINI_API_URL + "?key=" + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            System.out.println("========== GEMINI RESPONSE ==========");
            System.out.println(response);
            System.out.println("=====================================");

            String text = extractText(response);
            System.out.println("Extracted Text: " + text);
            return text;

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
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
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
