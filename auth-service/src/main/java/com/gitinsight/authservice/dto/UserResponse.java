package com.gitinsight.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String name;
    private String avatarUrl;
    private String role;
    private String githubUsername;
    private LocalDateTime createdAt;
}
