package com.gitinsight.githubservice.service.impl;

import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.GitHubUserApiResponse;
import com.gitinsight.githubservice.service.GitHubService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class GitHubServiceImpl implements GitHubService {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestClient restClient;

    public GitHubServiceImpl() {
        this.restClient = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0")
                .build();
    }

    @Override
    public GitHubProfileResponse getProfile(String username) {
        GitHubUserApiResponse apiResponse;

        try {
            apiResponse = restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUserApiResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("GitHub user '" + username + "' not found.");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new RuntimeException("GitHub API rate limit exceeded. Please try again later.");
        }

        if (apiResponse == null) {
            throw new RuntimeException("Failed to fetch profile for user: " + username);
        }

        return mapToProfileResponse(apiResponse);
    }

    private GitHubProfileResponse mapToProfileResponse(GitHubUserApiResponse api) {
        GitHubProfileResponse profile = new GitHubProfileResponse();

        profile.setGithubId(api.getGithubId());
        profile.setUsername(api.getUsername());
        profile.setName(api.getName());
        profile.setAvatarUrl(api.getAvatarUrl());
        profile.setProfileUrl(api.getProfileUrl());
        profile.setBio(api.getBio());
        profile.setCompany(api.getCompany());
        profile.setLocation(api.getLocation());
        profile.setWebsite(api.getWebsite());
        profile.setEmail(api.getEmail());
        profile.setTwitterUsername(api.getTwitterUsername());
        profile.setHireable(api.getHireable());
        profile.setPublicRepositories(api.getPublicRepositories());
        profile.setPublicGists(api.getPublicGists());
        profile.setFollowers(api.getFollowers());
        profile.setFollowing(api.getFollowing());
        profile.setCreatedAt(api.getCreatedAt());
        profile.setUpdatedAt(api.getUpdatedAt());

        return profile;
    }
}
