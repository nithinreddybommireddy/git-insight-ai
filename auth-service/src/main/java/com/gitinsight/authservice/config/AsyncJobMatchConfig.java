package com.gitinsight.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated thread pool for async Job Match processing.
 *
 * <p>Key invariants:
 * <ul>
 *   <li>{@code AbortPolicy} — when queue is full, the job is REJECTED immediately.
 *       Job Match work NEVER executes on the HTTP request thread.</li>
 *   <li>Bounded queue (capacity 5) — limits concurrent pending jobs.</li>
 *   <li>Core 2 / max 4 — appropriate for Render's limited CPU resources.</li>
 * </ul>
 */
@Configuration
public class AsyncJobMatchConfig {

    /**
     * Thread pool for async Job Match execution.
     * Bean name is "jobMatchTaskExecutor" so it can be referenced by
     * {@code @Async("jobMatchTaskExecutor")}.
     */
    @Bean("jobMatchTaskExecutor")
    public ThreadPoolTaskExecutor jobMatchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("job-match-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
