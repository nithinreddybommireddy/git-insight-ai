package com.gitinsight.githubservice.dto.response;

import lombok.Data;

@Data
public class DeveloperScoreResponse {

    private String username;
    private int overallScore;
    private String level;

    // Raw metric values
    private int contributionRecency;
    private int commitFrequency;
    private int repositoryHealth;
    private int repositoryQuality;
    private int contributionConsistency;
    private int languageDiversity;
    private int collaboration;
    private int openSourceImpact;
    private int popularity;
    private int maintenance;

    // Detailed breakdown for each metric
    private MetricScore contributionRecencyDetails;
    private MetricScore commitFrequencyDetails;
    private MetricScore repositoryHealthDetails;
    private MetricScore repositoryQualityDetails;
    private MetricScore contributionConsistencyDetails;
    private MetricScore languageDiversityDetails;
    private MetricScore collaborationDetails;
    private MetricScore openSourceImpactDetails;
    private MetricScore popularityDetails;
    private MetricScore maintenanceDetails;

    // Legacy fields for backward compatibility
    private int totalStars;
    private int totalForks;
    private int totalRepositories;
    private int languageCount;
    private String[] languages;
    private int avgHealthScore;
    private int avgPopularityScore;
    private int avgMaintenanceScore;
    private int contributionRecencyScore;
    private int commitFrequencyScore;
    private int consistencyScore;

    // AI-powered insights
    private DeveloperInsights insights;

    @Data
    public static class MetricScore {
        private int score;
        private int weight;
        private String label;
        private String description;
        private String explanation;
        private String improvementSuggestion;
        private String trend; // "up", "down", "stable"
        private String icon;
    }

    @Data
    public static class DeveloperInsights {
        private String overallAssessment;
        private String strongestSkill;
        private String weakestArea;
        private String collaborationAnalysis;
        private String openSourceImpact;
        private String technologyExpertise;
        private String activityTrend;
        private String repositoryQualityObs;
        private String recommendations;
    }

    public static DeveloperScoreResponse empty(String username) {
        DeveloperScoreResponse score = new DeveloperScoreResponse();
        score.setUsername(username);
        score.setOverallScore(0);
        score.setLevel("N/A");
        score.setTotalStars(0);
        score.setTotalForks(0);
        score.setTotalRepositories(0);
        score.setLanguageCount(0);
        score.setLanguages(new String[0]);
        score.setAvgHealthScore(0);
        score.setAvgPopularityScore(0);
        score.setAvgMaintenanceScore(0);
        score.setContributionRecencyScore(0);
        score.setCommitFrequencyScore(0);
        score.setConsistencyScore(0);
        score.setContributionRecency(0);
        score.setCommitFrequency(0);
        score.setRepositoryHealth(0);
        score.setRepositoryQuality(0);
        score.setContributionConsistency(0);
        score.setLanguageDiversity(0);
        score.setCollaboration(0);
        score.setOpenSourceImpact(0);
        score.setPopularity(0);
        score.setMaintenance(0);
        return score;
    }
}
