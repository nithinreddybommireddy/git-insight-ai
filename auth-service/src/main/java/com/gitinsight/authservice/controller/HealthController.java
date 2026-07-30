package com.gitinsight.authservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/", "/api/health"})
    public ApiResponse<String> health() {
        return new ApiResponse<>(
                true,
                "Auth Service Running Successfully",
                "Auth Service - GitInsight AI"
        );
    }
}
