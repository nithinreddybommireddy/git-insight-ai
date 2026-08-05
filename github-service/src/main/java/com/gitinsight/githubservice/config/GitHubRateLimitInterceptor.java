package com.gitinsight.githubservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Retries GitHub API calls that hit the rate limit — HTTP 429, or 403 when
 * {@code X-RateLimit-Remaining} is 0 — instead of surfacing the error to the
 * caller. Each retry waits for the reset time GitHub reports via
 * {@code Retry-After} / {@code X-RateLimit-Reset} (capped so a request thread
 * never blocks for the full hourly reset), which lets short secondary limits
 * (roughly one minute) self-heal. After the cap it returns the final response
 * so the caller can degrade gracefully.
 */
@Component
public class GitHubRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GitHubRateLimitInterceptor.class);

    /** Total attempts including the initial request. */
    private static final int MAX_ATTEMPTS = 3;
    /** Never sleep longer than this on a single retry. */
    private static final Duration MAX_WAIT = Duration.ofSeconds(60);
    /** Fallback wait when GitHub provides no timing info. */
    private static final Duration FALLBACK_WAIT = Duration.ofSeconds(10);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        for (int attempt = 1; ; attempt++) {
            ClientHttpResponse response = execution.execute(request, body);
            if (!isRateLimited(response) || attempt >= MAX_ATTEMPTS) {
                return response;
            }
            Duration wait = waitDuration(response.getHeaders());
            log.warn("GitHub rate limited on {} (attempt {}/{}), retrying in {}s",
                    request.getURI(), attempt, MAX_ATTEMPTS, wait.toSeconds());
            sleep(wait);
        }
    }

    private boolean isRateLimited(ClientHttpResponse response) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return true;
        }
        // GitHub sometimes reports rate limits as 403 with X-RateLimit-Remaining: 0
        if (status == HttpStatus.FORBIDDEN) {
            String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
            return remaining != null && remaining.trim().equals("0");
        }
        return false;
    }

    private Duration waitDuration(HttpHeaders headers) {
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return capped(Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // fall through to X-RateLimit-Reset
            }
        }
        String reset = headers.getFirst("X-RateLimit-Reset");
        if (reset != null) {
            try {
                long resetEpoch = Long.parseLong(reset.trim());
                long seconds = resetEpoch - Instant.now().getEpochSecond();
                return capped(Math.max(seconds, 1));
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return FALLBACK_WAIT;
    }

    private Duration capped(long seconds) {
        Duration wait = Duration.ofSeconds(seconds);
        return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
    }

    private void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
