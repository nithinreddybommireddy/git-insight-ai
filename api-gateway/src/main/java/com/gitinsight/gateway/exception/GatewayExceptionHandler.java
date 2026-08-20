package com.gitinsight.gateway.exception;

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
import java.util.concurrent.TimeoutException;

/**
 * Handles gateway-level errors and returns clean JSON responses.
 *
 * <p>Maps:
 * <ul>
 *   <li>Service not found (Eureka lookup failure) → 503</li>
 *   <li>Connection refused (downstream down) → 503</li>
 *   <li>Gateway response timeout → 504</li>
 *   <li>Unknown route → 504</li>
 *   <li>Unexpected failure → 500</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        int status;
        String message;

        if (ex instanceof NotFoundException) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value();
            message = "Service unavailable";
            log.warn("Service not found: {}", ex.getMessage());
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT.value();
            message = "Gateway timeout — the upstream service did not respond in time";
            log.warn("Gateway timeout: {}", ex.getMessage());
        } else if (ex instanceof ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value();
            message = "Service unavailable — downstream connection refused";
            log.warn("Connection refused: {}", ex.getMessage());
        } else if (ex instanceof PrematureCloseException) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value();
            message = "Service unavailable — connection closed prematurely";
            log.warn("Premature close: {}", ex.getMessage());
        } else if (ex instanceof ResponseStatusException rse) {
            status = rse.getStatusCode().value();
            message = rse.getReason() != null ? rse.getReason() : "Error";
        } else if (ex instanceof io.netty.channel.ConnectTimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT.value();
            message = "Gateway timeout — connection to upstream timed out";
            log.warn("Netty connect timeout: {}", ex.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "Internal gateway error";
            log.error("Gateway error: {}", ex.getMessage(), ex);
        }

        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":%d,\"message\":\"%s\"}",
                status, message.replace("\"", "\\\"")
        );

        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }
}
