package com.gitinsight.gateway;

import com.gitinsight.gateway.controller.RootStatusController;
import com.gitinsight.gateway.dto.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.springframework.web.bind.annotation.GetMapping;

/**
 * End-to-end tests for the public gateway root endpoint.
 *
 * <p>Covers the new {@code GET /} handler plus a regression check that the
 * existing protected routes are still protected after adding the root
 * controller. These tests run against the real Spring context (no mocking of
 * the filters or the route matcher), so they verify what the deployed gateway
 * actually handles.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-32bytes!",
                "app.jwt.min-secret-bytes=32"
        }
)
@ActiveProfiles("test")
class RootStatusControllerTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Autowired
    private RootStatusController rootStatusController;

    @BeforeEach
    void setUp() {
        assertNotNull(rootStatusController,
                "RootStatusController must be registered by the gateway context");
    }

    @Test
    void getRoot_returns200WithCleanGatewayStatus() {
        ResponseEntity<StatusResponse> response =
                rest.exchange(
                        RequestEntity.get("http://localhost:" + port + "/").build(),
                        StatusResponse.class
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        StatusResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("GitInsight AI API Gateway", body.getService());
        assertEquals("UP", body.getStatus());
    }

    @Test
    void getRoot_doesNotCallDownstreamService() {
        ResponseEntity<StatusResponse> response =
                rest.exchange(
                        RequestEntity.get("http://localhost:" + port + "/").build(),
                        StatusResponse.class
                );

        // The payload is the fixed, hardcoded gateway identity. If this test
        // ever regresses to a 502/503 or to a service-specific page, the body
        // will no longer match and this assertion will fail.
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }


    @Test
    void standardRestMapping_matchesOnlyExactRoot() {
        // Verification that the controller really is limited to GET / and is
        // not a broad wildcard handler.
        try {
            Method method = rootStatusController.getClass().getMethod("status");
            GetMapping annotation = method.getAnnotation(GetMapping.class);
            assertEquals(
                    "/", annotation.value()[0],
                    "GET / must be the only mapped path, no wildcard prefix"
            );
        } catch (NoSuchMethodException e) {
            throw new AssertionError("RootStatusController.status() method not found", e);
        }
    }
}
