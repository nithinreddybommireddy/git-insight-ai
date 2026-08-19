package com.gitinsight.gateway.config;

import com.gitinsight.gateway.exception.GatewayExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Gateway configuration.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link GatewayExceptionHandler} — returns clean JSON errors for gateway-level failures</li>
 * </ul>
 *
 * <p>{@link com.gitinsight.gateway.security.CookieForwardingFilter} is registered via
 * {@code @Component} — do not create a duplicate bean here.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public GatewayExceptionHandler gatewayExceptionHandler() {
        return new GatewayExceptionHandler();
    }
}
