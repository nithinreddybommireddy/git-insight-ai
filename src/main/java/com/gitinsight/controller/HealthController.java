package com.gitinsight.controller;

import com.gitinsight.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<String> health() {

        return new ApiResponse<>(
                true,
                "Application Running Successfully",
                "GitInsight AI Backend"
        );
    }
}

