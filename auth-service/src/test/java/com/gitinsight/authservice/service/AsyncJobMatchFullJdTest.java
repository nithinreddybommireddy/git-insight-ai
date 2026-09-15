package com.gitinsight.authservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.dto.response.JobMatchJobStatus;
import com.gitinsight.authservice.entity.JobMatchJob;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.JobMatchJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobMatchJobService#createAndEnqueue} JD persistence.
 *
 * <p>Regression tests for the async truncation bug: the worker runs skill
 * extraction/classification on {@code job.getJdText()}, so truncating the JD to
 * 3500 chars at enqueue time silently dropped mandatory requirements listed
 * near the end of long job descriptions.
 */
@ExtendWith(MockitoExtension.class)
class AsyncJobMatchFullJdTest {

    @Mock
    private JobMatchJobRepository jobRepository;

    @Mock
    private JobMatcherService jobMatcherService;

    @Mock
    private JobMatchJobService.JobMatchJobWorker worker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JobMatchJobService newService() {
        return new JobMatchJobService(jobRepository, jobMatcherService, worker, objectMapper);
    }

    private User recruiter() {
        User u = new User();
        u.setId(1L);
        return u;
    }

    @Test
    void fullJdIsPersistedWithoutTruncation() {
        // ~12k chars — well beyond the old 3500-char truncation.
        String filler = "About our team and culture. ".repeat(500);
        String mandatoryTail = """

                Mandatory requirements:
                - Kubernetes
                - AWS
                - Kafka
                - Redis
                """;
        String jd = filler + mandatoryTail;
        assertThat(jd.length()).isGreaterThan(3500);

        when(jobRepository.save(any(JobMatchJob.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JobMatchJobService svc = newService();
        svc.createAndEnqueue(recruiter(), jd, List.of("candidate1"), "saved", false,
                List.of("Java"));

        ArgumentCaptor<JobMatchJob> captor = ArgumentCaptor.forClass(JobMatchJob.class);
        verify(jobRepository).save(captor.capture());
        JobMatchJob saved = captor.getValue();

        assertThat(saved.getJdText()).isEqualTo(jd);
        assertThat(saved.getJdText().stripTrailing()).endsWith("- Redis");
        assertThat(saved.getJdText()).contains("Mandatory requirements");
    }

    @Test
    void enqueueRejectionStillMarksJobFailed() {
        when(jobRepository.save(any(JobMatchJob.class)))
                .thenAnswer(inv -> {
                    JobMatchJob j = inv.getArgument(0);
                    j.setId(42L); // emulate the DB-assigned ID
                    return j;
                });
        doThrow(new TaskRejectedException("queue full"))
                .when(worker).executeJobMatchAsync(42L);

        JobMatchJobService svc = newService();
        JobMatchJob job = svc.createAndEnqueue(recruiter(), "Java dev JD", List.of("c1"),
                "saved", false, List.of("Java"));

        assertThat(job.getStatus()).isEqualTo(JobMatchJob.JobStatus.FAILED);
        verify(jobRepository).markQueuedJobFailed(42L, "Job match service is at capacity. Please try again shortly.");
    }
}
