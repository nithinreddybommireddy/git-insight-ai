package com.gitinsight.githubservice.dto.response;

import lombok.Data;

@Data
public class GitHubProfileResponse {

    private Long githubId;
    private String username;
    private String name;
    private String avatarUrl;
    private String profileUrl;

    private String bio;
    private String company;
    private String location;
    private String website;
    private String email;
    private String twitterUsername;
    private Boolean hireable;

    private Integer publicRepositories;
    private Integer publicGists;
    private Integer followers;
    private Integer following;

    private String createdAt;
    private String updatedAt;
}
