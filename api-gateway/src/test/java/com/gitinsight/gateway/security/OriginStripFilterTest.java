package com.gitinsight.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OriginStripFilter} — the gateway-edge fix for the
 * production "Generate Report → 403 Invalid CORS request" failure.
 *
 * <p>Root cause: browsers attach {@code Origin} to every POST. The gateway's
 * CORS filter allowed the request, but the forwarded {@code Origin} header made
 * the downstream service's own Spring CORS filter re-evaluate (and reject) the
 * request when the service's {@code CORS_ALLOWED_ORIGINS} was unset or drifted.
 */
class OriginStripFilterTest {

    private final OriginStripFilter filter = new OriginStripFilter();

    /** Chain capturing the request the rest of the gateway pipeline sees. */
    private ServerWebExchange capture(MockServerHttpRequest request) {
        ServerWebExchange[] holder = new ServerWebExchange[1];
        GatewayFilterChain chain = exchange -> {
            holder[0] = exchange;
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(request), chain).block();
        return holder[0];
    }

    @Test
    void originHeaderIsStrippedFromForwardedRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("https://gateway.example.com/api/reports/generate/nithin-marla")
                .header("Origin", "https://git-insight-ai-one.vercel.app")
                .header("Content-Type", "application/json")
                .build();

        ServerWebExchange exchanged = capture(request);

        assertNull(exchanged.getRequest().getHeaders().getOrigin(),
                "Origin must be stripped before forwarding to downstream services");
        assertFalse(exchanged.getRequest().getHeaders().containsKey("Access-Control-Request-Method"));
        // Unrelated headers must survive.
        assertEquals("application/json",
                exchanged.getRequest().getHeaders().getFirst("Content-Type"));
    }

    @Test
    void preflightOnlyHeadersAreStripped() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("https://gateway.example.com/api/reports/generate/nithin-marla")
                .header("Origin", "https://git-insight-ai-one.vercel.app")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build();

        ServerWebExchange exchanged = capture(request);

        assertNull(exchanged.getRequest().getHeaders().getOrigin());
        assertFalse(exchanged.getRequest().getHeaders().containsKey("Access-Control-Request-Method"));
        assertFalse(exchanged.getRequest().getHeaders().containsKey("Access-Control-Request-Headers"));
    }

    @Test
    void requestWithoutOriginPassesThroughUntouched() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("https://gateway.example.com/api/github/torvalds/score")
                .build();

        ServerWebExchange exchanged = capture(request);

        assertNull(exchanged.getRequest().getHeaders().getOrigin());
        assertEquals(HttpStatus.OK, exchanged.getResponse().getStatusCode());
    }

    @Test
    void filterOrderRunsBeforeNettyRoutingFilter() {
        // NettyRoutingFilter (the filter that actually forwards downstream) sits
        // at LOWEST_PRECEDENCE; the strip must happen before it so the request
        // that reaches the service is the already-stripped one.
        assertTrue(filter.getOrder() < org.springframework.core.Ordered.LOWEST_PRECEDENCE,
                "Origin strip must run before the Netty routing filter forwards the request");
        assertEquals(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1, filter.getOrder());
    }
}
