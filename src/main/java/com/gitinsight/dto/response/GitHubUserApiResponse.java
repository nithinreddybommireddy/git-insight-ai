package com.gitinsight.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubUserApiResponse {

    @JsonProperty("id")
    private Long githubId;

    @JsonProperty("login")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("html_url")
    private String profileUrl;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("company")
    private String company;

    @JsonProperty("location")
    private String location;

    @JsonProperty("blog")
    private String website;

    @JsonProperty("email")
    private String email;

    @JsonProperty("twitter_username")
    private String twitterUsername;

    @JsonProperty("hireable")
    private Boolean hireable;

    @JsonProperty("public_repos")
    private Integer publicRepositories;

    @JsonProperty("public_gists")
    private Integer publicGists;

    @JsonProperty("followers")
    private Integer followers;

    @JsonProperty("following")
    private Integer following;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}
