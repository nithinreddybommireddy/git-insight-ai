package com.gitinsight.authservice.dto.response;

import java.time.LocalDateTime;

/**
 * Summary DTO for the Job Match history endpoint.
 * Lightweight view — does not include full results.
 */
public record JobMatchJobSummary(
        Long jobId,
        String status,
        String jobTitle,
        String source,
        int total,
        int processed,
        int failed,
        int progressPercent,
        boolean aiEnabled,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String errorMessage
) {}
