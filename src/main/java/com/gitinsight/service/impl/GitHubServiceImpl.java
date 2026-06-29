package com.gitinsight.service.impl;


import com.gitinsight.dto.response.GitHubProfileResponse;
import com.gitinsight.service.GitHubService;
import org.springframework.stereotype.Service;

@Service
public class GitHubServiceImpl implements GitHubService {

    @Override
    public GitHubProfileResponse getProfile(String username) {

        GitHubProfileResponse profile = new GitHubProfileResponse();

        profile.setGithubId(123456L);
        profile.setUsername(username);
        profile.setName("Dummy User");
        profile.setBio("Java Full Stack Developer");
        profile.setCompany("GitInsight");
        profile.setLocation("Hyderabad");
        profile.setFollowers(250);
        profile.setFollowing(45);
        profile.setPublicRepositories(15);

        return profile;
    }
}
