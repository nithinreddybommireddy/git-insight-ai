package com.gitinsight.githubservice.dto.response;

import lombok.Data;

@Data
public class RepositoryResponse {

    private Long githubId;
    private String name;
    private String fullName;
    private String description;
    private String htmlUrl;
    private String homepage;
    private String language;
    private boolean fork;
    private String defaultBranch;

    private int stars;
    private int forks;
    private int openIssues;
    private int watchers;
    private int size;

    private String[] topics;
    private boolean hasLicense;

    private String createdAt;
    private String updatedAt;
    private String pushedAt;

    private boolean archived;
    private boolean disabled;

    private int healthScore;
    private int documentationScore;
    private int maintenanceScore;
    private int popularityScore;
    private int activityScore;
}
