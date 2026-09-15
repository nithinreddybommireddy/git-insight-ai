package com.gitinsight.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strips the browser's {@code Origin} (and CORS preflight-only {@code Access-Control-Request-*})
 * headers before a request is forwarded to a downstream service.
 *
 * <p>Why: CORS is enforced once, at this gateway (see {@code CorsConfig} / the
 * {@code globalcors} YAML block). But Spring MVC's CORS filter on each downstream
 * service ALSO runs on every proxied request that still carries an {@code Origin}
 * header — and browsers attach {@code Origin} to <em>every</em> POST (and any
 * cross-origin request), not just cross-origin GETs. When the downstream
 * service's {@code app.cors.allowed-origins} is not configured (or drifts from
 * the browser origin), its CorsFilter rejects the request with
 * <em>403 "Invalid CORS request"</em> even though the gateway already allowed it.
 *
 * <p>This is exactly what broke report generation in production: GETs (profile,
 * score, history) worked because same-origin GETs carry no {@code Origin} header,
 * while the {@code POST /api/reports/generate/{username}} call failed with 403.
 *
 * <p>Once the gateway has validated the origin and added its own
 * {@code Access-Control-Allow-*} response headers, the upstream Origin header has
 * no purpose downstream — no service code reads it (verified by search). Stripping
 * it makes downstream CORS checks inert without weakening any security decision:
 * the gateway's own CORS filter still runs on the original request.
 */
@Component
public class OriginStripFilter implements GlobalFilter, Ordered {

    private static final String ORIGIN = "Origin";
    private static final String ACR_METHOD = "Access-Control-Request-Method";
    private static final String ACR_HEADERS = "Access-Control-Request-Headers";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        if (request.getHeaders().containsKey(ORIGIN)) {
            var mutated = request.mutate()
                    .headers(h -> {
                        h.remove(ORIGIN);
                        h.remove(ACR_METHOD);
                        h.remove(ACR_HEADERS);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        }
        return chain.filter(exchange);
    }

    /**
     * Run before the Netty routing filter (Ordered.LOWEST_PRECEDENCE) so the
     * stripped request — not the original — is what gets forwarded, and after
     * the gateway CORS preflight handling, which must still see the real Origin.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
