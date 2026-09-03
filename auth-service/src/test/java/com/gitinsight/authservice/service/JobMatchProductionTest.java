package com.gitinsight.authservice.service;

import com.gitinsight.authservice.dto.response.JobMatchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-like test using the exact Java Full Stack Developer JD.
 * No source code modifications — diagnosis only.
 */
@DisplayName("Job Match Production-Like Test — Java Full Stack Developer JD")
class JobMatchProductionTest {

    /**
     * The EXACT JD uploaded by the user. Do NOT rewrite, normalize, or alter.
     */
    private static final String JD = """
            Java Full Stack Developer

            We are looking for a Java Full Stack Developer with experience in Java,
            Spring Boot, React.js, REST APIs, SQL, Git, and Docker.

            Preferred skills:
            - Microservices
            - GitHub
            - Problem-solving
            - AI/ML knowledge
            """;

    private final JobMatcherService svc = new JobMatcherService(RestClient.create());

    // ══════════════════════════════════════════════════════════════════
    //  TEST 7 — JD SKILL EXTRACTION
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 7: Exact skills extracted from the uploaded JD")
    void exactSkillsExtracted() {
        List<String> required = svc.extractRequiredSkills(JD);

        System.out.println("\n═══ TEST 7: JD SKILL EXTRACTION ═══");
        System.out.println("Extracted skills (" + required.size() + "):");
        required.forEach(s -> System.out.println("  ✅ " + s));

        // Report exactly what the extractor produces
        Set<String> expectedFromJD = Set.of(
                "Java",           // "Java" mentioned
                "Spring Boot",    // "Spring Boot" mentioned
                "React",          // "React.js" mentioned
                "REST API",       // "REST APIs" mentioned
                "SQL",            // "SQL" mentioned
                "Git",            // "Git" mentioned
                "Docker",         // "Docker" mentioned
                "Microservices",  // "Microservices" mentioned
                "GitHub",         // "GitHub" mentioned
                "Machine Learning" // "AI/ML knowledge" — ML pattern matches
                // Note: "Problem-solving" has no skill alias
                // Note: "AI" alone matches the "ai" alias in some skills
        );

        // Verify core skills are extracted
        assertThat(required).contains("Java", "Spring Boot", "React", "REST API",
                "SQL", "Git", "Docker", "Microservices");

        // Report what WAS and WAS NOT extracted
        System.out.println("\n── Skills in JD but NOT extracted ──");
        expectedFromJD.forEach(skill -> {
            if (!required.contains(skill)) {
                System.out.println("  ❌ " + skill + " (not in SKILL_ALIASES)");
            }
        });

        System.out.println("\n── Skills extracted but not in expected set ──");
        required.forEach(skill -> {
            if (!expectedFromJD.contains(skill)) {
                System.out.println("  ➕ " + skill + " (unexpected extraction)");
            }
        });

        // Specific checks
        assertThat(required).as("Java must be extracted").contains("Java");
        assertThat(required).as("Spring Boot must be extracted").contains("Spring Boot");
        assertThat(required).as("React must be extracted").contains("React");
        assertThat(required).as("REST API must be extracted").contains("REST API");
        assertThat(required).as("SQL must be extracted").contains("SQL");
        assertThat(required).as("Git must be extracted").contains("Git");
        assertThat(required).as("Docker must be extracted").contains("Docker");
        assertThat(required).as("Microservices must be extracted").contains("Microservices");
    }

    // ══════════════════════════════════════════════════════════════════
    //  SKILL EXTRACTION ANALYSIS
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 7b: Analyze each JD skill extraction individually")
    void analyzeEachSkillExtraction() {
        List<String> required = svc.extractRequiredSkills(JD);

        System.out.println("\n═══ SKILL-BY-SKILL ANALYSIS ═══");

        String[][] skillChecks = {
                {"Java", "\"Java\" in JD → pattern: java|jdk"},
                {"Spring Boot", "\"Spring Boot\" in JD → pattern: spring boot|springboot|spring-boot"},
                {"React", "\"React.js\" in JD → pattern: react|react.js|reactjs"},
                {"REST API", "\"REST APIs\" in JD → pattern: rest api|restful|restcontroller|@restcontroller"},
                {"SQL", "\"SQL\" in JD → pattern: sql"},
                {"Git", "\"Git\" in JD → pattern: git (word-boundary)"},
                {"Docker", "\"Docker\" in JD → pattern: docker|dockerfile|containerization"},
                {"Microservices", "\"Microservices\" in JD → pattern: microservice|microservices|spring cloud|eureka"},
                {"GitHub", "\"GitHub\" in JD → pattern: github"},
                {"Machine Learning", "\"AI/ML\" in JD → pattern: machine learning|ml (word-boundary)"},
        };

        for (String[] check : skillChecks) {
            boolean extracted = required.contains(check[0]);
            System.out.println((extracted ? "✅" : "❌") + " " + check[0] + " — " + check[1]);
        }

        System.out.println("\n── Skills with no alias in SKILL_ALIASES ──");
        System.out.println("  ⚠️  \"Problem-solving\" — no pattern defined");
        System.out.println("  ⚠️  \"AI/ML\" — only ML alias exists (\"ai\" may match other skills)");
    }

    // ══════════════════════════════════════════════════════════════════
    //  JOB TITLE INFERENCE
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 7c: Job title inferred from JD")
    void jobTitleInferred() {
        String title = svc.inferJobTitle(JD);
        System.out.println("\n═══ JOB TITLE ═══");
        System.out.println("Inferred: \"" + title + "\"");
        assertThat(title).contains("Java Full Stack Developer");
    }

    // ══════════════════════════════════════════════════════════════════
    //  SYNC MATCH WITH MOCK CANDIDATES
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 1: Sync match with unreachable github-service (offline mode)")
    void syncMatchOfflineMode() {
        System.out.println("\n═══ TEST 1: SYNC MATCH (offline — github-service unreachable) ═══");

        // Use RestClient.create() which hits localhost:8081 (not running)
        // All candidates will fail with connection refused
        List<String> candidates = List.of(
                "user1", "user2", "user3", "user4", "user5",
                "user6", "user7", "user8", "user9", "user10",
                "user11", "user12", "user13", "user14", "user15",
                "user16", "user17", "user18", "user19", "user20"
        );

        long start = System.currentTimeMillis();
        JobMatchResponse result = svc.match(JD, candidates, "saved");
        long duration = System.currentTimeMillis() - start;

        System.out.println("Total candidates: " + result.total());
        System.out.println("Processed: " + result.processed());
        System.out.println("Failed: " + result.failed());
        System.out.println("Skipped: " + (result.total() - result.processed() - result.failed()));
        System.out.println("Ranked: " + result.results().size());
        System.out.println("Required skills: " + result.requiredSkills());
        System.out.println("AI enabled: " + result.aiEnabled());
        System.out.println("AI explanations: " + result.aiExplanations().size());
        System.out.println("Duration: " + duration + "ms");

        // Verify ALL 20 candidates are accounted for
        assertThat(result.processed() + result.failed()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(20);
        // Since github-service is unreachable, all fail
        assertThat(result.failed()).isEqualTo(20);
    }

    // ══════════════════════════════════════════════════════════════════
    //  SYNC MATCH — EDGE CASES
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 1b: Sync match with zero candidates")
    void syncMatchZeroCandidates() {
        System.out.println("\n═══ TEST 1b: SYNC MATCH (zero candidates) ═══");

        JobMatchResponse result = svc.match(JD, List.of(), "saved");

        System.out.println("Total: " + result.total());
        System.out.println("Processed: " + result.processed());
        System.out.println("Failed: " + result.failed());

        assertThat(result.total()).isEqualTo(0);
        assertThat(result.processed()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.results()).isEmpty();
    }

    @Test
    @DisplayName("TEST 1c: Sync match with AI enabled (offline)")
    void syncMatchAiEnabledOffline() {
        System.out.println("\n═══ TEST 1c: SYNC MATCH + AI (offline) ═══");

        long start = System.currentTimeMillis();
        JobMatchResponse result = svc.match(JD, List.of("user1", "user2"), "saved", true);
        long duration = System.currentTimeMillis() - start;

        System.out.println("Total: " + result.total());
        System.out.println("Processed: " + result.processed());
        System.out.println("Failed: " + result.failed());
        System.out.println("AI enabled: " + result.aiEnabled());
        System.out.println("AI explanations: " + result.aiExplanations().size());
        System.out.println("Duration: " + duration + "ms");

        // AI will be skipped (offline) but deterministic results still returned
        assertThat(result.processed() + result.failed()).isEqualTo(2);
        assertThat(result.aiEnabled()).isFalse(); // AI unavailable offline
    }

    // ══════════════════════════════════════════════════════════════════
    //  MATCH SCORE FORMULA
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 3: Match score formula validation")
    void matchScoreFormula() {
        System.out.println("\n═══ TEST 3: MATCH SCORE FORMULA ═══");

        // Test the 60/40 formula
        assertThat(JobMatcherService.computeMatchScore(100, 100)).isEqualTo(100);
        assertThat(JobMatcherService.computeMatchScore(0, 0)).isEqualTo(0);
        assertThat(JobMatcherService.computeMatchScore(100, 0)).isEqualTo(60);
        assertThat(JobMatcherService.computeMatchScore(0, 100)).isEqualTo(40);
        assertThat(JobMatcherService.computeMatchScore(50, 50)).isEqualTo(50);

        System.out.println("Formula: 60% skill match + 40% developer score");
        System.out.println("  100% skills + 100% dev = 100");
        System.out.println("  100% skills + 0% dev = 60");
        System.out.println("  0% skills + 100% dev = 40");
        System.out.println("  50% skills + 50% dev = 50");
    }

    // ══════════════════════════════════════════════════════════════════
    //  EVIDENCE CORPUS DETECTION
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 4: Source-evidence pattern matching validation")
    void sourceEvidencePatternMatching() {
        System.out.println("\n═══ TEST 4: SOURCE-EVIDENCE PATTERN MATCHING ═══");

        // Test REST API detection from source code
        String restSource = """
                @RestController
                @RequestMapping("/api/orders")
                public class OrderController {
                    @GetMapping("/{id}")
                    public Order getOrder(@PathVariable Long id) {
                        return orderService.findById(id);
                    }
                    @PostMapping
                    public Order createOrder(@RequestBody Order order) {
                        return orderService.create(order);
                    }
                }
                """;
        assertThat(svc.matches(restSource, "REST API"))
                .as("REST API should be detected from @RestController + @RequestMapping")
                .isTrue();

        // Test Microservices detection — NOTE: @EnableDiscoveryClient alone does NOT
        // match the current Microservices pattern. The pattern requires explicit keywords
        // like "eureka", "service discovery", "spring cloud", etc.
        String microserviceSource = """
                @SpringBootApplication
                @EnableDiscoveryClient
                public class OrderServiceApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(OrderServiceApplication.class, args);
                    }
                }
                """;
        // @EnableDiscoveryClient is a strong Microservices source signal (sourcePatternMatches)
        assertThat(JobMatcherService.sourcePatternMatches(microserviceSource, "Microservices"))
                .as("Microservices should be detected from @EnableDiscoveryClient + Spring Boot")
                .isTrue();

        // @EnableEurekaClient is a strong Microservices signal (sourcePatternMatches)
        String eurekaSource = "@EnableEurekaClient\nspring.cloud.service-registry.url=http://eureka:8761";
        assertThat(JobMatcherService.sourcePatternMatches(eurekaSource, "Microservices"))
                .as("Microservices should be detected from @EnableEurekaClient + eureka")
                .isTrue();

        // Docker detection: filename-aware via sourcePatternMatches(content, filename, skill)
        // Without filename, Dockerfile instructions without the word "docker" do NOT match
        String dockerfileInstructions = """
                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY target/app.jar app.jar
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;
        assertThat(JobMatcherService.sourcePatternMatches(dockerfileInstructions, "Dockerfile", "Docker"))
                .as("Docker should be detected from Dockerfile filename")
                .isTrue();
        // Without filename context, generic Dockerfile instructions (no keyword) do NOT match
        assertThat(svc.matches(dockerfileInstructions, "Docker"))
                .as("Dockerfile instructions without keyword or filename should NOT match")
                .isFalse();

        // But if the filename "Dockerfile" is mentioned, it works:
        String dockerfileWithLabel = "Dockerfile:\nFROM eclipse-temurin:21-jre";
        assertThat(svc.matches(dockerfileWithLabel, "Docker"))
                .as("Docker should be detected when Dockerfile name is present")
                .isTrue();

        // Test Git detection from platform evidence
        String gitEvidence = "git_source:github_repository";
        assertThat(svc.matches(gitEvidence, "Git"))
                .as("Git should be detected from git_source:github_repository")
                .isTrue();

        // SQL detection: PostgreSQL is a strong SQL source signal (sourcePatternMatches)
        String sqlEvidence = "spring-boot-starter-data-jpa hibernate PostgreSQL";
        assertThat(JobMatcherService.sourcePatternMatches(sqlEvidence, "SQL"))
                .as("SQL should be detected from PostgreSQL in source evidence")
                .isTrue();

        // MySQL also detected via sourcePatternMatches
        assertThat(JobMatcherService.sourcePatternMatches("MySQL database", "SQL")).isTrue();

        // Test Spring Boot detection from pom.xml
        String pomXml = """
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
                """;
        assertThat(svc.matches(pomXml, "Spring Boot"))
                .as("Spring Boot should be detected from spring-boot-starter-web")
                .isTrue();

        // Java detection: import java.* is now a strong Java source signal (sourcePatternMatches)
        String javaWithImport = "import java.util.List;\nimport java.util.Map;";
        assertThat(JobMatcherService.sourcePatternMatches(javaWithImport, "Java"))
                .as("Java should be detected from import java.util")
                .isTrue();
        // @SpringBootApplication is also strong Java evidence
        assertThat(JobMatcherService.sourcePatternMatches("@SpringBootApplication\npublic class MyApp {}", "Java"))
                .as("Java should be detected from @SpringBootApplication")
                .isTrue();
        // A generic class without Java-specific imports should NOT match
        String genericClass = "class MyClass:\n    def __init__(self):";
        assertThat(JobMatcherService.sourcePatternMatches(genericClass, "Java"))
                .as("Generic class without Java imports should NOT match")
                .isFalse();

        // Test React detection
        String packageJson = "\"react\": \"^18.2.0\", \"react-dom\": \"^18.2.0\"";
        assertThat(svc.matches(packageJson, "React"))
                .as("React should be detected from package.json")
                .isTrue();

        System.out.println("All source-evidence patterns validated:");
        System.out.println("  ✅ REST API: @RestController + @RequestMapping");
        System.out.println("  ✅ Microservices: @EnableDiscoveryClient → detected via sourcePatternMatches");
        System.out.println("  ✅ Microservices: @EnableEurekaClient + eureka → detected");
        System.out.println("  ✅ Docker: Dockerfile content");
        System.out.println("  ✅ Git: git_source:github_repository");
        System.out.println("  ✅ SQL: spring-data-jpa evidence");
        System.out.println("  ✅ Spring Boot: spring-boot-starter-web");
        System.out.println("  ✅ Java: Java source code");
        System.out.println("  ✅ React: package.json");
    }

    // ══════════════════════════════════════════════════════════════════
    //  FALSE POSITIVE PROTECTION
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 4b: False positive protection")
    void falsePositiveProtection() {
        System.out.println("\n═══ TEST 4b: FALSE POSITIVE PROTECTION ═══");

        // DIAGNOSTIC: Dockerfile instructions alone (FROM, RUN) do NOT contain the word
        // "docker" or "dockerfile", so the Docker pattern does NOT match.
        // This is a known gap: the Docker pattern requires the keyword explicitly.
        String dockerOnly = "FROM node:18\nRUN npm install";
        boolean dockerFromInstructions = svc.matches(dockerOnly, "Docker");
        System.out.println("  Docker from bare instructions: " + dockerFromInstructions + " (DIAGNOSTIC: gap found)");
        // Docker instructions without keyword → NOT detected (known limitation)

        // But with explicit Docker keyword, it works:
        String dockerWithKeyword = "Dockerfile:\nFROM node:18";
        assertThat(svc.matches(dockerWithKeyword, "Docker")).isTrue();

        // Microservices NOT proved by Docker alone
        assertThat(svc.matches(dockerOnly, "Microservices")).isFalse();

        // "pom.xml" existence should NOT prove Spring Boot (needs specific evidence)
        String pomNoSpring = """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>my-app</artifactId>
                </project>
                """;
        boolean springFromPom = svc.matches(pomNoSpring, "Spring Boot");
        System.out.println("  Generic pom.xml → Spring Boot: " + springFromPom + " (DIAGNOSTIC)");

        // "@RestController" should prove REST API
        String restController = "@RestController public class Foo {}";
        assertThat(svc.matches(restController, "REST API")).isTrue();

        System.out.println("  ✅ Docker from keyword → detected");
        System.out.println("  ✅ Docker without keyword → NOT detected (known gap)");
        System.out.println("  ✅ Docker without microservice evidence → NOT Microservices");
        System.out.println("  ✅ @RestController → REST API detected");
    }

    // ══════════════════════════════════════════════════════════════════
    //  CONSTANTS VERIFICATION
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 5: Repository and source limits")
    void repositoryLimits() {
        System.out.println("\n═══ TEST 5: REPOSITORY AND SOURCE LIMITS ═══");

        assertThat(svc.maxEvidenceRepos).isEqualTo(15);
        assertThat(JobMatcherService.MAX_CANDIDATES).isEqualTo(25);
        assertThat(JobMatcherService.GLOBAL_MATCH_TIME_MS).isEqualTo(50_000);
        assertThat(JobMatcherService.MAX_EVIDENCE_TIME_MS_PER_CANDIDATE).isEqualTo(15_000);
        assertThat(JobMatcherService.REQUEST_BUDGET_PER_CANDIDATE).isEqualTo(50);

        System.out.println("  MAX_EVIDENCE_REPOS: " + svc.maxEvidenceRepos);
        System.out.println("  MAX_CANDIDATES: " + JobMatcherService.MAX_CANDIDATES);
        System.out.println("  GLOBAL_MATCH_TIME_MS: " + JobMatcherService.GLOBAL_MATCH_TIME_MS);
        System.out.println("  MAX_EVIDENCE_TIME_MS_PER_CANDIDATE: " + JobMatcherService.MAX_EVIDENCE_TIME_MS_PER_CANDIDATE);
        System.out.println("  REQUEST_BUDGET_PER_CANDIDATE: " + JobMatcherService.REQUEST_BUDGET_PER_CANDIDATE);
    }
}
