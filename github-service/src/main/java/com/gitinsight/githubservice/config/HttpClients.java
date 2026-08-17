package com.gitinsight.githubservice.config;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HTTP client configuration for github-service's external integrations
 * (GitHub REST API and Google Gemini).
 *
 * <p>Every upstream client must run on explicit timeouts — never the OS/JDK
 * default — so a hung third-party service cannot pin a Spring worker thread
 * indefinitely. Connect timeout is configured on the underlying
 * {@link HttpClient} (the JDK-level knob); the read timeout on the
 * {@link JdkClientHttpRequestFactory}.
 */
public final class HttpClients {

    private HttpClients() {
    }

    public static ClientHttpRequestFactory timeoutFactory(int connectSeconds, int readSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        return factory;
    }

    /** GitHub REST API client (5s connect / 30s read). */
    public static ClientHttpRequestFactory githubFactory() {
        return timeoutFactory(5, 30);
    }
}
