package com.gitinsight.gateway.controller;

import com.gitinsight.gateway.dto.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public gateway root endpoint.
 *
 * <p>GET / answers with a lightweight status JSON without calling any
 * downstream microservice and without exposing secrets, internal URLs, JWT
 * claims, or infrastructure details.
 *
 * <p>Path design: this controller handles EXACTLY {@code /}. It does not use
 * a prefix wildcard such as {@code /**}, so existing {@code /api/**} routes and
 * {@code /actuator/**} are unaffected and are still matched by their own
 * gateway routes / global filters.
 */
@RestController
public class RootStatusController {

    private static final String SERVICE_NAME = "GitInsight AI API Gateway";
    private static final String STATUS = "UP";

    @GetMapping("/")
    public StatusResponse status() {
        return new StatusResponse(SERVICE_NAME, STATUS);
    }
}
