package com.gitinsight.authservice.dto.response;

import java.time.LocalDateTime;

/**
 * Status/response DTO for the async Job Match polling endpoint.
 * Returned by both {@code POST /match/async} (immediate) and
 * {@code GET /match/{jobId}} (progress + result).
 */
public record JobMatchJobStatus(
        Long jobId,
        String status,           // QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED
        String jobTitle,
        int total,
        int processed,
        int failed,
        int progressPercent,     // 0–100, computed as (processed + failed) * 100 / total
        boolean aiEnabled,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,    // non-null only for FAILED
        JobMatchResponse result // non-null only for COMPLETED/PARTIAL
) {

    /** Compute progress percentage from counters. */
    public static int computeProgress(int processed, int failed, int total) {
        if (total <= 0) return 0;
        return (int) Math.min(100, ((long) processed + failed) * 100 / total);
    }
}
