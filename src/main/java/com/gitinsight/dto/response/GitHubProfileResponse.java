package com.gitinsight.dto.response;


import lombok.Data;

@Data
public class GitHubProfileResponse {

    // Basic Profile
    private Long githubId;
    private String username;
    private String name;
    private String avatarUrl;
    private String profileUrl;

    // Personal Information
    private String bio;
    private String company;
    private String location;
    private String website;
    private String email;
    private String twitterUsername;
    private Boolean hireable;

    // GitHub Statistics
    private Integer publicRepositories;
    private Integer publicGists;
    private Integer followers;
    private Integer following;

    // Dates
    private String createdAt;
    private String updatedAt;
}

