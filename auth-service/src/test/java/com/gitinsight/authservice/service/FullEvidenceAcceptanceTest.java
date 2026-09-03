package com.gitinsight.authservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.dto.response.JobMatchResponse.AiExplanation;
import com.gitinsight.authservice.dto.response.JobMatchResponse.JobMatchCandidate;
import com.gitinsight.authservice.dto.response.JobMatchResponse.SkillEvidenceView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FULL-EVIDENCE acceptance tests. These exercise the REAL pipeline end to end:
 * repository inventory → repository selection → minimum coverage → document /
 * build / config discovery → nested module discovery → source discovery →
 * source selection → evidence fetch → skill detection → matched/missing result.
 *
 * <p>github-service is stubbed over a local HTTP server; every evidence file /
 * directory read is served from canned content keyed by owner/repo/branch/path
 * (never a prebuilt corpus string injected into the matcher).
 */
@DisplayName("Full-Evidence Job Match Acceptance")
class FullEvidenceAcceptanceTest {

    static final String JD = """
            Java Full Stack Developer
            We need Java, Spring Boot, REST APIs, SQL, Git, Docker, Microservices and Redis.
            """;

    private HttpServer server;
    private FakeGitHubServer github;
    private ObjectMapper mapper;

    @BeforeEach
    void startStub() throws IOException {
        mapper = new ObjectMapper();
        github = new FakeGitHubServer(mapper);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", github);
        server.start();
    }

    @AfterEach
    void stopStub() {
        if (server != null) server.stop(0);
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private JobMatcherService plainService() {
        return new JobMatcherService("http://127.0.0.1:" + port(), "", 15,
                2000, 120_000, 20, 10, mapper);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 1 — JD skills
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. JD requiredSkills are deterministic and include every full-evidence skill")
    void jdSkillsExtracted() {
        List<String> required = plainService().extractRequiredSkills(JD);
        assertThat(required).contains("Java", "Spring Boot", "REST API", "SQL", "Git",
                "Docker", "Microservices", "Redis");
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 2 — Minimum repository coverage and NO early stopping: skills whose
    //  evidence only exists in repos 9-12 must be detected.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2. Skills found in repos 9-12 are detected — early skills never stop analysis")
    void deepAnalysisFindsLateRepoSkills() {
        Map<String, Map<String, Object>> repos = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            String name = "r" + String.format("%02d", i);
            repos.put(name, repo(name, "Java project " + name, "Java", List.of(), 30 - i));
        }
        github.reposFor("devfull", repos);

        FakeEvidenceService svc = new FakeEvidenceService(port(), mapper);

        // Repos 1-8: generic Java — only Java + Spring Boot confirmed early.
        for (int i = 1; i <= 8; i++) {
            String name = "r" + String.format("%02d", i);
            svc.file("devfull", name, "main", "README.md", "# " + name + " backend project");
            svc.file("devfull", name, "main", "pom.xml", """
                    <dependency><groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-web</artifactId></dependency>""");
        }
        // Repo 9: SQL evidence only here.
        svc.file("devfull", "r09", "main", "README.md", "# data-service");
        svc.file("devfull", "r09", "main", "application.yml",
                "spring:\n  datasource:\n    url: jdbc:postgresql://localhost:5432/app\n    driver-class-name: org.postgresql.Driver\n  jpa:\n    properties:\n      hibernate:\n        show_sql: true\n");
        // Repo 10: Redis evidence only here (realistic root-level RedisConfig.java).
        svc.file("devfull", "r10", "main", "README.md", "# user-cache-service");
        svc.file("devfull", "r10", "main", "RedisConfig.java", """
                package com.example.cache;
                import org.springframework.context.annotation.Configuration;
                @Configuration
                public class RedisConfig {
                    // RedisTemplate wiring against spring.data.redis
                }""");
        svc.dir("devfull", "r10", "main", "", List.of(file("README.md"), file("RedisConfig.java")));
        // Repo 11: Docker evidence only here.
        svc.file("devfull", "r11", "main", "README.md", "# container-infra");
        svc.file("devfull", "r11", "main", "Dockerfile",
                "# Dockerfile for the app\nFROM eclipse-temurin:21\nWORKDIR /app\nCOPY app.jar app.jar\nENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]\n");
        // Repo 12: nested monorepo — Microservices + REST API only here.
        svc.file("devfull", "r12", "main", "README.md", "# platform-modules");
        svc.dir("devfull", "r12", "main", "",
                List.of(file("README.md"), dir("api-gateway"), dir("eureka-server"), dir("auth-service")));

        svc.file("devfull", "r12", "main", "api-gateway/pom.xml",
                "<artifactId>spring-cloud-starter-gateway</artifactId>");
        svc.file("devfull", "r12", "main", "api-gateway/src/main/java/ApiGatewayApplication.java",
                "package com.example.gateway;\n@SpringBootApplication public class ApiGatewayApplication { SpringApplication.run(ApiGatewayApplication.class); }");
        svc.dir("devfull", "r12", "main", "api-gateway",
                List.of(file("pom.xml"), dir("src")));
        svc.dir("devfull", "r12", "main", "api-gateway/src", List.of(dir("main")));
        svc.dir("devfull", "r12", "main", "api-gateway/src/main", List.of(dir("java")));
        svc.dir("devfull", "r12", "main", "api-gateway/src/main/java",
                List.of(file("ApiGatewayApplication.java")));

        svc.file("devfull", "r12", "main", "eureka-server/pom.xml",
                "<artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>");
        svc.file("devfull", "r12", "main", "eureka-server/application.yml",
                "eureka:\n  client:\n    register-with-eureka: false\n    fetch-registry: false");
        svc.file("devfull", "r12", "main", "eureka-server/src/main/java/EurekaServerApplication.java",
                "package com.example.eureka;\n@EnableEurekaClient\n@SpringBootApplication public class EurekaServerApplication {}");
        svc.dir("devfull", "r12", "main", "eureka-server",
                List.of(file("pom.xml"), file("application.yml"), dir("src")));
        svc.dir("devfull", "r12", "main", "eureka-server/src", List.of(dir("main")));
        svc.dir("devfull", "r12", "main", "eureka-server/src/main", List.of(dir("java")));
        svc.dir("devfull", "r12", "main", "eureka-server/src/main/java",
                List.of(file("EurekaServerApplication.java")));

        svc.file("devfull", "r12", "main", "auth-service/pom.xml",
                "<artifactId>spring-boot-starter-web</artifactId>");
        svc.file("devfull", "r12", "main", "auth-service/src/main/java/AuthController.java",
                "package com.example.auth;\n@RestController\npublic class AuthController {\n  @GetMapping(\"/users\") String users() { return \"ok\"; }\n}");
        svc.dir("devfull", "r12", "main", "auth-service",
                List.of(file("pom.xml"), dir("src")));
        svc.dir("devfull", "r12", "main", "auth-service/src", List.of(dir("main")));
        svc.dir("devfull", "r12", "main", "auth-service/src/main", List.of(dir("java")));
        svc.dir("devfull", "r12", "main", "auth-service/src/main/java",
                List.of(file("AuthController.java")));

        JobMatchResponse resp = svc.matchAsync(JD, List.of("devfull"), "saved", false);
        assertThat(resp.failed()).as("candidate must not fail").isZero();
        assertThat(resp.results()).hasSize(1);

        JobMatchCandidate c = resp.results().get(0);
        System.out.println("DEBUG deep: touched=" + svc.reposTouched());
        System.out.println("DEBUG deep: fetched=" + svc.fetchedFiles());
        System.out.println("DEBUG deep: matched=" + c.matchedSkills());

        // FULL-EVIDENCE: early Java/Spring Boot confirmations did NOT stop the
        // analysis — skills whose evidence only exists in repos 9-12 are found.
        assertThat(c.matchedSkills())
                .as("late-repo skills must be detected")
                .contains("Java", "Spring Boot", "REST API", "SQL", "Docker",
                        "Microservices", "Redis");

        // Minimum coverage: repositories 1-12 (all available) were actually
        // inspected — at least min(10, 12) = 10 repositories fetched.
        assertThat(svc.reposTouched()).contains("r01", "r02", "r03", "r04", "r05", "r06",
                "r07", "r08", "r09", "r10", "r11", "r12");
        assertThat(svc.reposTouched().size()).isGreaterThanOrEqualTo(10);

        // Specific evidence files for the late skills were fetched.
        assertThat(svc.fetchedFiles()).contains(
                "r09/main/application.yml",
                "r10/main/RedisConfig.java",
                "r11/main/Dockerfile",
                "r12/main/api-gateway/pom.xml",
                "r12/main/eureka-server/pom.xml",
                "r12/main/auth-service/src/main/java/AuthController.java");

        // Per-skill evidence records carry the actual proving file + confidence.
        Map<String, SkillEvidenceView> evidence = new HashMap<>();
        for (SkillEvidenceView e : c.skillEvidence()) evidence.put(e.skill(), e);
        assertThat(evidence.get("SQL").file()).isEqualTo("application.yml");
        assertThat(evidence.get("Redis").file()).isEqualTo("RedisConfig.java");
        assertThat(evidence.get("Redis").confidence()).isEqualTo("HIGH");
        assertThat(evidence.get("Microservices").confidence()).isEqualTo("HIGH");
        assertThat(evidence.get("Docker").repository()).isEqualTo("r11");
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 3 — Missing skills are only declared after the evidence search is
    //  exhausted (all 12 repos analyzed, nothing found).
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3. A genuinely absent skill is declared missing only after all repositories were searched")
    void missingDeclaredOnlyAfterFullSearch() {
        Map<String, Map<String, Object>> repos = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            String name = "r" + String.format("%02d", i);
            repos.put(name, repo(name, "Java project " + name, "Java", List.of(), 30 - i));
        }
        github.reposFor("devnoredis", repos);
        FakeEvidenceService svc = new FakeEvidenceService(port(), mapper);

        for (int i = 1; i <= 12; i++) {
            String name = "r" + String.format("%02d", i);
            svc.file("devnoredis", name, "main", "README.md", "# " + name + " backend");
            svc.file("devnoredis", name, "main", "pom.xml",
                    "<artifactId>spring-boot-starter-web</artifactId>");
        }
        // NOTE: no repository contains any Redis evidence.

        JobMatchResponse resp = svc.matchAsync(JD, List.of("devnoredis"), "saved", false);
        JobMatchCandidate c = resp.results().get(0);

        assertThat(c.missingSkills()).contains("Redis");
        // Redis was searched across ALL available repositories before being
        // declared missing (no early stop after Java/Spring Boot were found).
        assertThat(svc.reposTouched()).hasSize(12);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 4 — Source file cap: 20 in async full-evidence mode (never more)
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4. Async full-evidence caps source files per repository at 20")
    void sourceFileCapInAsyncMode() {
        Map<String, Map<String, Object>> repos = new LinkedHashMap<>();
        repos.put("big", repo("big", "Big Java repo", "Java", List.of(), 5));
        github.reposFor("devbig", repos);
        FakeEvidenceService svc = new FakeEvidenceService(port(), mapper);

        svc.file("devbig", "big", "main", "README.md", "# big repo");
        List<Map<String, Object>> javaFiles = new ArrayList<>();
        javaFiles.add(file("AuthController.java"));
        javaFiles.add(file("UserController.java"));
        javaFiles.add(file("OrderController.java"));
        javaFiles.add(file("PaymentController.java"));
        javaFiles.add(file("ProductController.java"));
        javaFiles.add(file("CartController.java"));
        javaFiles.add(file("AdminController.java"));
        javaFiles.add(file("CustomerController.java"));
        javaFiles.add(file("InventoryController.java"));
        javaFiles.add(file("CatalogController.java"));
        javaFiles.add(file("CheckoutController.java"));
        javaFiles.add(file("ShipmentController.java"));
        javaFiles.add(file("ReviewController.java"));
        javaFiles.add(file("SearchController.java"));
        javaFiles.add(file("AccountController.java"));
        javaFiles.add(file("ProfileController.java"));
        javaFiles.add(file("SettingsController.java"));
        javaFiles.add(file("NotificationController.java"));
        javaFiles.add(file("ReportController.java"));
        javaFiles.add(file("DashboardController.java"));
        javaFiles.add(file("BlogController.java"));
        javaFiles.add(file("CommentController.java"));
        javaFiles.add(file("BookingController.java"));
        javaFiles.add(file("InvoiceController.java"));
        javaFiles.add(file("WalletController.java"));
        javaFiles.add(file("SubscriptionController.java"));
        javaFiles.add(file("FeedbackController.java"));
        javaFiles.add(file("AddressController.java"));
        javaFiles.add(file("WishlistController.java"));
        javaFiles.add(file("CouponController.java"));
        svc.dir("devbig", "big", "main", "", List.of(file("README.md"), dir("src")));
        svc.dir("devbig", "big", "main", "src", List.of(dir("main")));
        svc.dir("devbig", "big", "main", "src/main", List.of(dir("java")));
        svc.dir("devbig", "big", "main", "src/main/java", javaFiles);
        svc.file("devbig", "big", "main", "src/main/java/AuthController.java",
                "package com.example;\n@RestController public class AuthController {}");
        svc.file("devbig", "big", "main", "src/main/java/UserController.java",
                "package com.example;\n@RestController public class UserController {}");
        svc.file("devbig", "big", "main", "src/main/java/OrderController.java",
                "package com.example;\n@RestController public class OrderController {}");
        svc.file("devbig", "big", "main", "src/main/java/PaymentController.java",
                "package com.example;\n@RestController public class PaymentController {}");
        svc.file("devbig", "big", "main", "src/main/java/ProductController.java",
                "package com.example;\n@RestController public class ProductController {}");
        svc.file("devbig", "big", "main", "src/main/java/CartController.java",
                "package com.example;\n@RestController public class CartController {}");
        svc.file("devbig", "big", "main", "src/main/java/AdminController.java",
                "package com.example;\n@RestController public class AdminController {}");
        svc.file("devbig", "big", "main", "src/main/java/CustomerController.java",
                "package com.example;\n@RestController public class CustomerController {}");
        svc.file("devbig", "big", "main", "src/main/java/InventoryController.java",
                "package com.example;\n@RestController public class InventoryController {}");
        svc.file("devbig", "big", "main", "src/main/java/CatalogController.java",
                "package com.example;\n@RestController public class CatalogController {}");
        svc.file("devbig", "big", "main", "src/main/java/CheckoutController.java",
                "package com.example;\n@RestController public class CheckoutController {}");
        svc.file("devbig", "big", "main", "src/main/java/ShipmentController.java",
                "package com.example;\n@RestController public class ShipmentController {}");
        svc.file("devbig", "big", "main", "src/main/java/ReviewController.java",
                "package com.example;\n@RestController public class ReviewController {}");
        svc.file("devbig", "big", "main", "src/main/java/SearchController.java",
                "package com.example;\n@RestController public class SearchController {}");
        svc.file("devbig", "big", "main", "src/main/java/AccountController.java",
                "package com.example;\n@RestController public class AccountController {}");
        svc.file("devbig", "big", "main", "src/main/java/ProfileController.java",
                "package com.example;\n@RestController public class ProfileController {}");
        svc.file("devbig", "big", "main", "src/main/java/SettingsController.java",
                "package com.example;\n@RestController public class SettingsController {}");
        svc.file("devbig", "big", "main", "src/main/java/NotificationController.java",
                "package com.example;\n@RestController public class NotificationController {}");
        svc.file("devbig", "big", "main", "src/main/java/ReportController.java",
                "package com.example;\n@RestController public class ReportController {}");
        svc.file("devbig", "big", "main", "src/main/java/DashboardController.java",
                "package com.example;\n@RestController public class DashboardController {}");
        svc.file("devbig", "big", "main", "src/main/java/BlogController.java",
                "package com.example;\n@RestController public class BlogController {}");
        svc.file("devbig", "big", "main", "src/main/java/CommentController.java",
                "package com.example;\n@RestController public class CommentController {}");
        svc.file("devbig", "big", "main", "src/main/java/BookingController.java",
                "package com.example;\n@RestController public class BookingController {}");
        svc.file("devbig", "big", "main", "src/main/java/InvoiceController.java",
                "package com.example;\n@RestController public class InvoiceController {}");
        svc.file("devbig", "big", "main", "src/main/java/WalletController.java",
                "package com.example;\n@RestController public class WalletController {}");
        svc.file("devbig", "big", "main", "src/main/java/SubscriptionController.java",
                "package com.example;\n@RestController public class SubscriptionController {}");
        svc.file("devbig", "big", "main", "src/main/java/FeedbackController.java",
                "package com.example;\n@RestController public class FeedbackController {}");
        svc.file("devbig", "big", "main", "src/main/java/AddressController.java",
                "package com.example;\n@RestController public class AddressController {}");
        svc.file("devbig", "big", "main", "src/main/java/WishlistController.java",
                "package com.example;\n@RestController public class WishlistController {}");
        svc.file("devbig", "big", "main", "src/main/java/CouponController.java",
                "package com.example;\n@RestController public class CouponController {}");

        JobMatchResponse resp = svc.matchAsync(JD, List.of("devbig"), "saved", false);
        assertThat(resp.results()).hasSize(1);

        System.out.println("DEBUG cap: fetched=" + svc.fetchedFiles());
        long fetched = svc.fetchedFiles().stream()
                .filter(f -> f.startsWith("big/main/src/main/java/")).count();
        assertThat(fetched).as("source files fetched must respect the 20-file deep cap")
                .isLessThanOrEqualTo(20);
        assertThat(fetched).isGreaterThanOrEqualTo(1);
        // A Java source file was selected by realistic filename and its content
        // (@RestController) proved REST API.
        JobMatchCandidate c = resp.results().get(0);
        assertThat(c.matchedSkills()).contains("REST API");
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 5 — Candidate evidence isolation.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5. Candidate evidence is isolated across candidates sharing identical repo/file names")
    void candidateEvidenceIsolation() {
        Map<String, Map<String, Object>> reposA = new LinkedHashMap<>();
        reposA.put("shared", repo("shared", "Java repo", "Java", List.of(), 5));
        github.reposFor("devA", reposA);
        Map<String, Map<String, Object>> reposB = new LinkedHashMap<>();
        reposB.put("shared", repo("shared", "Java repo", "Java", List.of(), 5));
        github.reposFor("devB", reposB);

        FakeEvidenceService svc = new FakeEvidenceService(port(), mapper);

        // devA's "shared" repo contains RedisConfig with Redis evidence.
        svc.file("devA", "shared", "main", "README.md", "# shared repo");
        svc.file("devA", "shared", "main", "RedisConfig.java",
                "package com.example;\n@Configuration public class RedisConfig { /* redis cache */ }");
        svc.dir("devA", "shared", "main", "",
                List.of(file("README.md"), file("RedisConfig.java")));
        // devB's "shared" repo contains NO Redis at all.
        svc.file("devB", "shared", "main", "README.md", "# shared repo");
        svc.file("devB", "shared", "main", "pom.xml",
                "<artifactId>spring-boot-starter-web</artifactId>");

        JobMatchResponse resp = svc.matchAsync(JD, List.of("devA", "devB"), "saved", false);
        assertThat(resp.processed()).isEqualTo(2);

        JobMatchCandidate a = resp.results().stream()
                .filter(x -> x.username().equals("devA")).findFirst().orElseThrow();
        JobMatchCandidate b = resp.results().stream()
                .filter(x -> x.username().equals("devB")).findFirst().orElseThrow();

        assertThat(a.matchedSkills()).contains("Redis");
        assertThat(b.matchedSkills()).as("devB must not inherit devA's Redis evidence")
                .doesNotContain("Redis");
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 6 — Document/build/config evidence categories are all inspected.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. All relevant evidence categories (docs, build, config, docker, CI) are inspected")
    void allEvidenceCategoriesInspected() {
        List<String> files = JobMatcherService.evidenceFilesFor(List.of(
                "Java", "Spring Boot", "SQL", "Docker", "Kubernetes", "CI/CD",
                "React", "Python"));

        // Documentation
        assertThat(files).contains("README.md", "ARCHITECTURE.md", "DESIGN.md", "API.md");
        // Java build/dependency files
        assertThat(files).contains("pom.xml", "build.gradle", "build.gradle.kts",
                "settings.gradle", "gradle.properties");
        // Configuration files
        assertThat(files).contains("application.yml", "application.yaml",
                "application.properties", "bootstrap.yml");
        // JS + Python dependency files
        assertThat(files).contains("package.json", "requirements.txt", "pyproject.toml");
        // Docker/deployment + CI
        assertThat(files).contains("Dockerfile", "docker-compose.yml",
                "Jenkinsfile", ".gitlab-ci.yml");

        // Bounded subdirectory probes exist for CI workflows, docs, k8s/deploy.
        assertThat(JobMatcherService.EVIDENCE_PROBE_DIRS).contains(
                ".github/workflows", "docs", "k8s", "deploy", "manifests");
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 7 — Async deep budgets are separate from sync budgets.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("7. Async FULL-EVIDENCE budgets (250 req / 120s / 20 files) are separate from sync (50/15s/5)")
    void asyncBudgetsAreSeparateFromSync() {
        assertThat(JobMatcherService.REQUEST_BUDGET_PER_CANDIDATE).isEqualTo(50);
        assertThat(JobMatcherService.MAX_EVIDENCE_TIME_MS_PER_CANDIDATE).isEqualTo(15_000);
        assertThat(JobMatcherService.FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE)
                .isEqualTo(250);
        assertThat(JobMatcherService.FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS)
                .isEqualTo(120_000);
        assertThat(JobMatcherService.FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO).isEqualTo(20);
        assertThat(JobMatcherService.MIN_REPOSITORY_COVERAGE).isEqualTo(10);

        JobMatcherService.MatchContext sync = new JobMatcherService.MatchContext(
                Long.MAX_VALUE, 50, false);
        assertThat(sync.deep).isFalse();
        assertThat(sync.sourceFileCap()).isEqualTo(5);

        JobMatcherService.MatchContext deep = new JobMatcherService.MatchContext(
                Long.MAX_VALUE, 250, true);
        assertThat(deep.deep).isTrue();
        assertThat(deep.sourceFileCap()).isEqualTo(20);
        assertThat(deep.minRepositoryCoverage).isEqualTo(10);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 8 — All candidates are processed in async mode (none disappears).
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("8. Async match processes every candidate: processed + failed == total")
    void everyCandidateProcessed() {
        for (String u : List.of("cand1", "cand2", "cand3")) {
            Map<String, Map<String, Object>> repos = new LinkedHashMap<>();
            repos.put(u + "-app", repo(u + "-app", "Java repo", "Java", List.of(), 3));
            github.reposFor(u, repos);
        }
        FakeEvidenceService svc = new FakeEvidenceService(port(), mapper);
        for (String u : List.of("cand1", "cand2", "cand3")) {
            svc.file(u, u + "-app", "main", "README.md", "# app");
            svc.file(u, u + "-app", "main", "pom.xml",
                    "<artifactId>spring-boot-starter-web</artifactId>");
        }

        JobMatchResponse resp = svc.matchAsync(JD,
                List.of("cand1", "cand2", "cand3"), "saved", false);
        assertThat(resp.processed() + resp.failed()).isEqualTo(3);
        assertThat(resp.processed()).isEqualTo(3);
        assertThat(resp.results()).hasSize(3);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 9 — Gemini can never contradict deterministic matching.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("9. AI gaps/claims that contradict deterministic matchedSkills are removed (fallback)")
    void aiContradictionsRemoved() {
        JobMatchCandidate c = new JobMatchCandidate(
                "devfull", "Dev", null, null, 80, "Advanced", 90, 90,
                List.of("Java", "Spring Boot", "Docker", "REST API"),
                List.of("Redis", "Microservices"),
                List.of("Java"), List.of("r01"), List.of());

        JobMatcherService.AiExplanationView ai = new JobMatcherService.AiExplanationView(
                "devfull", 1, "Strong fit",
                "Solid Java and Spring Boot. However the candidate lacks Docker experience.",
                List.of("Java", "Spring Boot"),
                List.of("No experience with Docker", "Could grow into Kafka"),
                "Interview");

        List<AiExplanation> merged = JobMatcherService.mergeAiExplanations(
                List.of(c), java.util.Map.of("devfull", ai));

        assertThat(merged).hasSize(1);
        AiExplanation e = merged.get(0);
        // Docker is deterministically matched; the AI's "lacks Docker" claim is
        // contradictory and is replaced by the deterministic explanation.
        assertThat(e.explanation()).contains("verified from deterministic repository evidence");
        assertThat(e.gaps()).doesNotContain("No experience with Docker");
        assertThat(e.gaps()).contains("Could grow into Kafka");

        assertThat(JobMatcherService.hasMissingClaim(
                "The candidate has no Redis experience", List.of("Redis"))).isTrue();
        assertThat(JobMatcherService.hasMissingClaim(
                "Solid Redis skills", List.of("Redis"))).isFalse();
        assertThat(JobMatcherService.hasMissingClaim(
                "Missing Docker knowledge", List.of("Docker"))).isTrue();
    }

    // ════════════════════════════════════════════════════════════════════
    //  TEST 10 — Cache keys are per owner/repo/branch/path (no cross-repo bleed).
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("10. Shared file cache is keyed by owner/repo/branch/path only")
    void cacheKeysArePerRepository() {
        String a = JobMatcherService.evidenceCacheKey("devA", "shared", "main", "RedisConfig.java");
        String b = JobMatcherService.evidenceCacheKey("devB", "shared", "main", "RedisConfig.java");
        String c = JobMatcherService.evidenceCacheKey("devB", "shared", "main", "pom.xml");
        assertThat(a).isNotEqualTo(b);
        assertThat(b).isNotEqualTo(c);
        assertThat(JobMatcherService.evidenceCacheKey("devA", "shared", "main", "RedisConfig.java"))
                .isEqualTo(a);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Fixtures
    // ════════════════════════════════════════════════════════════════════

    static Map<String, Object> repo(String name, String description, String language,
                                    List<String> topics, int stars) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("language", language);
        m.put("topics", topics);
        m.put("stars", stars);
        m.put("defaultBranch", "main");
        return m;
    }

    static Map<String, Object> dir(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", "dir");
        return m;
    }

    static Map<String, Object> file(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", "file");
        return m;
    }

    /**
     * github-service stub: serves profile / score / languages / repos per user.
     */
    static final class FakeGitHubServer implements HttpHandler {
        private final ObjectMapper mapper;
        private final Map<String, List<Map<String, Object>>> reposByUser = new HashMap<>();
        private final Map<String, Integer> scoreByUser = new HashMap<>();

        FakeGitHubServer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        void reposFor(String user, Map<String, Map<String, Object>> repos) {
            reposByUser.put(user, new ArrayList<>(repos.values()));
            scoreByUser.putIfAbsent(user, 80);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            try {
                if (path.matches("/api/github/[^/]+/repos")) {
                    String user = path.split("/")[3];
                    body = mapper.writeValueAsBytes(Map.of(
                            "success", true, "message", "ok",
                            "data", reposByUser.getOrDefault(user, List.of())));
                } else if (path.matches("/api/github/profile/[^/]+")) {
                    String user = path.split("/")[4];
                    body = mapper.writeValueAsBytes(Map.of(
                            "success", true, "message", "ok",
                            "data", Map.of("username", user, "name", user,
                                    "avatarUrl", "", "bio", "Backend engineer")));
                } else if (path.matches("/api/github/[^/]+/score")) {
                    String user = path.split("/")[3];
                    body = mapper.writeValueAsBytes(Map.of(
                            "success", true, "message", "ok",
                            "data", Map.of("overallScore", scoreByUser.getOrDefault(user, 80),
                                    "level", "Advanced")));
                } else if (path.matches("/api/github/[^/]+/languages/weighted")) {
                    body = mapper.writeValueAsBytes(Map.of(
                            "success", true, "message", "ok",
                            "data", List.of(Map.of("language", "Java", "percentage", 90.0))));
                } else {
                    body = mapper.writeValueAsBytes(Map.of(
                            "success", false, "message", "not found", "data", null));
                }
            } catch (Exception e) {
                body = mapper.writeValueAsBytes(Map.of(
                        "success", false, "message", "error", "data", null));
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Real JobMatcherService with a canned evidence file-system. Every evidence
     * read goes through the REAL discovery → selection → fetch → detection
     * pipeline; only the network transport is replaced.
     */
    static final class FakeEvidenceService extends JobMatcherService {
        private final Map<String, String> files = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> dirs = new HashMap<>();
        private final Set<String> reposTouched = new LinkedHashSet<>();
        private final List<String> fetched = new ArrayList<>();

        FakeEvidenceService(int port, ObjectMapper mapper) {
            super("http://127.0.0.1:" + port, "", 15, 2000, 120_000, 20, 10, mapper);
        }

        void file(String owner, String repo, String branch, String path, String content) {
            files.put(owner + "/" + repo + "/" + branch + "/" + path, content);
        }

        void dir(String owner, String repo, String branch, String path,
                 List<Map<String, Object>> items) {
            dirs.put(owner + "/" + repo + "/" + branch + "/" + path, items);
        }

        @Override
        FileResult budgetedFetch(String owner, String repo, String branch, String file,
                                 MatchContext ctx) {
            reposTouched.add(repo);
            fetched.add(repo + "/" + branch + "/" + file);
            String content = files.get(owner + "/" + repo + "/" + branch + "/" + file);
            return content == null ? new FileResult("", "404") : new FileResult(content, null);
        }

        @Override
        List<Map<String, Object>> budgetedDirFetch(String owner, String repo, String branch,
                                                   String path, MatchContext ctx) {
            reposTouched.add(repo);
            return dirs.get(owner + "/" + repo + "/" + branch + "/" + path);
        }

        Set<String> reposTouched() {
            return reposTouched;
        }

        List<String> fetchedFiles() {
            return List.copyOf(fetched);
        }
    }
}
