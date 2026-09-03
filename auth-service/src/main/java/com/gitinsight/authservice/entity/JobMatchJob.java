package com.gitinsight.authservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted state for an asynchronous Job Match request.
 * Created when a recruiter triggers async match; the background worker
 * transitions through QUEUED → RUNNING → COMPLETED/PARTIAL/FAILED.
 *
 * <p>Thread-safety: each worker generates a unique {@code executionToken}
 * (UUID) when claiming a job. ALL subsequent updates (completion, failure)
 * must include this token. If a stale/recovered worker attempts to update
 * a job it no longer owns, the WHERE clause returns 0 rows and the update
 * is rejected. This prevents stale workers from overwriting recovered jobs.
 */
@Entity
@Table(name = "job_match_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.QUEUED;

    @Column(length = 200)
    private String jobTitle;

    /** JSON array of required skill strings. */
    @Column(columnDefinition = "TEXT")
    private String requiredSkillsJson;

    /** "file" when usernames were uploaded, "saved" for the recruiter's pool. */
    @Column(length = 20)
    private String source;

    /** Truncated job description text (for AI step). */
    @Column(columnDefinition = "TEXT")
    private String jdText;

    /** JSON array of GitHub usernames to analyze. */
    @Column(columnDefinition = "TEXT")
    private String candidatePoolJson;

    private int total;
    private int processed;
    private int failed;

    /** Serialized {@code JobMatchResponse}. NULL while running. */
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    private boolean aiEnabled;

    /**
     * Unique execution token (UUID) assigned when a worker claims this job.
     * ALL worker updates (complete, fail) must include this token.
     * Prevents stale/recovered workers from overwriting a newer worker's state.
     * Exposed only internally — never sent to frontend API responses.
     */
    @Column(length = 36)
    private String executionToken;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum JobStatus {
        QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED
    }
}
