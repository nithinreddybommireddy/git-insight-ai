package com.gitinsight.githubservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.service.GitHubService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/profile/{username}")
    public ApiResponse<GitHubProfileResponse> getProfile(
            @PathVariable String username) {

        GitHubProfileResponse profile = gitHubService.getProfile(username);

        return new ApiResponse<>(
                true,
                "GitHub profile fetched successfully.",
                profile
        );
    }
}
