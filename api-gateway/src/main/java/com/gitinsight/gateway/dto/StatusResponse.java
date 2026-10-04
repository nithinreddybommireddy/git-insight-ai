package com.gitinsight.gateway.dto;

/**
 * Lightweight, public status payload returned by {@link
 * com.gitinsight.gateway.controller.RootStatusController} for {@code GET /}.
 *
 * <p>Contains only the gateway service identity and its status. No secrets,
 * no downstream service URLs, no JWT claims, no infrastructure details.
 */
public class StatusResponse {

    private final String service;
    private final String status;

    public StatusResponse(String service, String status) {
        this.service = service;
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public String getStatus() {
        return status;
    }
}
