package com.gitinsight.githubservice.dto.response;

import lombok.Data;

@Data
public class DeveloperScoreResponse {

    private String username;
    private int overallScore;
    private int totalStars;
    private int totalForks;
    private int totalRepositories;
    private int languageCount;
    private String[] languages;
    private int avgHealthScore;
    private int avgPopularityScore;
    private int avgMaintenanceScore;
    private String level;

    public static DeveloperScoreResponse empty(String username) {
        DeveloperScoreResponse score = new DeveloperScoreResponse();
        score.setUsername(username);
        score.setOverallScore(0);
        score.setTotalStars(0);
        score.setTotalForks(0);
        score.setTotalRepositories(0);
        score.setLanguageCount(0);
        score.setLanguages(new String[0]);
        score.setAvgHealthScore(0);
        score.setAvgPopularityScore(0);
        score.setAvgMaintenanceScore(0);
        score.setLevel("N/A");
        return score;
    }
}
