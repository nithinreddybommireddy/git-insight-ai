package com.gitinsight.gateway.config;

import com.gitinsight.gateway.exception.GatewayExceptionHandler;
import com.gitinsight.gateway.security.CookieForwardingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Gateway configuration.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link CookieForwardingFilter} — forwards auth/CSRF cookies to downstream services</li>
 *   <li>{@link GatewayExceptionHandler} — returns clean JSON errors for gateway-level failures</li>
 * </ul>
 */
@Configuration
@EnableWebFlux
public class GatewayConfig {

    @Bean
    public CookieForwardingFilter cookieForwardingFilter() {
        return new CookieForwardingFilter();
    }

    @Bean
    public GatewayExceptionHandler gatewayExceptionHandler() {
        return new GatewayExceptionHandler();
    }
}
