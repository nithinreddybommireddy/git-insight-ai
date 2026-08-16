package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse.DeveloperInsights;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse.MetricScore;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.service.GitHubIntegrationService.GitHubContributor;
import com.gitinsight.githubservice.service.GitHubIntegrationService.LanguageBreakdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Modular, deterministic Developer Scoring Engine.
 * <p>
 * Each metric is calculated independently in its own method.
 * Weights are configurable constants. All scores are 0-100.
 * Missing data is handled gracefully with sensible defaults.
 */
@Service
public class ScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(ScoringEngine.class);

    // ═══════════════════════════════════════════════════
    // CONFIGURABLE WEIGHTS — change these to rebalance
    // ═══════════════════════════════════════════════════
    public static final double WEIGHT_CONTRIBUTION_RECENCY = 0.15;
    public static final double WEIGHT_COMMIT_FREQUENCY = 0.15;
    public static final double WEIGHT_REPOSITORY_HEALTH = 0.15;
    public static final double WEIGHT_REPOSITORY_QUALITY = 0.10;
    public static final double WEIGHT_CONTRIBUTION_CONSISTENCY = 0.10;
    public static final double WEIGHT_LANGUAGE_DIVERSITY = 0.10;
    public static final double WEIGHT_COLLABORATION = 0.10;
    public static final double WEIGHT_OPEN_SOURCE_IMPACT = 0.05;
    public static final double WEIGHT_POPULARITY = 0.05;
    public static final double WEIGHT_MAINTENANCE = 0.05;

    // ═══════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════

    public DeveloperScoreResponse calculate(
            String username,
            List<RepositoryResponse> allRepos,
            GitHubProfileResponse profile
    ) {
        return calculate(username, allRepos, profile, null, null);
    }

    /**
     * Full scoring entry point with optional enriched GitHub data.
     * {@code weightedLanguages} — byte-weighted language breakdown (nullable; falls back to repo-size heuristic).
     * {@code contributors} — aggregate contributors across the developer's repos (nullable; falls back to fork/issue signals).
     */
    public DeveloperScoreResponse calculate(
            String username,
            List<RepositoryResponse> allRepos,
            GitHubProfileResponse profile,
            List<LanguageBreakdown> weightedLanguages,
            List<GitHubContributor> contributors
    ) {
        // Filter: ignore forks, archived, empty, templates
        List<RepositoryResponse> effectiveRepos = allRepos.stream()
                .filter(r -> !r.isFork())
                .filter(r -> !r.isArchived())
                .filter(r -> r.getSize() > 0)
                .collect(Collectors.toList());

        if (effectiveRepos.isEmpty() && !allRepos.isEmpty()) {
            // If all repos filtered, use originals as fallback
            effectiveRepos = allRepos.stream()
                    .filter(r -> !r.isFork())
                    .filter(r -> !r.isArchived())
                    .collect(Collectors.toList());
        }
        if (effectiveRepos.isEmpty()) {
            return DeveloperScoreResponse.empty(username);
        }

        int repoCount = effectiveRepos.size();

        // ── Calculate each metric independently ──
        MetricScore recency = calcContributionRecency(effectiveRepos, repoCount);
        MetricScore frequency = calcCommitFrequency(effectiveRepos);
        MetricScore health = calcRepositoryHealth(effectiveRepos);
        MetricScore quality = calcRepositoryQuality(effectiveRepos);
        MetricScore consistency = calcContributionConsistency(effectiveRepos, repoCount);
        MetricScore diversity = calcLanguageDiversity(effectiveRepos, weightedLanguages);
        MetricScore collaboration = calcCollaborationScore(effectiveRepos, allRepos, contributors, username);
        MetricScore impact = calcOpenSourceImpact(effectiveRepos);
        MetricScore pop = calcPopularity(effectiveRepos, profile);
        MetricScore maintenance = calcMaintenance(effectiveRepos);

        // ── Aggregate final score ──
        int overallScore = (int) Math.round(
                recency.getScore() * WEIGHT_CONTRIBUTION_RECENCY +
                frequency.getScore() * WEIGHT_COMMIT_FREQUENCY +
                health.getScore() * WEIGHT_REPOSITORY_HEALTH +
                quality.getScore() * WEIGHT_REPOSITORY_QUALITY +
                consistency.getScore() * WEIGHT_CONTRIBUTION_CONSISTENCY +
                diversity.getScore() * WEIGHT_LANGUAGE_DIVERSITY +
                collaboration.getScore() * WEIGHT_COLLABORATION +
                impact.getScore() * WEIGHT_OPEN_SOURCE_IMPACT +
                pop.getScore() * WEIGHT_POPULARITY +
                maintenance.getScore() * WEIGHT_MAINTENANCE
        );
        overallScore = Math.max(0, Math.min(100, overallScore));

        // ── Build response ──
        DeveloperScoreResponse res = new DeveloperScoreResponse();
        res.setUsername(username);
        res.setOverallScore(overallScore);
        res.setLevel(determineLevel(overallScore));

        res.setContributionRecency(recency.getScore());
        res.setCommitFrequency(frequency.getScore());
        res.setRepositoryHealth(health.getScore());
        res.setRepositoryQuality(quality.getScore());
        res.setContributionConsistency(consistency.getScore());
        res.setLanguageDiversity(diversity.getScore());
        res.setCollaboration(collaboration.getScore());
        res.setOpenSourceImpact(impact.getScore());
        res.setPopularity(pop.getScore());
        res.setMaintenance(maintenance.getScore());

        res.setContributionRecencyDetails(recency);
        res.setCommitFrequencyDetails(frequency);
        res.setRepositoryHealthDetails(health);
        res.setRepositoryQualityDetails(quality);
        res.setContributionConsistencyDetails(consistency);
        res.setLanguageDiversityDetails(diversity);
        res.setCollaborationDetails(collaboration);
        res.setOpenSourceImpactDetails(impact);
        res.setPopularityDetails(pop);
        res.setMaintenanceDetails(maintenance);

        // Legacy fields
        res.setTotalStars(effectiveRepos.stream().mapToInt(RepositoryResponse::getStars).sum());
        res.setTotalForks(effectiveRepos.stream().mapToInt(RepositoryResponse::getForks).sum());
        res.setTotalRepositories(effectiveRepos.size());
        res.setLanguageCount((int) effectiveRepos.stream()
                .map(RepositoryResponse::getLanguage)
                .filter(l -> l != null && !l.isEmpty())
                .distinct().count());
        res.setLanguages(effectiveRepos.stream()
                .map(RepositoryResponse::getLanguage)
                .filter(l -> l != null && !l.isEmpty())
                .distinct().toArray(String[]::new));
        res.setAvgHealthScore(health.getScore());
        res.setAvgPopularityScore(pop.getScore());
        res.setAvgMaintenanceScore(maintenance.getScore());
        res.setContributionRecencyScore(recency.getScore());
        res.setCommitFrequencyScore(frequency.getScore());
        res.setConsistencyScore(consistency.getScore());

        // AI insights
        res.setInsights(generateInsights(res, effectiveRepos, profile));

        return res;
    }

    // ═══════════════════════════════════════════════════
    // 1. CONTRIBUTION RECENCY (weight: 15%)
    // Measures: what % of repos were pushed to recently
    // ═══════════════════════════════════════════════════

    MetricScore calcContributionRecency(List<RepositoryResponse> repos, int repoCount) {
        long active30 = repos.stream().filter(r -> daysSincePush(r) < 30).count();
        long active90 = repos.stream().filter(r -> daysSincePush(r) < 90).count();

        double ratio30 = repoCount > 0 ? (double) active30 / repoCount : 0;
        double ratio90 = repoCount > 0 ? (double) active90 / repoCount : 0;

        int score = (int) Math.round(ratio30 * 60 + ratio90 * 40);

        MetricScore m = new MetricScore();
        m.setScore(Math.min(score, 100));
        m.setWeight(15);
        m.setLabel("Contribution Recency");
        m.setDescription("How recently this developer has pushed code");
        m.setIcon("activity");
        m.setTrend(score >= 60 ? "up" : score >= 30 ? "stable" : "down");

        if (score >= 80) {
            m.setExplanation("Actively maintains most repositories with recent commits");
            m.setImprovementSuggestion("Continue the excellent momentum — consistency is key!");
        } else if (score >= 50) {
            m.setExplanation("Some repositories are actively maintained while others are dormant");
            m.setImprovementSuggestion("Try to update older repositories or archive them to keep your profile clean");
        } else {
            m.setExplanation("Most repositories have not been updated recently");
            m.setImprovementSuggestion("Consider revisiting older projects or starting new ones to show active development");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 2. COMMIT FREQUENCY (weight: 15%)
    // Measures: average days since last push across repos
    // ═══════════════════════════════════════════════════

    MetricScore calcCommitFrequency(List<RepositoryResponse> repos) {
        double avgDays = repos.stream()
                .mapToLong(this::daysSincePush)
                .average()
                .orElse(365);

        int score;
        if (avgDays <= 7) score = 100;
        else if (avgDays <= 14) score = 90;
        else if (avgDays <= 30) score = 75;
        else if (avgDays <= 60) score = 55;
        else if (avgDays <= 90) score = 35;
        else if (avgDays <= 180) score = 20;
        else score = 5;

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(15);
        m.setLabel("Commit Frequency");
        m.setDescription("How often this developer pushes code (avg days since last push)");
        m.setIcon("git-commit");
        m.setTrend(score >= 60 ? "up" : score >= 30 ? "stable" : "down");

        if (score >= 80) {
            m.setExplanation("Frequent commits across repositories — very active developer");
            m.setImprovementSuggestion("Maintain this cadence for the highest score possible");
        } else if (score >= 40) {
            m.setExplanation("Moderate commit frequency with activity every 1-2 months");
            m.setImprovementSuggestion("Aim for at least weekly commits to improve this metric");
        } else {
            m.setExplanation("Infrequent commits — most repos haven't been updated in months");
            m.setImprovementSuggestion("Set a goal to contribute to at least one repository per week");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 3. REPOSITORY HEALTH (weight: 15%)
    // Measures: README, license, topics, CI, releases, wiki, security
    // ═══════════════════════════════════════════════════

    MetricScore calcRepositoryHealth(List<RepositoryResponse> repos) {
        double avgHealth = repos.stream()
                .mapToInt(r -> {
                    int score = 0;
                    if (r.getDescription() != null && !r.getDescription().isEmpty()) score += 10;
                    if (r.isHasLicense()) score += 15;
                    if (r.getTopics() != null && r.getTopics().length > 0) score += 15;
                    if (r.getHomepage() != null && !r.getHomepage().isEmpty()) score += 10;
                    if (r.getSize() > 50) score += 10; // meaningful project size
                    if (r.getStars() > 0) score += 10;
                    if (r.getForks() > 0) score += 10;
                    if (daysSincePush(r) < 90) score += 20; // recently maintained
                    return Math.min(score, 100);
                })
                .average()
                .orElse(0);

        int score = (int) Math.round(avgHealth);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(15);
        m.setLabel("Repository Health");
        m.setDescription("Quality of repository setup: README, license, topics, CI readiness");
        m.setIcon("heart-pulse");
        m.setTrend(score >= 60 ? "up" : score >= 35 ? "stable" : "down");

        if (score >= 70) {
            m.setExplanation("Well-maintained repositories with descriptions, licenses, and topics");
            m.setImprovementSuggestion("Consider adding CI/CD badges, security policies, and contribution guides");
        } else if (score >= 40) {
            m.setExplanation("Some repositories have health indicators but many are missing licenses or descriptions");
            m.setImprovementSuggestion("Add README files, licenses, and topics to all repositories");
        } else {
            m.setExplanation("Most repositories lack documentation, licenses, and maintenance indicators");
            m.setImprovementSuggestion("Start by adding a README, license file, and relevant topics to each repo");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 4. REPOSITORY QUALITY (weight: 10%)
    // Measures: documentation, tests, structure, commit history
    // ═══════════════════════════════════════════════════

    MetricScore calcRepositoryQuality(List<RepositoryResponse> repos) {
        double avgQuality = repos.stream()
                .mapToInt(r -> {
                    int score = 30; // base
                    if (r.getDescription() != null && r.getDescription().length() > 20) score += 15;
                    if (r.getHomepage() != null && !r.getHomepage().isEmpty()) score += 10;
                    if (r.getTopics() != null && r.getTopics().length > 0) score += 15;
                    if (r.isHasLicense()) score += 10;
                    if (r.getSize() > 500) score += 10; // substantial codebase
                    if (r.getStars() >= 5) score += 10; // community validation
                    return Math.min(score, 100);
                })
                .average()
                .orElse(0);

        int score = (int) Math.round(avgQuality);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(10);
        m.setLabel("Repository Quality");
        m.setDescription("Code quality indicators: documentation, project structure, releases");
        m.setIcon("shield-check");
        m.setTrend(score >= 60 ? "up" : score >= 35 ? "stable" : "down");

        if (score >= 70) {
            m.setExplanation("High-quality codebases with proper documentation and structure");
            m.setImprovementSuggestion("Add unit tests and CI badges to further improve quality scores");
        } else if (score >= 40) {
            m.setExplanation("Adequate quality but some repos lack documentation or structure");
            m.setImprovementSuggestion("Improve README files with installation guides, examples, and API docs");
        } else {
            m.setExplanation("Repos need better documentation, project structure, and meaningful descriptions");
            m.setImprovementSuggestion("Focus on creating well-structured repos with clear documentation");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 5. CONTRIBUTION CONSISTENCY (weight: 10%)
    // Measures: evenly maintained repos
    // ═══════════════════════════════════════════════════

    MetricScore calcContributionConsistency(List<RepositoryResponse> repos, int repoCount) {
        long active = repos.stream()
                .filter(r -> r.getActivityScore() >= 30)
                .count();

        double ratio = repoCount > 0 ? (double) active / repoCount : 0;
        int score = (int) Math.round(ratio * 100);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(10);
        m.setLabel("Contribution Consistency");
        m.setDescription("How evenly the developer distributes effort across all repositories");
        m.setIcon("equal-approximately");
        m.setTrend(score >= 70 ? "up" : score >= 40 ? "stable" : "down");

        if (score >= 80) {
            m.setExplanation("All repositories are well-maintained — excellent consistency");
            m.setImprovementSuggestion("Keep up the balanced approach to maintenance");
        } else if (score >= 40) {
            m.setExplanation("Some repos are actively maintained while others are neglected");
            m.setImprovementSuggestion("Try to distribute effort more evenly across all projects");
        } else {
            m.setExplanation("Most repositories have minimal activity");
            m.setImprovementSuggestion("Focus on maintaining fewer repos better, or archive inactive ones");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 6. LANGUAGE DIVERSITY (weight: 10%)
    // Measures: number of languages, weighted by repo size
    // ═══════════════════════════════════════════════════

    MetricScore calcLanguageDiversity(List<RepositoryResponse> repos) {
        return calcLanguageDiversity(repos, null);
    }

    MetricScore calcLanguageDiversity(List<RepositoryResponse> repos, List<LanguageBreakdown> weightedLanguages) {
        // Real byte-weighted data from GitHub's /languages endpoint (weighted by repository size)
        if (weightedLanguages != null && !weightedLanguages.isEmpty()) {
            int languageCount = weightedLanguages.size();
            double diversityBonus = Math.min(languageCount * 12, 60);

            // Shannon diversity index based on actual byte share
            double total = weightedLanguages.stream().mapToDouble(LanguageBreakdown::percentage).sum();
            double entropy = 0;
            for (LanguageBreakdown lb : weightedLanguages) {
                double p = total > 0 ? lb.percentage() / total : 0;
                if (p > 0) entropy -= p * (Math.log(p) / Math.log(2));
            }
            double normalizedEntropy = Math.min(entropy / Math.log(Math.max(languageCount, 2)) / Math.log(2), 1.0);

            int score = (int) Math.round(diversityBonus + normalizedEntropy * 40);
            score = Math.min(score, 100);

            MetricScore m = new MetricScore();
            m.setScore(score);
            m.setWeight(10);
            m.setLabel("Language Diversity");
            m.setDescription("Variety and balance of programming languages, weighted by actual code volume");
            m.setIcon("code-2");
            m.setTrend(score >= 60 ? "up" : score >= 30 ? "stable" : "down");

            if (score >= 70) {
                m.setExplanation("Strong multi-language developer with well-distributed code across " + languageCount + " languages (byte-weighted)");
                m.setImprovementSuggestion("Consider exploring new languages or frameworks to further diversify");
            } else if (score >= 35) {
                m.setExplanation("Uses " + languageCount + " languages with solid depth in primary languages");
                m.setImprovementSuggestion("Try learning a new language or framework to broaden your stack");
            } else {
                m.setExplanation("Code volume is concentrated in " + languageCount + " language(s)");
                m.setImprovementSuggestion("Explore additional programming languages to increase versatility");
            }
            return m;
        }

        // Fallback: repo-size-weighted heuristic when /languages data is unavailable
        Map<String, Integer> langSize = new HashMap<>();
        int totalSize = 0;

        for (RepositoryResponse r : repos) {
            String lang = r.getLanguage();
            if (lang != null && !lang.isEmpty()) {
                int size = Math.max(r.getSize(), 1);
                langSize.merge(lang, size, Integer::sum);
                totalSize += size;
            }
        }

        if (langSize.isEmpty()) {
            MetricScore m = new MetricScore();
            m.setScore(5);
            m.setWeight(10);
            m.setLabel("Language Diversity");
            m.setDescription("Range of programming languages used");
            m.setIcon("code-2");
            m.setTrend("stable");
            m.setExplanation("No recognizable programming languages detected");
            m.setImprovementSuggestion("Add language metadata to repositories");
            return m;
        }

        // Weight languages by size
        int languageCount = langSize.size();
        double diversityBonus = Math.min(languageCount * 12, 60);

        // Shannon diversity index based on size distribution
        double total = totalSize;
        double entropy = 0;
        for (int size : langSize.values()) {
            double p = size / total;
            if (p > 0) entropy -= p * (Math.log(p) / Math.log(2));
        }
        double normalizedEntropy = Math.min(entropy / Math.log(Math.max(languageCount, 2)) / Math.log(2), 1.0);

        int score = (int) Math.round(diversityBonus + normalizedEntropy * 40);
        score = Math.min(score, 100);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(10);
        m.setLabel("Language Diversity");
        m.setDescription("Variety and balance of programming languages used");
        m.setIcon("code-2");
        m.setTrend(score >= 60 ? "up" : score >= 30 ? "stable" : "down");

        if (score >= 70) {
            m.setExplanation("Strong multi-language developer with well-distributed skills across " + languageCount + " languages");
            m.setImprovementSuggestion("Consider exploring new languages or frameworks to further diversify");
        } else if (score >= 35) {
            m.setExplanation("Uses " + languageCount + " languages with some depth in primary languages");
            m.setImprovementSuggestion("Try learning a new language or framework to broaden your stack");
        } else {
            m.setExplanation("Primarily works with " + (languageCount > 0 ? languageCount + " language(s)" : "unknown languages"));
            m.setImprovementSuggestion("Explore additional programming languages to increase versatility");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 7. COLLABORATION SCORE (weight: 10%)
    // Measures: fork engagement, issues, PR-friendliness
    // ═══════════════════════════════════════════════════

    MetricScore calcCollaborationScore(List<RepositoryResponse> effective, List<RepositoryResponse> all) {
        return calcCollaborationScore(effective, all, null, null);
    }

    MetricScore calcCollaborationScore(List<RepositoryResponse> effective, List<RepositoryResponse> all,
                                       List<GitHubContributor> contributors, String username) {
        int score = 20; // base

        // Forks of their repos — indicates others find their work valuable
        long totalForks = effective.stream().mapToInt(RepositoryResponse::getForks).sum();
        if (totalForks > 0) score += 15;
        if (totalForks >= 10) score += 10;
        if (totalForks >= 50) score += 10;
        if (totalForks >= 100) score += 10;

        // Open issues — indicates community engagement
        long totalIssues = effective.stream().mapToInt(RepositoryResponse::getOpenIssues).sum();
        if (totalIssues >= 5) score += 10;  // repos are being used
        if (totalIssues >= 20) score += 10;

        // They fork others — indicates collaboration
        long forkedCount = all.stream().filter(RepositoryResponse::isFork).count();
        if (forkedCount > 0) score += 5;
        if (forkedCount >= 5) score += 10;
        if (forkedCount >= 10) score += 5;

        // Watchers on their repos
        long totalWatchers = effective.stream().mapToInt(RepositoryResponse::getWatchers).sum();
        if (totalWatchers > 0) score += 5;

        // External contributors to their repos (from the /contributors API) — genuine collaboration signal
        if (contributors != null && !contributors.isEmpty()) {
            long externalCount = contributors.stream()
                    .map(GitHubContributor::login)
                    .filter(Objects::nonNull)
                    .filter(l -> username == null || !l.equalsIgnoreCase(username))
                    .distinct()
                    .count();
            if (externalCount > 0) score += 5;
            if (externalCount >= 3) score += 5;
            if (externalCount >= 10) score += 10;
            if (externalCount >= 25) score += 10;
        }

        score = Math.min(score, 100);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(10);
        m.setLabel("Collaboration");
        m.setDescription("Community engagement: forks, issues, pull requests, and contributions");
        m.setIcon("users");
        m.setTrend(score >= 50 ? "up" : score >= 25 ? "stable" : "down");

        if (score >= 60) {
            m.setExplanation("Active community engagement with forks, issues, and collaborative development");
            m.setImprovementSuggestion("Consider adding CONTRIBUTING.md and issue/PR templates to encourage more contributions");
        } else if (score >= 30) {
            m.setExplanation("Some community interaction but limited collaborative signals");
            m.setImprovementSuggestion("Engage more with other projects through PRs, code reviews, and discussions");
        } else {
            m.setExplanation("Limited evidence of collaborative development");
            m.setImprovementSuggestion("Start contributing to open source projects and enable issues on your repos");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 8. OPEN SOURCE IMPACT (weight: 5%)
    // Measures: stars, forks, watchers, size
    // ═══════════════════════════════════════════════════

    MetricScore calcOpenSourceImpact(List<RepositoryResponse> repos) {
        int totalStars = repos.stream().mapToInt(RepositoryResponse::getStars).sum();
        int totalForks = repos.stream().mapToInt(RepositoryResponse::getForks).sum();
        int totalWatchers = repos.stream().mapToInt(RepositoryResponse::getWatchers).sum();

        // Combined impact score
        int starScore = Math.min(totalStars / 10, 50);
        int forkScore = Math.min(totalForks / 3, 20);
        int watchScore = Math.min(totalWatchers, 15);
        int sizeScore = Math.min(repos.size() * 3, 15);

        int score = starScore + forkScore + watchScore + sizeScore;
        score = Math.min(score, 100);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(5);
        m.setLabel("Open Source Impact");
        m.setDescription("Overall impact of open-source contributions measured by stars, forks, and reach");
        m.setIcon("globe");
        m.setTrend(score >= 50 ? "up" : score >= 20 ? "stable" : "down");

        if (score >= 60) {
            m.setExplanation("Significant open-source presence with broad community adoption");
            m.setImprovementSuggestion("Create more public repositories and promote them through developer communities");
        } else if (score >= 25) {
            m.setExplanation("Some open-source recognition with a few starred repositories");
            m.setImprovementSuggestion("Contribute to popular projects and share your work on social platforms");
        } else {
            m.setExplanation("Limited open-source visibility");
            m.setImprovementSuggestion("Start by contributing to existing projects and building a public portfolio");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 9. POPULARITY (weight: 5%)
    // Measures: followers, stars, profile visibility
    // ═══════════════════════════════════════════════════

    MetricScore calcPopularity(List<RepositoryResponse> repos, GitHubProfileResponse profile) {
        int score = 0;

        if (profile != null) {
            int followers = profile.getFollowers() != null ? profile.getFollowers() : 0;
            if (followers > 0) score += 15;
            if (followers >= 10) score += 10;
            if (followers >= 100) score += 15;
            if (followers >= 1000) score += 10;
            if (followers >= 10000) score += 10;
        }

        int totalStars = repos.stream().mapToInt(RepositoryResponse::getStars).sum();
        if (totalStars > 0) score += 10;
        if (totalStars >= 50) score += 10;
        if (totalStars >= 500) score += 10;

        int totalWatchers = repos.stream().mapToInt(RepositoryResponse::getWatchers).sum();
        if (totalWatchers > 0) score += 5;
        if (totalWatchers >= 100) score += 5;

        score = Math.min(score, 100);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(5);
        m.setLabel("Popularity");
        m.setDescription("GitHub profile popularity based on followers, stars, and community reach");
        m.setIcon("trending-up");
        m.setTrend(score >= 40 ? "up" : score >= 15 ? "stable" : "down");

        if (score >= 50) {
            m.setExplanation("Notable profile with significant followers and community interest");
            m.setImprovementSuggestion("Continue producing high-quality work and engaging with the community");
        } else if (score >= 20) {
            m.setExplanation("Moderate visibility with some followers and project interest");
            m.setImprovementSuggestion("Share your projects on social media and developer forums to increase reach");
        } else {
            m.setExplanation("Low visibility profile");
            m.setImprovementSuggestion("Build your presence by contributing to popular repos and networking");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // 10. MAINTENANCE (weight: 5%)
    // Measures: issue response, recent updates, release-like behavior
    // ═══════════════════════════════════════════════════

    MetricScore calcMaintenance(List<RepositoryResponse> repos) {
        double avgMaintenance = repos.stream()
                .mapToInt(r -> {
                    int score = 40; // base
                    long days = daysSincePush(r);
                    if (days < 7) score += 25;
                    else if (days < 30) score += 15;
                    else if (days < 90) score += 5;
                    else score -= 10;

                    if (r.getOpenIssues() <= 3) score += 10;
                    else if (r.getOpenIssues() <= 10) score += 5;
                    else if (r.getOpenIssues() > 30) score -= 5;

                    if (r.getSize() > 100) score += 5;
                    if (r.getForks() > 0) score += 5; // maintenance needed for forks

                    return Math.max(0, Math.min(score, 100));
                })
                .average()
                .orElse(40);

        int score = (int) Math.round(avgMaintenance);

        MetricScore m = new MetricScore();
        m.setScore(score);
        m.setWeight(5);
        m.setLabel("Maintenance");
        m.setDescription("Long-term repository care: issue management, updates, and release cadence");
        m.setIcon("wrench");
        m.setTrend(score >= 60 ? "up" : score >= 35 ? "stable" : "down");

        if (score >= 70) {
            m.setExplanation("Excellent long-term maintenance with recent updates and managed issues");
            m.setImprovementSuggestion("Consider setting up automated dependency updates and release workflows");
        } else if (score >= 40) {
            m.setExplanation("Adequate maintenance but some repos could use attention");
            m.setImprovementSuggestion("Address open issues and update dependencies regularly");
        } else {
            m.setExplanation("Most repositories need maintenance attention");
            m.setImprovementSuggestion("Prioritize updating stale repositories and triaging open issues");
        }
        return m;
    }

    // ═══════════════════════════════════════════════════
    // AI INSIGHTS GENERATOR
    // ═══════════════════════════════════════════════════

    private DeveloperInsights generateInsights(
            DeveloperScoreResponse score,
            List<RepositoryResponse> repos,
            GitHubProfileResponse profile
    ) {
        DeveloperInsights insights = new DeveloperInsights();

        int s = score.getOverallScore();
        String name = profile != null && profile.getName() != null ? profile.getName() : score.getUsername();

        // Overall assessment
        if (s >= 80) {
            insights.setOverallAssessment(name + " is an Elite developer with outstanding GitHub activity, code quality, and community presence. This profile demonstrates professional-grade software engineering practices.");
        } else if (s >= 65) {
            insights.setOverallAssessment(name + " is an Advanced developer with strong coding practices and consistent contributions. The profile shows solid software engineering fundamentals with room for deeper community engagement.");
        } else if (s >= 50) {
            insights.setOverallAssessment(name + " is a Proficient developer with good activity and decent repository quality. Focused effort on collaboration and documentation could significantly boost the score.");
        } else if (s >= 35) {
            insights.setOverallAssessment(name + " is an Intermediate developer showing some activity and basic repository practices. Increasing contribution frequency and improving repo documentation are the key growth areas.");
        } else if (s >= 20) {
            insights.setOverallAssessment(name + " is a Beginner developer with limited GitHub activity. Consistent contributions and better repository practices would dramatically improve this score.");
        } else {
            insights.setOverallAssessment(name + " is a Newcomer to GitHub or has limited public activity. Starting with regular contributions and well-documented projects is the best path forward.");
        }

        // Strongest skill
        String[][] skills = {
                {"Contribution Recency", "contributionRecency"},
                {"Commit Frequency", "commitFrequency"},
                {"Repository Health", "repositoryHealth"},
                {"Repository Quality", "repositoryQuality"},
                {"Contribution Consistency", "contributionConsistency"},
                {"Language Diversity", "languageDiversity"},
                {"Collaboration", "collaboration"},
                {"Open Source Impact", "openSourceImpact"},
                {"Popularity", "popularity"},
                {"Maintenance", "maintenance"}
        };
        int[] values = {score.getContributionRecency(), score.getCommitFrequency(), score.getRepositoryHealth(),
                score.getRepositoryQuality(), score.getContributionConsistency(), score.getLanguageDiversity(),
                score.getCollaboration(), score.getOpenSourceImpact(), score.getPopularity(), score.getMaintenance()};

        int maxIdx = 0, minIdx = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[maxIdx]) maxIdx = i;
            if (values[i] < values[minIdx]) minIdx = i;
        }

        insights.setStrongestSkill(skills[maxIdx][0]);
        insights.setWeakestArea(skills[minIdx][0]);

        // Collaboration analysis
        int collab = score.getCollaboration();
        if (collab >= 60) {
            insights.setCollaborationAnalysis("Strong collaborator with active community engagement. The developer's repos show healthy fork and issue activity, indicating community interest and collaborative development.");
        } else if (collab >= 30) {
            insights.setCollaborationAnalysis("Moderate collaboration signals. The developer engages with the community but could benefit from more active participation in pull requests and discussions.");
        } else {
            insights.setCollaborationAnalysis("Limited collaboration activity. Increasing engagement with other developers through PRs, code reviews, and community discussions would strengthen this area.");
        }

        // Open source impact
        int impact = score.getOpenSourceImpact();
        if (impact >= 50) {
            insights.setOpenSourceImpact("Meaningful open-source presence with projects that have attracted community attention through stars, forks, and contributions.");
        } else if (impact >= 20) {
            insights.setOpenSourceImpact("Some open-source visibility. Continuing to build public projects and contributing to existing ones will increase impact over time.");
        } else {
            insights.setOpenSourceImpact("Early stage open-source journey. Focus on building useful tools and sharing them with the developer community to grow impact.");
        }

        // Technology expertise
        String[] langs = score.getLanguages();
        if (langs != null && langs.length > 0) {
            String langStr = String.join(", ", Arrays.copyOf(langs, Math.min(langs.length, 5)));
            insights.setTechnologyExpertise("Technology stack includes " + langStr +
                    (langs.length > 5 ? " and " + (langs.length - 5) + " more" : "") + ".");
        } else {
            insights.setTechnologyExpertise("Technology stack could not be determined from public repositories.");
        }

        // Activity trend
        int recency = score.getContributionRecency();
        if (recency >= 60) {
            insights.setActivityTrend("Active contributor — most repositories have been updated within the last month, indicating strong ongoing engagement.");
        } else if (recency >= 30) {
            insights.setActivityTrend("Moderately active — some recent contributions but overall activity could be more consistent.");
        } else {
            insights.setActivityTrend("Low recent activity — most repositories have not been updated in several months.");
        }

        // Repo quality observations
        int quality = score.getRepositoryQuality();
        int health = score.getRepositoryHealth();
        if (quality >= 70 && health >= 70) {
            insights.setRepositoryQualityObs("Excellent repository quality with well-documented, properly structured codebases that serve as good examples of software craftsmanship.");
        } else if (quality >= 40 || health >= 40) {
            insights.setRepositoryQualityObs("Adequate repository quality but documentation, licensing, and project structure could be improved for several repositories.");
        } else {
            insights.setRepositoryQualityObs("Repository quality needs attention — focus on adding README files, licenses, and improving project documentation.");
        }

        // Recommendations
        StringBuilder rec = new StringBuilder();
        if (recency < 50) rec.append("Increase contribution frequency by committing to repositories at least weekly. ");
        if (health < 50) rec.append("Add README files, licenses, and topics to all repositories. ");
        if (collab < 50) rec.append("Engage more with the open-source community through PRs and issue discussions. ");
        if (quality < 50) rec.append("Improve code documentation and project structure across repositories. ");
        if (score.getLanguageDiversity() < 40) rec.append("Explore new programming languages and frameworks. ");
        if (impact < 30) rec.append("Build and share more public projects to increase open-source presence. ");
        if (rec.isEmpty()) rec.append("Continue the excellent work! Focus on mentoring others and contributing to major open-source projects.");

        insights.setRecommendations(rec.toString().trim());

        return insights;
    }

    // ═══════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════

    private long daysSincePush(RepositoryResponse r) {
        try {
            if (r.getPushedAt() != null) {
                Instant pushed = ZonedDateTime.parse(r.getPushedAt()).toInstant();
                return Duration.between(pushed, Instant.now()).toDays();
            }
        } catch (Exception e) {
            // A malformed pushedAt would otherwise silently skew the health
            // metric — log the actual value so API/format regressions surface
            // instead of being masked by the 365-day fallback.
            log.warn("Failed to parse pushedAt '{}' for repo {} — falling back to 365 days",
                    r.getPushedAt(), r.getFullName(), e);
        }
        return 365;
    }

    String determineLevel(int score) {
        if (score >= 90) return "Elite 🏆";
        if (score >= 80) return "Expert 🏅";
        if (score >= 65) return "Advanced 🚀";
        if (score >= 50) return "Proficient 💼";
        if (score >= 35) return "Intermediate 📘";
        if (score >= 20) return "Beginner 🌱";
        return "Newcomer 🌟";
    }
}
