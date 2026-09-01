package com.gitinsight.authservice.repository;

import com.gitinsight.authservice.entity.JobMatchJob;
import com.gitinsight.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobMatchJobRepository extends JpaRepository<JobMatchJob, Long> {

    /** Recent jobs for a recruiter, newest first. */
    List<JobMatchJob> findByRecruiterOrderByCreatedAtDesc(User recruiter);

    /** Find all jobs in a given status (for recovery). */
    List<JobMatchJob> findByStatus(JobMatchJob.JobStatus status);

    /**
     * Atomically claim a QUEUED job by transitioning it to RUNNING and assigning
     * a unique execution token. Returns 1 if this caller claimed it, 0 if another
     * worker already did. This is the ONLY mechanism that transitions QUEUED → RUNNING.
     *
     * <p>The execution token is a UUID that uniquely identifies the owning worker.
     * All subsequent updates must include this token to prevent stale-worker overwrites.
     */
    @Modifying
    @Query("UPDATE JobMatchJob j SET j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.RUNNING, " +
           "j.startedAt = CURRENT_TIMESTAMP, j.executionToken = :executionToken " +
           "WHERE j.id = :id AND j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.QUEUED")
    int atomicClaimJob(@Param("id") Long id, @Param("executionToken") String executionToken);

    /**
     * Atomically complete a RUNNING job. Only succeeds if the job is still RUNNING
     * AND owned by this worker (executionToken match). Returns 1 if updated, 0 otherwise.
     *
     * <p>This prevents stale workers from overwriting a recovered job's state.
     */
    @Modifying
    @Query("UPDATE JobMatchJob j SET j.status = :newStatus, j.resultJson = :resultJson, " +
           "j.processed = :processed, j.failed = :failed, j.total = :total, " +
           "j.jobTitle = :jobTitle, j.completedAt = CURRENT_TIMESTAMP " +
           "WHERE j.id = :id AND j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.RUNNING " +
           "AND j.executionToken = :executionToken")
    int atomicCompleteJob(
            @Param("id") Long id,
            @Param("executionToken") String executionToken,
            @Param("newStatus") JobMatchJob.JobStatus newStatus,
            @Param("resultJson") String resultJson,
            @Param("processed") int processed,
            @Param("failed") int failed,
            @Param("total") int total,
            @Param("jobTitle") String jobTitle);

    /**
     * Atomically fail a RUNNING job. Only succeeds if the job is still RUNNING
     * AND owned by this worker (executionToken match). Returns 1 if updated, 0 otherwise.
     */
    @Modifying
    @Query("UPDATE JobMatchJob j SET j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.FAILED, " +
           "j.errorMessage = :errorMessage, j.completedAt = CURRENT_TIMESTAMP " +
           "WHERE j.id = :id AND j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.RUNNING " +
           "AND j.executionToken = :executionToken")
    int atomicFailJob(@Param("id") Long id, @Param("executionToken") String executionToken,
                      @Param("errorMessage") String errorMessage);

    /**
     * Recovery: find RUNNING jobs stuck from a previous process (started more than {@code staleThreshold} ago).
     * Resets them to QUEUED and clears the stale execution token so the old worker can no longer
     * complete/update the job. The next worker to claim it will receive a new execution token.
     */
    @Modifying
    @Query("UPDATE JobMatchJob j SET j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.QUEUED, " +
           "j.startedAt = NULL, j.executionToken = NULL " +
           "WHERE j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.RUNNING " +
           "AND j.startedAt < :staleThreshold")
    int recoverStaleJobs(@Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * Mark a QUEUED job as FAILED (for queue-full rejection).
     */
    @Modifying
    @Query("UPDATE JobMatchJob j SET j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.FAILED, " +
           "j.errorMessage = :errorMessage, j.completedAt = CURRENT_TIMESTAMP " +
           "WHERE j.id = :id AND j.status = com.gitinsight.authservice.entity.JobMatchJob$JobStatus.QUEUED")
    int markQueuedJobFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
}
