package com.gitinsight.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Gateway's reactive application context loads successfully.
 *
 * <p>This is critical because common/pom.xml previously included spring-webmvc
 * which could silently inject the servlet/MVC stack into the reactive Gateway,
 * causing startup failures in production while unit tests still passed.
 *
 * <p>The test profile disables Eureka registration and provides a test JWT secret
 * so the context loads without external infrastructure.
 */
@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-32bytes!",
                "app.jwt.min-secret-bytes=32"
        }
)
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // If the application context fails to load (e.g. servlet/MVC conflict),
        // this test fails. That is exactly what we want to detect.
    }
}
