package com.gitinsight.analytics.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/", "/api/health"})
    public ApiResponse<String> health() {
        return new ApiResponse<>(
                true,
                "Analytics Service Running Successfully",
                "Analytics Service - GitInsight AI"
        );
    }
}
