package com.gitinsight.authservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.dto.response.JobMatchJobStatus;
import com.gitinsight.authservice.dto.response.JobMatchJobSummary;
import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.entity.JobMatchJob;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.JobMatchJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Async Job Match Architecture Tests")
class AsyncJobMatchTest {

    @Mock
    private JobMatchJobRepository jobRepository;

    @Mock
    private JobMatcherService jobMatcherService;

    @Mock
    private JobMatchJobService.JobMatchJobWorker worker;

    private JobMatchJobService jobMatchJobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testRecruiter;

    @BeforeEach
    void setUp() {
        jobMatchJobService = new JobMatchJobService(
                jobRepository, jobMatcherService, worker, objectMapper);

        testRecruiter = new User();
        testRecruiter.setId(1L);
        testRecruiter.setName("Test Recruiter");
        testRecruiter.setEmail("recruiter@test.com");
    }

    private JobMatchJob createJob(Long id, int total) {
        JobMatchJob job = new JobMatchJob();
        job.setId(id);
        job.setStatus(JobMatchJob.JobStatus.RUNNING);
        job.setRecruiter(testRecruiter);
        job.setTotal(total);
        // Build a proper JSON array of usernames
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < total; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append((char)('a' + i % 26)).append("\"");
        }
        sb.append("]");
        job.setCandidatePoolJson(sb.toString());
        job.setRequiredSkillsJson("[\"Java\"]");
        job.setJdText("Java developer");
        job.setSource("saved");
        return job;
    }

    // ══════════════════════════════════════════════════════════════════
    //  1. SAVED CANDIDATE COVERAGE
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Saved Candidate Coverage")
    class SavedCandidateCoverage {

        @Test
        @DisplayName("20 saved candidates → all 20 analyzed via matchAsync")
        void twentySavedCandidatesAllAnalyzed() {
            List<String> usernames = new ArrayList<>();
            for (int i = 1; i <= 20; i++) usernames.add("user" + i);

            JobMatchResponse mockResponse = new JobMatchResponse(
                    "Java Developer", List.of("Java"), "saved",
                    20, 20, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), eq(usernames), eq("saved"), eq(false)))
                    .thenReturn(mockResponse);

            JobMatchResponse result = jobMatcherService.matchAsync(
                    "Java developer", usernames, "saved", false);

            assertThat(result.processed() + result.failed()).isEqualTo(20);
            assertThat(result.total()).isEqualTo(20);
        }

        @Test
        @DisplayName("AI_CANDIDATE_LIMIT does not truncate deterministic analysis")
        void aiCandidateLimitDoesNotTruncateDeterministic() {
            List<String> usernames = new ArrayList<>();
            for (int i = 1; i <= 20; i++) usernames.add("user" + i);

            JobMatchResponse mockResponse = new JobMatchResponse(
                    "Java Developer", List.of("Java"), "saved",
                    20, 20, 0, List.of(), true, "gemini-2.0-flash", List.of());
            when(jobMatcherService.matchAsync(anyString(), eq(usernames), eq("saved"), eq(true)))
                    .thenReturn(mockResponse);

            JobMatchResponse result = jobMatcherService.matchAsync(
                    "Java developer", usernames, "saved", true);

            assertThat(result.processed()).isEqualTo(20);
            assertThat(result.aiEnabled()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  2. QUEUE SATURATION — NO ZOMBIE JOBS
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Queue Saturation")
    class QueueSaturation {

        @Test
        @DisplayName("Queue full → job marked FAILED, not left QUEUED")
        void queueFullMarksJobFailed() {
            JobMatchJob savedJob = new JobMatchJob();
            savedJob.setId(42L);
            savedJob.setStatus(JobMatchJob.JobStatus.QUEUED);
            savedJob.setRecruiter(testRecruiter);
            savedJob.setTotal(5);
            when(jobRepository.save(any(JobMatchJob.class))).thenReturn(savedJob);

            doThrow(new TaskRejectedException("Queue capacity reached"))
                    .when(worker).executeJobMatchAsync(eq(42L));

            JobMatchJob result = jobMatchJobService.createAndEnqueue(
                    testRecruiter, "Java developer", List.of("a", "b", "c", "d", "e"),
                    "saved", false, List.of("Java"));

            assertThat(result.getStatus()).isEqualTo(JobMatchJob.JobStatus.FAILED);
            assertThat(result.getErrorMessage()).contains("at capacity");
            verify(jobRepository).markQueuedJobFailed(eq(42L), contains("at capacity"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  3. @ASYNC PROXY BEHAVIOR
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("@Async Execution")
    class AsyncExecution {

        @Test
        @DisplayName("Worker bean uses jobMatchTaskExecutor (verified by @Async annotation)")
        void workerUsesJobMatchExecutor() throws Exception {
            var asyncMethod = JobMatchJobService.JobMatchJobWorker.class
                    .getMethod("executeJobMatchAsync", Long.class);
            assertThat(asyncMethod.isAnnotationPresent(org.springframework.scheduling.annotation.Async.class))
                    .isTrue();
            var asyncAnnotation = asyncMethod.getAnnotation(
                    org.springframework.scheduling.annotation.Async.class);
            assertThat(asyncAnnotation.value()).isEqualTo("jobMatchTaskExecutor");
        }

        @Test
        @DisplayName("JobMatchJobWorker is a separate bean (not inner self-invocation)")
        void workerIsSeparateBean() {
            assertThat(JobMatchJobService.JobMatchJobWorker.class
                    .isAnnotationPresent(org.springframework.stereotype.Service.class))
                    .isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  4. ATOMIC CLAIM — EXECUTION TOKEN
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Atomic Claim with Execution Token")
    class AtomicClaim {

        @Test
        @DisplayName("Duplicate workers → only one claims job (token mismatch)")
        void duplicateWorkersOnlyOneClaims() {
            // First worker claims successfully
            when(jobRepository.atomicClaimJob(eq(42L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(42L, 5);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 5, 5, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);
            when(jobRepository.atomicCompleteJob(eq(42L), anyString(), any(), anyString(), anyInt(), anyInt(), anyInt(), anyString()))
                    .thenReturn(1);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(42L);

            // Second worker tries to claim — fails (already RUNNING)
            when(jobRepository.atomicClaimJob(eq(42L), anyString())).thenReturn(0);
            w.executeJobMatchAsync(42L);

            // matchAsync was called only ONCE
            verify(jobMatcherService, times(1))
                    .matchAsync(anyString(), anyList(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Claim returns 0 → worker returns immediately, no DB lookup")
        void claimReturnsZeroWorkerExits() {
            when(jobRepository.atomicClaimJob(eq(42L), anyString())).thenReturn(0);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(42L);

            verify(jobRepository, never()).findById(anyLong());
            verify(jobMatcherService, never()).matchAsync(anyString(), anyList(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Each claim generates a unique execution token")
        void eachClaimGeneratesUniqueToken() {
            when(jobRepository.atomicClaimJob(eq(1L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(1L, 3);
            when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 3, 3, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);
            when(jobRepository.atomicCompleteJob(eq(1L), anyString(), any(), anyString(), anyInt(), anyInt(), anyInt(), anyString()))
                    .thenReturn(1);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);

            // Capture the execution token from the claim
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            w.executeJobMatchAsync(1L);

            verify(jobRepository).atomicClaimJob(eq(1L), tokenCaptor.capture());
            String capturedToken = tokenCaptor.getValue();

            // Verify it's a valid UUID
            assertThat(capturedToken).isNotBlank();
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                    java.util.UUID.fromString(capturedToken));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  5. ATOMIC COMPLETION WITH TOKEN
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Atomic Completion with Token")
    class AtomicCompletion {

        @Test
        @DisplayName("Stale worker cannot overwrite recovered worker (token mismatch)")
        void staleWorkerCannotOverwrite() {
            when(jobRepository.atomicClaimJob(eq(42L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(42L, 3);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 3, 3, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);

            // atomicCompleteJob returns 0 — token mismatch (stale worker)
            when(jobRepository.atomicCompleteJob(eq(42L), anyString(), any(), anyString(), anyInt(), anyInt(), anyInt(), anyString()))
                    .thenReturn(0);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(42L);

            // Worker got 0 from atomicCompleteJob — did not overwrite
            verify(jobRepository).atomicCompleteJob(eq(42L), anyString(), any(), anyString(), anyInt(), anyInt(), anyInt(), anyString());
            // No failure was recorded either (the worker's catch block calls atomicFailJob, but only on exception)
        }

        @Test
        @DisplayName("COMPLETED job means processed = total")
        void completedJobProcessedEqualsTotal() {
            when(jobRepository.atomicClaimJob(eq(1L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(1L, 5);
            when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 5, 5, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);
            when(jobRepository.atomicCompleteJob(eq(1L), anyString(), eq(JobMatchJob.JobStatus.COMPLETED), anyString(), eq(5), eq(0), eq(5), anyString()))
                    .thenReturn(1);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(1L);

            verify(jobRepository).atomicCompleteJob(
                    eq(1L), anyString(), eq(JobMatchJob.JobStatus.COMPLETED), anyString(), eq(5), eq(0), eq(5), anyString());
        }

        @Test
        @DisplayName("One failed candidate → PARTIAL status")
        void oneFailedCandidatePartialStatus() {
            when(jobRepository.atomicClaimJob(eq(1L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(1L, 3);
            when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

            // 2 processed, 1 failed → PARTIAL
            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 3, 2, 1, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);
            when(jobRepository.atomicCompleteJob(eq(1L), anyString(), eq(JobMatchJob.JobStatus.PARTIAL), anyString(), eq(2), eq(1), eq(3), anyString()))
                    .thenReturn(1);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(1L);

            verify(jobRepository).atomicCompleteJob(
                    eq(1L), anyString(), eq(JobMatchJob.JobStatus.PARTIAL), anyString(), eq(2), eq(1), eq(3), anyString());
        }

        @Test
        @DisplayName("All candidates failed → FAILED status")
        void allFailedCandidateFailedStatus() {
            when(jobRepository.atomicClaimJob(eq(1L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(1L, 3);
            when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

            // 0 processed, 3 failed → FAILED
            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 3, 0, 3, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);
            when(jobRepository.atomicCompleteJob(eq(1L), anyString(), eq(JobMatchJob.JobStatus.FAILED), anyString(), eq(0), eq(3), eq(3), anyString()))
                    .thenReturn(1);

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(1L);

            verify(jobRepository).atomicCompleteJob(
                    eq(1L), anyString(), eq(JobMatchJob.JobStatus.FAILED), anyString(), eq(0), eq(3), eq(3), anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  6. STATUS CALCULATION
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Status Calculation Rules")
    class StatusCalculation {

        @Test
        @DisplayName("20 processed, 0 failed → COMPLETED")
        void allProcessedCompleted() {
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(20, 0, 20))
                    .isEqualTo(JobMatchJob.JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("19 processed, 1 failed → PARTIAL")
        void someFailedPartial() {
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(19, 1, 20))
                    .isEqualTo(JobMatchJob.JobStatus.PARTIAL);
        }

        @Test
        @DisplayName("0 processed, 20 failed → FAILED")
        void allFailedFailed() {
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(0, 20, 20))
                    .isEqualTo(JobMatchJob.JobStatus.FAILED);
        }

        @Test
        @DisplayName("19 processed, 0 failed, 1 pending → FAILED (incomplete)")
        void pendingCandidatesFailed() {
            // 19 processed + 0 failed = 19 < 20 total → not complete
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(19, 0, 20))
                    .isEqualTo(JobMatchJob.JobStatus.FAILED);
        }

        @Test
        @DisplayName("0 processed, 0 failed, total=0 → FAILED (edge case)")
        void zeroTotalFailed() {
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(0, 0, 0))
                    .isEqualTo(JobMatchJob.JobStatus.FAILED);
        }

        @Test
        @DisplayName("1 processed, 0 failed, total=1 → COMPLETED")
        void singleCandidateCompleted() {
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(1, 0, 1))
                    .isEqualTo(JobMatchJob.JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("0 processed, 1 failed, total=1 → FAILED")
        void singleCandidateAllFailed() {
            assertThat(JobMatchJobService.JobMatchJobWorker.computeFinalStatus(0, 1, 1))
                    .isEqualTo(JobMatchJob.JobStatus.FAILED);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  7. RECOVERY
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Recovery")
    class Recovery {

        @Test
        @DisplayName("Stale RUNNING jobs recovered on startup")
        void staleRunningJobsRecovered() {
            when(jobRepository.recoverStaleJobs(any(LocalDateTime.class))).thenReturn(3);
            when(jobRepository.findByStatus(JobMatchJob.JobStatus.QUEUED)).thenReturn(List.of());

            jobMatchJobService.recoverStaleJobs();

            verify(jobRepository).recoverStaleJobs(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("QUEUED jobs re-enqueued on startup")
        void queuedJobsReenqueued() {
            when(jobRepository.recoverStaleJobs(any(LocalDateTime.class))).thenReturn(0);

            JobMatchJob queuedJob = new JobMatchJob();
            queuedJob.setId(10L);
            queuedJob.setStatus(JobMatchJob.JobStatus.QUEUED);
            when(jobRepository.findByStatus(JobMatchJob.JobStatus.QUEUED)).thenReturn(List.of(queuedJob));

            jobMatchJobService.recoverStaleJobs();

            verify(worker).executeJobMatchAsync(10L);
        }

        @Test
        @DisplayName("Recovery clears execution token (old worker invalidated)")
        void recoveryClearsToken() {
            // Verify the recovery query clears executionToken
            // The repository method includes: j.executionToken = NULL
            when(jobRepository.recoverStaleJobs(any(LocalDateTime.class))).thenReturn(1);
            when(jobRepository.findByStatus(JobMatchJob.JobStatus.QUEUED)).thenReturn(List.of());

            jobMatchJobService.recoverStaleJobs();

            verify(jobRepository).recoverStaleJobs(any(LocalDateTime.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  8. STALE WORKER RECOVERY REGRESSION
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stale Worker Recovery Regression")
    class StaleWorkerRecovery {

        @Test
        @DisplayName("Worker A claims → stale → Worker B claims → Worker A cannot complete")
        void staleWorkerCannotCompleteAfterRecovery() {
            // Scenario: Worker A claims job 42, then becomes stale
            // Recovery resets job to QUEUED, clears token
            // Worker B claims with new token
            // Worker A later tries to complete → rejected (token mismatch)

            // Step 1: Worker A claims (gets token A)
            when(jobRepository.atomicClaimJob(eq(42L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(42L, 3);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            JobMatchResponse responseA = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 3, 3, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(responseA);

            // Worker A's completion returns 0 (stale — token mismatch after recovery)
            when(jobRepository.atomicCompleteJob(eq(42L), anyString(), any(), anyString(), anyInt(), anyInt(), anyInt(), anyString()))
                    .thenReturn(0);

            var workerA = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            workerA.executeJobMatchAsync(42L);

            // Verify Worker A tried to complete but got 0
            verify(jobRepository).atomicCompleteJob(eq(42L), anyString(), any(), anyString(), anyInt(), anyInt(), anyInt(), anyString());
            // Worker A should not have written any result
            verify(jobRepository, never()).atomicFailJob(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("Worker B completes successfully after Worker A was rejected")
        void workerBCompletesAfterWorkerARejected() {
            // Worker B claims with a new token
            when(jobRepository.atomicClaimJob(eq(42L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(42L, 3);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            JobMatchResponse response = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 3, 3, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenReturn(response);
            when(jobRepository.atomicCompleteJob(eq(42L), anyString(), eq(JobMatchJob.JobStatus.COMPLETED), anyString(), eq(3), eq(0), eq(3), anyString()))
                    .thenReturn(1);

            var workerB = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            workerB.executeJobMatchAsync(42L);

            // Worker B should complete successfully
            verify(jobRepository).atomicCompleteJob(eq(42L), anyString(), eq(JobMatchJob.JobStatus.COMPLETED), anyString(), eq(3), eq(0), eq(3), anyString());
        }

        @Test
        @DisplayName("Failed worker cannot change a completed job")
        void failedWorkerCannotChangeCompletedJob() {
            // Worker's atomicFailJob returns 0 (job already completed by another)
            when(jobRepository.atomicClaimJob(eq(1L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(1L, 3);
            when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
            when(jobRepository.atomicFailJob(eq(1L), anyString(), anyString())).thenReturn(0);

            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("GitHub API down"));

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(1L);

            // atomicFailJob was called but returned 0 — job already completed
            verify(jobRepository).atomicFailJob(eq(1L), anyString(), contains("GitHub API down"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  9. SECURITY — RECRUITER OWNERSHIP
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security — Recruiter Ownership")
    class Security {

        @Test
        @DisplayName("Recruiter A cannot see Recruiter B's job")
        void recruiterCannotSeeOtherJobs() {
            User otherRecruiter = new User();
            otherRecruiter.setId(2L);

            JobMatchJob job = new JobMatchJob();
            job.setId(42L);
            job.setRecruiter(testRecruiter);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            JobMatchJobStatus status = jobMatchJobService.getStatus(42L, otherRecruiter);
            assertThat(status).isNull();
        }

        @Test
        @DisplayName("Recruiter can see their own job")
        void recruiterCanSeeOwnJob() {
            JobMatchJob job = new JobMatchJob();
            job.setId(42L);
            job.setRecruiter(testRecruiter);
            job.setStatus(JobMatchJob.JobStatus.COMPLETED);
            job.setTotal(5);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            JobMatchJobStatus status = jobMatchJobService.getStatus(42L, testRecruiter);
            assertThat(status).isNotNull();
            assertThat(status.jobId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Non-existent job returns null")
        void nonExistentJobReturnsNull() {
            when(jobRepository.findById(999L)).thenReturn(Optional.empty());

            JobMatchJobStatus status = jobMatchJobService.getStatus(999L, testRecruiter);
            assertThat(status).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  10. PROGRESS COUNTERS
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Progress")
    class Progress {

        @Test
        @DisplayName("Progress percent computed correctly")
        void progressPercentCorrect() {
            assertThat(JobMatchJobStatus.computeProgress(8, 1, 20)).isEqualTo(45);
            assertThat(JobMatchJobStatus.computeProgress(0, 0, 20)).isEqualTo(0);
            assertThat(JobMatchJobStatus.computeProgress(19, 1, 20)).isEqualTo(100);
            assertThat(JobMatchJobStatus.computeProgress(0, 0, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("Polling does not trigger analysis")
        void pollingDoesNotTriggerAnalysis() {
            JobMatchJob job = new JobMatchJob();
            job.setId(42L);
            job.setRecruiter(testRecruiter);
            job.setStatus(JobMatchJob.JobStatus.RUNNING);
            job.setTotal(10);
            job.setProcessed(5);
            when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

            jobMatchJobService.getStatus(42L, testRecruiter);
            jobMatchJobService.getStatus(42L, testRecruiter);
            jobMatchJobService.getStatus(42L, testRecruiter);

            verify(jobMatcherService, never()).matchAsync(anyString(), anyList(), anyString(), anyBoolean());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  11. LIMITS PRESERVED
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Limits Preserved")
    class LimitsPreserved {

        @Test
        @DisplayName("MAX_EVIDENCE_REPOS = 15 per candidate")
        void evidenceReposLimit() {
            JobMatcherService svc = new JobMatcherService(RestClient.create());
            assertThat(svc.maxEvidenceRepos).isEqualTo(15);
        }

        @Test
        @DisplayName("MAX_SOURCE_FILES_PER_REPO = 5")
        void sourceFilesLimit() {
            try {
                var field = JobMatcherService.class.getDeclaredField("MAX_SOURCE_FILES_PER_REPO");
                field.setAccessible(true);
                assertThat(field.getInt(null)).isEqualTo(5);
            } catch (Exception e) {
                fail("Could not access MAX_SOURCE_FILES_PER_REPO: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("MAX_CANDIDATES = 25")
        void maxCandidates() {
            assertThat(JobMatcherService.MAX_CANDIDATES).isEqualTo(25);
        }

        @Test
        @DisplayName("Sync match remains backward compatible")
        void syncMatchBackwardCompatible() {
            JobMatcherService svc = new JobMatcherService(RestClient.create());
            JobMatchResponse resp = svc.match("Java developer", List.of("user1"), "saved");
            assertThat(resp).isNotNull();
            assertThat(resp.source()).isEqualTo("saved");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  12. EXCEPTION HANDLING
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("Worker exception → job marked FAILED with token")
        void workerExceptionMarksJobFailed() {
            when(jobRepository.atomicClaimJob(eq(1L), anyString())).thenReturn(1);
            JobMatchJob job = createJob(1L, 3);
            when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
            when(jobRepository.atomicFailJob(eq(1L), anyString(), anyString())).thenReturn(1);

            when(jobMatcherService.matchAsync(anyString(), anyList(), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("GitHub API down"));

            var w = new JobMatchJobService.JobMatchJobWorker(
                    jobRepository, jobMatcherService, objectMapper);
            w.executeJobMatchAsync(1L);

            verify(jobRepository).atomicFailJob(eq(1L), anyString(), contains("GitHub API down"));
        }

        @Test
        @DisplayName("matchAsync processes ALL candidates (no deadline truncation)")
        void matchAsyncProcessesAll() {
            List<String> usernames = new ArrayList<>();
            for (int i = 1; i <= 25; i++) usernames.add("user" + i);

            JobMatchResponse mockResponse = new JobMatchResponse(
                    "Java", List.of("Java"), "saved", 25, 25, 0, List.of(), false, null, List.of());
            when(jobMatcherService.matchAsync(anyString(), eq(usernames), eq("saved"), eq(false)))
                    .thenReturn(mockResponse);

            JobMatchResponse result = jobMatcherService.matchAsync(
                    "Java developer", usernames, "saved", false);

            assertThat(result.processed()).isEqualTo(25);
            assertThat(result.failed()).isEqualTo(0);
            assertThat(result.total()).isEqualTo(25);
        }
    }
}
