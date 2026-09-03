package com.gitinsight.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards for the bounded source-file pattern matcher (FINAL SOURCE FILE
 * DISCOVERY FIX). Real repositories contain files such as AuthController.java,
 * OrderController.java, ApiGatewayApplication.java, RedisConfig.java and
 * UserService.java — the old exact-name-only filter (Controller.java,
 * Service.java, ...) selected none of them, which left
 * {@code sourceRootsDiscovered > 0} but {@code sourceFilesFetched = 0}.
 *
 * <p>These tests pin down that:
 * <ol>
 *   <li>real-world high-signal names are selected by suffix pattern,</li>
 *   <li>legacy exact names still match (nothing regresses),</li>
 *   <li>arbitrary {@code *.java} files are never selected (bounded),</li>
 *   <li>higher-signal files (controllers/applications) sort before lower-signal
 *       files (configs/services) within the 5-file budget, and</li>
 *   <li>the file NAME only selects evidence — the CONTENT still has to prove
 *       the skill (no generic *Service.java → Microservices).</li>
 * </ol>
 */
class SourceFileDiscoveryTest {

    // ── 1. Real-world names are now selected via bounded suffix patterns ──

    @Test
    void realWorldHighSignalNamesAreRecognized() {
        // The exact names that previously existed (Controller.java, Service.java, ...)
        // never occur in real repositories — the suffixed versions do.
        assertThat(JobMatcherService.isHighSignalSourceFile("AuthController.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("OrderController.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("UserRestController.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("PaymentResource.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("ApiGatewayApplication.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("UserServiceApplication.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("FixmateApplication.kt")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("RedisConfig.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("SecurityConfiguration.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("PaymentClient.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("OrderFeignClient.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("UserService.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("OrderServiceImpl.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("UserRepository.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("OrderMapper.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("RouteGateway.java")).isTrue();
    }

    @Test
    void fullPathsAreRecognizedThroughTheirFileName() {
        // Discovery accumulates full paths ("src/main/java/.../X.java"); the
        // suffix check is on the trailing file name, so paths must match too.
        assertThat(JobMatcherService.isHighSignalSourceFile(
                "api-gateway/src/main/java/com/example/gateway/ApiGatewayApplication.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile(
                "fixmate-backend/src/main/java/com/fixmate/order/OrderController.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile(
                "src/main/java/com/gitinsight/security/SecurityConfig.java")).isTrue();
    }

    // ── 2. Legacy exact names still match (no regression) ──

    @Test
    void legacyExactNamesStillMatch() {
        assertThat(JobMatcherService.isHighSignalSourceFile("Controller.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("RestController.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Service.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("ServiceImpl.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Config.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Configuration.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Repository.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Mapper.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("GatewayConfig.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("GatewayRouteConfig.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("FeignClient.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Application.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Application.kt")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Main.java")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("App.java")).isTrue();
        // Root-level non-Java entry / Docker files
        assertThat(JobMatcherService.isHighSignalSourceFile("index.js")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("index.ts")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("App.jsx")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("App.tsx")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("app.py")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("main.py")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("Dockerfile")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("docker-compose.yml")).isTrue();
        assertThat(JobMatcherService.isHighSignalSourceFile("docker-compose.yaml")).isTrue();
    }

    // ── 3. No arbitrary *.java crawl / no false positives ──

    @Test
    void arbitraryJavaFilesAreNotSelected() {
        assertThat(JobMatcherService.isHighSignalSourceFile("User.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("Order.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("Payment.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("Util.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("StringUtils.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("OrderControllerTest.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("ServiceNowConnector.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("package-info.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("data.sql")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("build.gradle")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile(null)).isFalse();
    }

    @Test
    void serviceSuffixDoesNotMatchEverythingEndingInJava() {
        // "ServiceNow..." or "...Consumer" files must not be treated as service classes.
        assertThat(JobMatcherService.isHighSignalSourceFile("ServiceNowClient.java")).isTrue(); // *Client.java suffix
        assertThat(JobMatcherService.isHighSignalSourceFile("NotificationConsumer.java")).isFalse();
        assertThat(JobMatcherService.isHighSignalSourceFile("JobScheduler.java")).isFalse();
    }

    // ── 4. Priority: highest-signal files are fetched first within the 5-file cap ──

    @Test
    void controllersAndApplicationsOutrankServicesAndConfigs() {
        assertThat(JobMatcherService.sourceFilePriority("AuthController.java"))
                .isLessThan(JobMatcherService.sourceFilePriority("UserService.java"));
        assertThat(JobMatcherService.sourceFilePriority("OrderRestController.java"))
                .isLessThan(JobMatcherService.sourceFilePriority("ApiGatewayApplication.java"))
                .isLessThan(JobMatcherService.sourceFilePriority("RedisConfig.java"))
                .isLessThan(JobMatcherService.sourceFilePriority("UserService.java"));
        assertThat(JobMatcherService.sourceFilePriority("PaymentClient.java"))
                .isLessThan(JobMatcherService.sourceFilePriority("UserService.java"));
    }

    @Test
    void restControllersShareTheControllerTier() {
        assertThat(JobMatcherService.sourceFilePriority("OrderRestController.java"))
                .isEqualTo(JobMatcherService.sourceFilePriority("AuthController.java"));
    }

    @Test
    void legacyExactNamesSortBelowEverySuffixMatch() {
        int legacyRank = JobMatcherService.sourceFilePriority("index.js");
        assertThat(legacyRank).isEqualTo(JobMatcherService.sourceFilePriority("Dockerfile"));
        assertThat(JobMatcherService.sourceFilePriority("Controller.java"))
                .isLessThan(legacyRank);
        assertThat(JobMatcherService.sourceFilePriority("Service.java"))
                .isLessThan(legacyRank);
        assertThat(JobMatcherService.sourceFilePriority("User.java"))
                .isGreaterThan(legacyRank); // non-candidates sort last
    }

    @Test
    void firstFiveFetchedAreTheHighestSignalFiles() {
        // More than MAX_SOURCE_FILES_PER_REPO (5) candidates discovered in one
        // repository — the first 5 by priority must be the high-signal ones.
        List<String> discovered = List.of(
                "UserService.java",
                "AuthController.java",
                "OrderServiceImpl.java",
                "SecurityConfig.java",
                "ApiGatewayApplication.java",
                "PaymentClient.java",
                "ProductResource.java",
                "Dockerfile",
                "UserRepository.java",
                "OrderController.java");

        List<String> sorted = discovered.stream()
                .sorted(Comparator.comparingInt(JobMatcherService::sourceFilePriority))
                .toList();
        List<String> firstFive = sorted.subList(0, 5);

        assertThat(firstFive).containsExactly(
                "AuthController.java",
                "OrderController.java",
                "ProductResource.java",
                "ApiGatewayApplication.java",
                "PaymentClient.java");
    }

    // ── 5. File NAME selects evidence; CONTENT still proves the skill ──

    @Test
    void controllerFileNameIsNotEnoughForRestApiEvidence() {
        // A class named AuthController.java without any REST annotations must NOT
        // confirm REST API — content is the evidence.
        String plainJava = "package com.example;\npublic class AuthController {\n    public String hello() { return \"hi\"; }\n}\n";
        assertThat(JobMatcherService.sourcePatternMatches(plainJava, "AuthController.java", "REST API")).isFalse();

        String restController = "package com.example;\nimport org.springframework.web.bind.annotation.*;\n"
                + "@RestController\n@RequestMapping(\"/api/orders\")\npublic class AuthController {}\n";
        assertThat(JobMatcherService.sourcePatternMatches(restController, "AuthController.java", "REST API")).isTrue();
    }

    @Test
    void genericServiceFileIsNotMicroservicesEvidence() {
        String genericService = "package com.example;\n@Service\npublic class UserService {\n    public void doWork() {}\n}\n";
        // Selection is allowed (the file may be fetched)…
        assertThat(JobMatcherService.isHighSignalSourceFile("UserService.java")).isTrue();
        // …but the content alone must not be treated as Microservices evidence.
        assertThat(JobMatcherService.sourcePatternMatches(genericService, "UserService.java", "Microservices")).isFalse();

        String discoveryClient = "package com.example;\n@EnableDiscoveryClient\npublic class UserServiceApplication {}\n";
        assertThat(JobMatcherService.sourcePatternMatches(discoveryClient, "UserServiceApplication.java", "Microservices")).isTrue();
    }

    @Test
    void applicationFileContentConfirmsSpringBoot() {
        String bootApp = "package com.example;\n@SpringBootApplication\npublic class ApiGatewayApplication {\n"
                + "    public static void main(String[] args) { SpringApplication.run(ApiGatewayApplication.class, args); }\n}\n";
        assertThat(JobMatcherService.isHighSignalSourceFile("ApiGatewayApplication.java")).isTrue();
        assertThat(JobMatcherService.sourcePatternMatches(bootApp, "ApiGatewayApplication.java", "Spring Boot")).isTrue();
    }

    @Test
    void dockerIsNotInferredFromJavaKeywordsAlone() {
        // FROM/COPY inside a .java file must not be classified as Docker — the
        // class name must not smuggle in the word "docker" either.
        String javaWithDockerishLines = "package com.example;\n"
                + "public class CopyHelper { String from = \"x\"; String copy = \"y\"; }\n";
        assertThat(JobMatcherService.sourcePatternMatches(javaWithDockerishLines, "CopyHelper.java", "Docker")).isFalse();

        // A Dockerfile (root-level legacy exact name, matched by content-free filename rule) is Docker evidence.
        assertThat(JobMatcherService.isHighSignalSourceFile("Dockerfile")).isTrue();
        assertThat(JobMatcherService.sourcePatternMatches("FROM openjdk:21", "Dockerfile", "Docker")).isTrue();
    }
}
