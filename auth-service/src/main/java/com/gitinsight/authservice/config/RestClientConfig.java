package com.gitinsight.authservice.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Explicit timeouts for the outbound HTTP clients (the GitHub OAuth token
 * exchange and profile fetch in {@code AuthService}). Without this, a stalled
 * upstream call can hold an authentication request open indefinitely.
 *
 * <p>Tests replace the {@code RestClient.Builder} with a stubbed
 * {@code @Primary} bean, so this customizer only affects production clients.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5_000);
            factory.setReadTimeout(20_000);
            builder.requestFactory(factory);
        };
    }
}
