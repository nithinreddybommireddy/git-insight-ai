package com.gitinsight.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Handles gateway-level errors and returns clean JSON responses.
 *
 * <p>Error mapping:
 * <ul>
 *   <li>No matching route → <b>404 Not Found</b></li>
 *   <li>Eureka service not found (no instances) → <b>503 Service Unavailable</b></li>
 *   <li>Downstream connection refused → <b>503 Service Unavailable</b></li>
 *   <li>Downstream connection closed prematurely → <b>503 Service Unavailable</b></li>
 *   <li>Gateway response timeout / connect timeout → <b>504 Gateway Timeout</b></li>
 *   <li>Other ResponseStatusException → mapped status code</li>
 *   <li>Unexpected internal failure → <b>500 Internal Server Error</b></li>
 * </ul>
 *
 * <p>Uses {@link ObjectMapper} for safe JSON serialization — no manual string
 * escaping that could produce malformed JSON with special characters.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        int status;
        String message;

        if (ex instanceof NotFoundException nfe) {
            String reason = nfe.getReason();
            if (reason != null && reason.contains("Unable to find the route")) {
                status = HttpStatus.NOT_FOUND.value();
                message = "Route not found";
                log.debug("No matching route: {}", ex.getMessage());
            } else {
                status = HttpStatus.SERVICE_UNAVAILABLE.value();
                message = "Service unavailable";
                log.warn("Service not found (no instances): {}", ex.getMessage());
            }
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT.value();
            message = "Gateway timeout";
            log.warn("Gateway timeout: {}", ex.getMessage());
        } else if (ex instanceof ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value();
            message = "Service unavailable";
            log.warn("Connection refused: {}", ex.getMessage());
        } else if (ex instanceof PrematureCloseException) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value();
            message = "Service unavailable";
            log.warn("Premature close: {}", ex.getMessage());
        } else if (ex instanceof io.netty.channel.ConnectTimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT.value();
            message = "Gateway timeout";
            log.warn("Netty connect timeout: {}", ex.getMessage());
        } else if (ex instanceof ResponseStatusException rse) {
            status = rse.getStatusCode().value();
            message = rse.getReason() != null ? rse.getReason() : "Error";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "Internal gateway error";
            log.error("Gateway error: {}", ex.getMessage(), ex);
        }

        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);

        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
        } catch (Exception e) {
            // Fallback: manually constructed but only with known-safe content
            String fallback = "{\"status\":" + status + ",\"message\":\"Error\"}";
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(fallback.getBytes(StandardCharsets.UTF_8))
            ));
        }
    }
}
