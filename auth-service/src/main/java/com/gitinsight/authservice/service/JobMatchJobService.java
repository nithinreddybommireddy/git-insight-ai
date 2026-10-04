package com.gitinsight.authservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.dto.response.JobMatchJobStatus;
import com.gitinsight.authservice.dto.response.JobMatchJobSummary;
import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.entity.JobMatchJob;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.JobMatchJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manages the lifecycle of asynchronous Job Match requests.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create QUEUED jobs from controller requests</li>
 *   <li>Enqueue jobs to the bounded thread pool AFTER the creating transaction
 *       commits (AbortPolicy — never on HTTP thread)</li>
 *   <li>Atomically claim QUEUED→RUNNING to prevent duplicate execution</li>
 *   <li>Persist per-candidate progress while RUNNING (processed/failed)</li>
 *   <li>Atomically transition RUNNING→COMPLETED/PARTIAL to prevent stale overwrites</li>
 *   <li>Recover stale RUNNING/QUEUED jobs on application startup</li>
 * </ul>
 *
 * <p>Critical invariant: {@code @Async} is on a SEPARATE bean method so Spring's
 * proxy-based AOP intercepts the call and routes it to {@code jobMatchTaskExecutor}.
 * If the async method were on the same bean that calls it, Spring's proxy would
 * NOT intercept it (self-invocation bypasses the proxy). Therefore, the worker
 * lives in a dedicated {@code JobMatchJobWorker} inner class that is injected
 * as a separate Spring bean.
 *
 * <p>Enqueue-vs-commit invariant: the worker's first statement is
 * {@code atomicClaimJob} — an UPDATE filtered on {@code status = QUEUED}. If the
 * enqueue ran while the creating transaction was still open, the worker thread
 * could execute that UPDATE BEFORE the job's INSERT was committed, match zero
 * rows, and silently exit — leaving the job permanently QUEUED (observed in
 * production as job stuck at {@code status=QUEUED, startedAt=null, processed=0}).
 * The enqueue is therefore registered as an after-commit synchronization.
 */
@Service
public class JobMatchJobService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchJobService.class);

    /** Stale RUNNING threshold: if a job has been RUNNING for more than 10 minutes
     *  with no progress, the previous process is assumed dead. */
    private static final int STALE_RUNNING_MINUTES = 10;

    private final JobMatchJobRepository jobRepository;
    private final JobMatcherService jobMatcherService;
    private final JobMatchJobWorker worker;
    private final ObjectMapper objectMapper;
    /** Null in unit tests — statements then run inline without a wrapping transaction. */
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public JobMatchJobService(JobMatchJobRepository jobRepository,
                              JobMatcherService jobMatcherService,
                              JobMatchJobWorker worker,
                              ObjectMapper objectMapper,
                              TransactionTemplate transactionTemplate) {
        this.jobRepository = jobRepository;
        this.jobMatcherService = jobMatcherService;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** Convenience constructor used by unit tests (no transaction template). */
    public JobMatchJobService(JobMatchJobRepository jobRepository,
                              JobMatcherService jobMatcherService,
                              JobMatchJobWorker worker,
                              ObjectMapper objectMapper) {
        this(jobRepository, jobMatcherService, worker, objectMapper, null);
    }

    // ─────────────────────── Job Creation ───────────────────────

    /**
     * Create a QUEUED job and enqueue it for background processing.
     * If the thread pool rejects the job (queue full), the job is immediately
     * marked FAILED — it is never left permanently QUEUED.
     *
     * <p>The FULL job description text is persisted (async matching runs skill
     * extraction and classification on it, so truncating here silently dropped
     * mandatory requirements listed near the end of long JDs). The AI prompt
     * still applies its own smaller truncation downstream.
     *
     * <p>The enqueue happens AFTER this method's transaction commits (see class
     * javadoc): the worker thread must never observe a QUEUED row that is not
     * yet committed.
     *
     * @return the created job (with ID and status set)
     * @throws RuntimeException if the job cannot be created or enqueued
     */
    @Transactional
    public JobMatchJob createAndEnqueue(User recruiter, String jdText, List<String> usernames,
                                         String source, boolean aiEnabled,
                                         List<String> requiredSkills) {
        JobMatchJob job = new JobMatchJob();
        job.setRecruiter(recruiter);
        job.setStatus(JobMatchJob.JobStatus.QUEUED);
        job.setJdText(jdText);
        job.setCandidatePoolJson(toJson(usernames));
        job.setRequiredSkillsJson(toJson(requiredSkills));
        job.setSource(source);
        job.setAiEnabled(aiEnabled);
        job.setTotal(usernames.size());
        job.setProcessed(0);
        job.setFailed(0);
        // Separate effectively-final binding: the after-commit closure below
        // captures it (a reassigned local cannot be referenced from an inner class).
        final JobMatchJob saved = jobRepository.save(job);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Production path (@Transactional proxy): enqueue after COMMIT so the
            // worker's atomicClaimJob UPDATE is guaranteed to see the committed row.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        enqueueOrMarkFailed(saved);
                    } catch (Exception e) {
                        // A post-commit enqueue failure must never fail the HTTP
                        // response that already returned success — startup recovery
                        // re-enqueues QUEUED jobs as a safety net.
                        log.error("Post-commit enqueue failed for job {} — leaving QUEUED for recovery",
                                saved.getId(), e);
                    }
                }
            });
        } else {
            // Unit-test / no-transaction path: enqueue inline.
            enqueueOrMarkFailed(saved);
        }

        return saved;
    }

    /** Enqueue the job, or mark it FAILED when the bounded queue is full. */
    private void enqueueOrMarkFailed(JobMatchJob job) {
        try {
            worker.executeJobMatchAsync(job.getId());
        } catch (TaskRejectedException e) {
            log.warn("Job match {} rejected — queue full", job.getId());
            String message = "Job match service is at capacity. Please try again shortly.";
            inTx(() -> jobRepository.markQueuedJobFailed(job.getId(), message));
            job.setStatus(JobMatchJob.JobStatus.FAILED);
            job.setErrorMessage(message);
            job.setCompletedAt(LocalDateTime.now());
        }
    }

    // ─────────────────────── Status Queries ───────────────────────

    /**
     * Get the status of a job. Returns null if not found.
     */
    @Transactional(readOnly = true)
    public JobMatchJobStatus getStatus(Long jobId, User recruiter) {
        JobMatchJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getRecruiter().getId().equals(recruiter.getId())) {
            return null;
        }
        return toStatus(job);
    }

    /**
     * List recent jobs for a recruiter (newest first, max 20).
     */
    @Transactional(readOnly = true)
    public List<JobMatchJobSummary> getHistory(User recruiter) {
        return jobRepository.findByRecruiterOrderByCreatedAtDesc(recruiter).stream()
                .limit(20)
                .map(this::toSummary)
                .toList();
    }

    // ─────────────────────── Recovery ───────────────────────

    /**
     * On application startup, recover jobs left in QUEUED or stale RUNNING state
     * by a previous process that died (Render restart/redeploy).
     *
     * <p>QUEUED jobs are re-enqueued directly.
     * RUNNING jobs stale > {@value STALE_RUNNING_MINUTES} minutes are reset to QUEUED and re-enqueued.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStaleJobs() {
        // 1. Recover stale RUNNING jobs (previous process died)
        int recovered = jobRepository.recoverStaleJobs(
                LocalDateTime.now().minusMinutes(STALE_RUNNING_MINUTES));
        if (recovered > 0) {
            log.info("Recovered {} stale RUNNING job(s) → QUEUED", recovered);
        }

        // 2. Re-enqueue all QUEUED jobs (includes freshly recovered ones).
        //    Enqueue after COMMIT — same invariant as createAndEnqueue.
        List<JobMatchJob> queued = jobRepository.findByStatus(JobMatchJob.JobStatus.QUEUED);
        for (JobMatchJob job : queued) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            worker.executeJobMatchAsync(job.getId());
                            log.info("Re-enqueued job {} on startup", job.getId());
                        } catch (TaskRejectedException e) {
                            log.warn("Could not re-enqueue job {} on startup: queue full — leaving QUEUED", job.getId());
                        } catch (Exception e) {
                            log.error("Startup re-enqueue failed for job {} — leaving QUEUED for recovery",
                                    job.getId(), e);
                        }
                    }
                });
            } else {
                try {
                    worker.executeJobMatchAsync(job.getId());
                    log.info("Re-enqueued job {} on startup", job.getId());
                } catch (TaskRejectedException e) {
                    log.warn("Could not re-enqueue job {} on startup: queue full — leaving QUEUED", job.getId());
                }
            }
        }
    }

    // ─────────────────────── Inner Worker Bean ───────────────────────

    /**
     * Separate Spring bean so that {@code @Async} proxy-based AOP intercepts
     * the call. If the @Async method were on this same service, Spring's proxy
     * would NOT intercept it (self-invocation bypasses the proxy).
     *
     * <p>The worker deliberately does NOT run inside one big transaction:
     * every state transition (claim, per-candidate progress, completion,
     * failure) is committed in its own short transaction so pollers observe
     * {@code RUNNING} and live {@code processed}/{@code failed} counters
     * instead of a job that stays QUEUED until the whole run finishes.
     */
    @Service
    public static class JobMatchJobWorker {

        private static final Logger log = LoggerFactory.getLogger(JobMatchJobWorker.class);

        private final JobMatchJobRepository jobRepository;
        private final JobMatcherService jobMatcherService;
        private final ObjectMapper objectMapper;
        /** Null in unit tests — statements then run inline without a wrapping transaction. */
        private final TransactionTemplate transactionTemplate;

        @Autowired
        public JobMatchJobWorker(JobMatchJobRepository jobRepository,
                                  JobMatcherService jobMatcherService,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate) {
            this.jobRepository = jobRepository;
            this.jobMatcherService = jobMatcherService;
            this.objectMapper = objectMapper;
            this.transactionTemplate = transactionTemplate;
        }

        /** Convenience constructor used by unit tests (no transaction template). */
        public JobMatchJobWorker(JobMatchJobRepository jobRepository,
                                  JobMatcherService jobMatcherService,
                                  ObjectMapper objectMapper) {
            this(jobRepository, jobMatcherService, objectMapper, null);
        }

        /** Run the given statement in its own short transaction. */
        private void inTx(Runnable action) {
            if (transactionTemplate != null) {
                transactionTemplate.executeWithoutResult(status -> action.run());
            } else {
                action.run();
            }
        }

        /** Run the given query in its own short transaction and return its row count. */
        private int inTxCount(Supplier<Integer> action) {
            if (transactionTemplate != null) {
                Integer result = transactionTemplate.execute(status -> action.get());
                return result != null ? result : 0;
            }
            return action.get();
        }

        /**
         * Execute a job match asynchronously. Called via Spring proxy so @Async
         * is properly intercepted and routed to jobMatchTaskExecutor.
         *
         * <p>Execution path:
         * {@code after-commit hook → proxy → jobMatchTaskExecutor → this method}
         *
         * <p>Lifecycle persisted for pollers:
         * {@code QUEUED → RUNNING (startedAt) → per-candidate processed/failed
         * increments → COMPLETED/PARTIAL/FAILED (completedAt, result/errorMessage)}
         */
        @Async("jobMatchTaskExecutor")
        public void executeJobMatchAsync(Long jobId) {
            // 1. Generate unique execution token for this worker
            String executionToken = java.util.UUID.randomUUID().toString();

            // 2. Atomic claim: QUEUED → RUNNING (with ownership token), committed immediately
            int claimed = inTxCount(() -> jobRepository.atomicClaimJob(jobId, executionToken));
            if (claimed == 0) {
                log.debug("Job {} already claimed by another worker", jobId);
                return;
            }

            JobMatchJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.error("Job {} not found after claiming", jobId);
                return;
            }

            log.info("Starting job match {} ({} candidates, token={})", jobId, job.getTotal(),
                    executionToken.substring(0, 8));

            try {
                // 3. Deserialize inputs
                List<String> usernames = objectMapper.readValue(
                        job.getCandidatePoolJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

                // 4. Call shared matching logic (no time deadline). Each completed
                //    candidate is persisted immediately so GET /match/{jobId} shows
                //    live progress (1/4, 2/4, …) instead of 0/4 for the whole run.
                JobMatchResponse response = jobMatcherService.matchAsync(
                        job.getJdText(), usernames, job.getSource(), job.isAiEnabled(),
                        (processed, failed, total) -> {
                            try {
                                int updated = inTxCount(() ->
                                        jobRepository.updateRunningProgress(jobId, executionToken, processed, failed));
                                if (updated == 0) {
                                    log.debug("Progress update skipped for job {} (no longer RUNNING/owned)", jobId);
                                }
                            } catch (Exception pe) {
                                // Progress persistence must never fail the match itself.
                                log.debug("Progress update failed for job {}: {}", jobId, pe.getMessage());
                            }
                        });

                // 5. Calculate final status using explicit rules:
                //    - processed == total → COMPLETED
                //    - processed > 0 && processed + failed == total → PARTIAL
                //    - processed == 0 && failed == total → FAILED
                //    - processed + failed < total → incomplete (should not reach here in async)
                String resultJson = objectMapper.writeValueAsString(response);
                JobMatchJob.JobStatus finalStatus = computeFinalStatus(
                        response.processed(), response.failed(), response.total());

                // 6. Atomic completion: RUNNING → finalStatus (with execution token ownership check),
                //    committed immediately — the terminal state is visible to pollers right away.
                int updated = inTxCount(() -> jobRepository.atomicCompleteJob(
                        jobId, executionToken, finalStatus, resultJson,
                        response.processed(), response.failed(), response.total(),
                        response.jobTitle()));

                if (updated == 0) {
                    log.warn("Job {} ownership lost — another worker completed/failed it (token mismatch)", jobId);
                } else {
                    log.info("Job match {} completed: status={}, processed={}, failed={}",
                            jobId, finalStatus, response.processed(), response.failed());
                }

            } catch (Exception e) {
                log.error("Job match {} failed with exception", jobId, e);
                inTx(() -> jobRepository.atomicFailJob(jobId, executionToken,
                        "Job match processing failed: " + e.getMessage()));
            }
        }

        /**
         * Compute the final job status from candidate result counters.
         *
         * <p>Rules:
         * <ul>
         *   <li>{@code processed == total} → COMPLETED (all candidates succeeded)</li>
         *   <li>{@code processed > 0 && processed + failed == total} → PARTIAL (some succeeded, some failed)</li>
         *   <li>{@code processed == 0 && failed == total} → FAILED (zero candidates succeeded)</li>
         *   <li>Any other combination → FAILED (unexpected state, treat as failure)</li>
         * </ul>
         *
         * <p>Never marks a job COMPLETED while candidates are still pending.
         * Never marks a zero-success job COMPLETED.
         */
        static JobMatchJob.JobStatus computeFinalStatus(int processed, int failed, int total) {
            if (total <= 0) {
                return JobMatchJob.JobStatus.FAILED;
            }
            if (processed == total) {
                // All candidates succeeded
                return JobMatchJob.JobStatus.COMPLETED;
            }
            if (processed > 0 && processed + failed == total) {
                // Some succeeded, some failed, none pending
                return JobMatchJob.JobStatus.PARTIAL;
            }
            if (processed == 0 && failed == total) {
                // All candidates failed
                return JobMatchJob.JobStatus.FAILED;
            }
            // Unexpected state (e.g., processed + failed < total = still pending)
            return JobMatchJob.JobStatus.FAILED;
        }
    }

    // ─────────────────────── DTO Conversion ───────────────────────

    private JobMatchJobStatus toStatus(JobMatchJob job) {
        JobMatchResponse result = null;
        if (job.getResultJson() != null) {
            try {
                result = objectMapper.readValue(job.getResultJson(), JobMatchResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize result for job {}: {}", job.getId(), e.getMessage());
            }
        }
        return new JobMatchJobStatus(
                job.getId(),
                job.getStatus().name(),
                job.getJobTitle(),
                job.getTotal(),
                job.getProcessed(),
                job.getFailed(),
                JobMatchJobStatus.computeProgress(job.getProcessed(), job.getFailed(), job.getTotal()),
                job.isAiEnabled(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorMessage(),
                result);
    }

    private JobMatchJobSummary toSummary(JobMatchJob job) {
        return new JobMatchJobSummary(
                job.getId(),
                job.getStatus().name(),
                job.getJobTitle(),
                job.getSource(),
                job.getTotal(),
                job.getProcessed(),
                job.getFailed(),
                JobMatchJobStatus.computeProgress(job.getProcessed(), job.getFailed(), job.getTotal()),
                job.isAiEnabled(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getErrorMessage());
    }

    // ─────────────────────── Helpers ───────────────────────

    /** Run the given statement in its own short transaction (inline when no template). */
    private void inTx(Runnable action) {
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
