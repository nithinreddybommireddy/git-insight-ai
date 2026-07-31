package com.gitinsight.githubservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubRepoApiResponse {

    @JsonProperty("id")
    private Long githubId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("homepage")
    private String homepage;

    @JsonProperty("language")
    private String language;

    @JsonProperty("fork")
    private boolean fork;

    @JsonProperty("private")
    private boolean isPrivate;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("stargazers_count")
    private int stargazersCount;

    @JsonProperty("forks_count")
    private int forksCount;

    @JsonProperty("open_issues_count")
    private int openIssuesCount;

    @JsonProperty("watchers_count")
    private int watchersCount;

    @JsonProperty("size")
    private int size;

    @JsonProperty("topics")
    private String[] topics;

    @JsonProperty("license")
    private License license;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("pushed_at")
    private String pushedAt;

    @JsonProperty("archived")
    private boolean archived;

    @JsonProperty("disabled")
    private boolean disabled;

    @JsonProperty("is_template")
    private boolean isTemplate;

    @JsonProperty("has_issues")
    private boolean hasIssues;

    @JsonProperty("has_wiki")
    private boolean hasWiki;

    @JsonProperty("has_projects")
    private boolean hasProjects;

    @JsonProperty("allow_forking")
    private boolean allowForking;

    @Data
    public static class License {
        @JsonProperty("key")
        private String key;

        @JsonProperty("name")
        private String name;

        @JsonProperty("spdx_id")
        private String spdxId;
    }
}
