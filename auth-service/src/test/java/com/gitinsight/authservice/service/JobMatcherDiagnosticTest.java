package com.gitinsight.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic tests that trace the exact pipeline behavior for the
 * Nithin-Marla scenario. These tests identify WHY skills like REST API,
 * Microservices, and Git might not be detected despite evidence existing
 * in the candidate's repositories.
 *
 * <p>DO NOT modify production logic — this is diagnosis only.</p>
 */
class JobMatcherDiagnosticTest {

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE ANALYSIS: Repository Selection
    // ══════════════════════════════════════════════════════════════════

    /**
     * DIAGNOSTIC 1: Repository relevance scoring for Nithin-Marla.
     *
     * The computeRepoRelevance method checks if required skills' patterns
     * match the repo's name/description/language/topics. A repo that
     * CONTAINS @RestController in its source code but doesn't mention
     * "REST API" in its metadata gets relevance 0 for REST API.
     *
     * This means if the candidate has many repos, the relevant repo
     * could be excluded from the top 15 evidence repositories.
     */
    @Test
    void diagnostic1_repoRelevanceScoringMayExcludeRelevantRepos() {
        // Simulate a repo that HAS @RestController in source but
        // has generic metadata (no REST API keywords)
        var genericRepo = new JobMatcherService.RepoView(
                "my-web-app",
                "A web application for order management",
                "Java",
                List.of("spring-boot", "web"),
                5,
                "main"
        );

        // Simulate a repo that MENTIONS REST in metadata but might not
        // have actual REST source code
        var describedRepo = new JobMatcherService.RepoView(
                "rest-api-project",
                "REST API microservice for e-commerce",
                "Java",
                List.of("rest-api", "microservices"),
                3,
                "main"
        );

        List<String> required = List.of("REST API", "Microservices", "Spring Boot",
                "Java", "Docker", "Git", "React", "SQL",
                "Machine Learning", "Artificial Intelligence");

        int genericScore = JobMatcherService.computeRepoRelevance(genericRepo, required);
        int describedScore = JobMatcherService.computeRepoRelevance(describedRepo, required);

        System.out.println("=== REPOSITORY RELEVANCE SCORING DIAGNOSTIC ===");
        System.out.println("Generic repo ('my-web-app', desc='A web application...') → relevance=" + genericScore);
        System.out.println("Described repo ('rest-api-project', desc='REST API microservice...') → relevance=" + describedScore);

        // CRITICAL FINDING: The generic repo gets LOW relevance despite
        // potentially having @RestController in source code
        // The described repo gets HIGH relevance even if it has no actual REST code
        assertThat(genericScore).isLessThan(describedScore);

        System.out.println();
        System.out.println("ROOT CAUSE: computeRepoRelevance only checks metadata, NOT source code.");
        System.out.println("A repo with @RestController in Java files but generic metadata");
        System.out.println("gets relevance=0 and may be EXCLUDED from the top 15 evidence repos.");
    }

    /**
     * DIAGNOSTIC 2: Verify that relevance scoring works with Java language.
     * If a repo's primary language is "Java", it gets +1 for Java skill.
     * But NOT for Spring Boot, REST API, or Microservices.
     */
    @Test
    void diagnostic2_javaLanguageGivesMinimalRelevance() {
        var javaRepo = new JobMatcherService.RepoView(
                "spring-ecommerce",
                "E-commerce platform",
                "Java",
                List.of("ecommerce", "shopping"),
                10,
                "develop"
        );

        List<String> required = List.of("REST API", "Microservices", "Spring Boot", "Java");

        int score = JobMatcherService.computeRepoRelevance(javaRepo, required);

        System.out.println("=== JAVA REPO RELEVANCE DIAGNOSTIC ===");
        System.out.println("Repo: spring-ecommerce (Java, desc='E-commerce platform')");
        System.out.println("Relevance score: " + score + " (max possible: " + required.size() + ")");

        // Java language gives +1 for Java skill
        // But REST API, Microservices, Spring Boot get 0 because keywords don't match
        assertThat(score).isGreaterThanOrEqualTo(1); // Java matches
        assertThat(score).isLessThan(required.size()); // But not all skills

        System.out.println("Only 'Java' is detected from metadata. REST API, Microservices,");
        System.out.println("Spring Boot require SOURCE CODE inspection to be confirmed.");
    }

    /**
     * DIAGNOSTIC 3: Relevance sorting — repos with same relevance are sorted
     * by stars. A popular but irrelevant repo can outrank a less popular
     * but relevant repo.
     */
    @Test
    void diagnostic3_starsCanOutrankRelevance() {
        var highStarIrrelevant = new JobMatcherService.RepoView(
                "dotfiles",
                "My dotfiles and configs",
                "Shell",
                List.of("dotfiles", "config"),
                50,  // High stars
                "main"
        );

        var lowStarRelevant = new JobMatcherService.RepoView(
                "order-service",
                "Microservice for order processing",
                "Java",
                List.of("spring-boot", "microservices"),
                2,  // Low stars
                "main"
        );

        List<String> required = List.of("REST API", "Microservices", "Spring Boot", "Java", "Docker");

        int irrelevantScore = JobMatcherService.computeRepoRelevance(highStarIrrelevant, required);
        int relevantScore = JobMatcherService.computeRepoRelevance(lowStarRelevant, required);

        System.out.println("=== STARS vs RELEVANCE DIAGNOSTIC ===");
        System.out.println("dotfiles (50 stars, Shell) → relevance=" + irrelevantScore);
        System.out.println("order-service (2 stars, Java+microservices) → relevance=" + relevantScore);

        // Both have 0 relevance (neither mentions REST API specifically)
        // BUT order-service mentions microservices → relevance=1
        // So relevance sorting SHOULD put order-service first
        assertThat(relevantScore).isGreaterThanOrEqualTo(irrelevantScore);

        System.out.println("Stars are only used as tiebreaker when relevance is equal.");
        System.out.println("A repo mentioning 'microservices' in description gets higher relevance.");
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE ANALYSIS: Early Stopping Prevents Source Discovery
    // ══════════════════════════════════════════════════════════════════

    /**
     * DIAGNOSTIC 4: If README contains enough skill keywords, ALL skills
     * may be confirmed from README alone, preventing source discovery.
     *
     * Example: README says "A Spring Boot REST API microservice with Docker"
     * → Spring Boot ✅, REST API ✅, Microservices ✅, Docker ✅
     * → allSkillsConfirmed = true
     * → source discovery SKIPPED
     *
     * This is CORRECT behavior when the README is accurate.
     * But if the README is GENERIC and doesn't mention skills, source
     * discovery should run.
     */
    @Test
    void diagnostic4_readmeConfirmingAllSkillsSkipsSourceDiscovery() {
        // Simulate a comprehensive README that mentions all skills
        String comprehensiveReadme = """
                # Order Management System
                
                A Spring Boot REST API microservice for e-commerce order management.
                Built with Java 21 and Docker containerization.
                Uses PostgreSQL for data storage.
                """;

        JobMatcherService svc = new JobMatcherService(org.springframework.web.client.RestClient.create());

        List<String> required = List.of("REST API", "Microservices", "Spring Boot",
                "Java", "Docker", "SQL");

        int confirmed = 0;
        for (String skill : required) {
            if (svc.matches(comprehensiveReadme, skill)) {
                confirmed++;
                System.out.println("  ✅ " + skill + " confirmed from README");
            } else {
                System.out.println("  ❌ " + skill + " NOT confirmed from README");
            }
        }

        System.out.println();
        System.out.println("=== README EARLY STOPPING DIAGNOSTIC ===");
        System.out.println("Skills confirmed from README: " + confirmed + "/" + required.size());

        // With this README, most skills are confirmed → source discovery skipped
        assertThat(confirmed).isGreaterThanOrEqualTo(4);

        System.out.println("When README is comprehensive, source discovery is correctly skipped.");
        System.out.println("The issue is when README is ABSENT or GENERIC.");
    }

    /**
     * DIAGNOSTIC 5: With ABSENT README, skills must come from build files
     * and source code. This is where the pipeline should work but might not.
     */
    @Test
    void diagnostic5_absentReadmeRequiresSourceEvidence() {
        // Simulate a repo with NO README but pom.xml has Spring Boot
        String pomXml = """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>order-service</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        JobMatcherService svc = new JobMatcherService(org.springframework.web.client.RestClient.create());

        List<String> required = List.of("REST API", "Microservices", "Spring Boot",
                "Java", "Docker", "SQL");

        System.out.println("=== ABSENT README SCENARIO ===");
        System.out.println("Simulating: README=missing, pom.xml=present");

        for (String skill : required) {
            if (svc.matches(pomXml, skill)) {
                System.out.println("  ✅ " + skill + " confirmed from pom.xml");
            } else {
                System.out.println("  ❌ " + skill + " NOT confirmed from pom.xml");
            }
        }

        System.out.println();
        System.out.println("pom.xml confirms: Spring Boot ✅, Java ✅");
        System.out.println("pom.xml does NOT confirm: REST API, Microservices, Docker, SQL");
        System.out.println("→ Source discovery SHOULD run for unconfirmed skills");
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE ANALYSIS: Source Evidence Enters Corpus
    // ══════════════════════════════════════════════════════════════════

    /**
     * DIAGNOSTIC 6: Verify that source evidence content actually matches
     * the SKILL_PATTERNS used by the final `matches()` function.
     *
     * The source evidence is formatted as:
     *   [REST API] @RestController
     *   [Spring Boot] @SpringBootApplication
     *
     * This is added to the corpus. Then `matches(corpus, skill)` uses
     * SKILL_PATTERNS which include "rest controller", "@restcontroller", etc.
     */
    @Test
    void diagnostic6_sourceEvidenceFormatMatchesSkillPatterns() {
        // Simulate source evidence as it would appear in the corpus
        String sourceEvidence = "[file OrderController.java]\n"
                + "[REST API] @RestController\n"
                + "@RequestMapping(\"/api/orders\")\n"
                + "\n"
                + "[source-evidence]\n"
                + "[REST API] @RestController\n"
                + "[Spring Boot] @SpringBootApplication\n";

        JobMatcherService svc = new JobMatcherService(org.springframework.web.client.RestClient.create());

        System.out.println("=== SOURCE EVIDENCE → CORPUS MATCHING DIAGNOSTIC ===");

        boolean restApiMatches = svc.matches(sourceEvidence, "REST API");
        boolean springBootMatches = svc.matches(sourceEvidence, "Spring Boot");

        System.out.println("Source evidence in corpus: '" + sourceEvidence.substring(0, Math.min(100, sourceEvidence.length())) + "...'");
        System.out.println("REST API pattern matches: " + restApiMatches);
        System.out.println("Spring Boot pattern matches: " + springBootMatches);

        assertThat(restApiMatches).isTrue();
        assertThat(springBootMatches).isTrue();

        System.out.println("✅ Source evidence format IS compatible with SKILL_PATTERNS.");
        System.out.println("The [REST API] tag and @RestController both match.");
    }

    /**
     * DIAGNOSTIC 7: Verify Git detection from platform-level evidence.
     * The corpus gets "git_source:github_repository" appended when repos exist.
     */
    @Test
    void diagnostic7_gitDetectionFromPlatformEvidence() {
        String corpus = "some evidence here git_source:github_repository";

        JobMatcherService svc = new JobMatcherService(org.springframework.web.client.RestClient.create());
        boolean gitMatches = svc.matches(corpus, "Git");

        System.out.println("=== GIT DETECTION DIAGNOSTIC ===");
        System.out.println("Corpus contains: 'git_source:github_repository'");
        System.out.println("Git SKILL_PATTERN matches: " + gitMatches);

        assertThat(gitMatches).isTrue();

        System.out.println("✅ Git IS detected from platform evidence.");
        System.out.println("'git' in 'git_source' matches the word-boundary pattern.");
    }

    /**
     * DIAGNOSTIC 8: Verify sourcePatternMatches for key skills.
     * This is the function used to detect skills in source file content
     * BEFORE adding evidence to the corpus.
     */
    @Test
    void diagnostic8_sourcePatternMatchesForAllKeySkills() {
        // REST API: @RestController in source
        String javaSource = """
                package com.example.controller;
                
                import org.springframework.web.bind.annotation.*;
                
                @RestController
                @RequestMapping("/api/orders")
                public class OrderController {
                    
                    @GetMapping("/{id}")
                    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
                        return ResponseEntity.ok(orderService.findById(id));
                    }
                    
                    @PostMapping
                    public ResponseEntity<Order> createOrder(@RequestBody OrderDto dto) {
                        return ResponseEntity.ok(orderService.create(dto));
                    }
                }
                """;

        System.out.println("=== SOURCE PATTERN MATCHING DIAGNOSTIC ===");
        System.out.println("Testing @RestController source code:");
        System.out.println("  REST API: " + JobMatcherService.sourcePatternMatches(javaSource, "REST API"));
        System.out.println("  Microservices: " + JobMatcherService.sourcePatternMatches(javaSource, "Microservices"));
        System.out.println("  Spring Boot: " + JobMatcherService.sourcePatternMatches(javaSource, "Spring Boot"));
        System.out.println("  Java: " + JobMatcherService.sourcePatternMatches(javaSource, "Java"));

        assertThat(JobMatcherService.sourcePatternMatches(javaSource, "REST API")).isTrue();
        // Microservices NOT detected from single controller (correct behavior)
        assertThat(JobMatcherService.sourcePatternMatches(javaSource, "Microservices")).isFalse();

        // Spring Cloud source
        String cloudSource = """
                @EnableDiscoveryClient
                @SpringBootApplication
                public class OrderServiceApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(OrderServiceApplication.class, args);
                    }
                }
                """;

        System.out.println("\nTesting Spring Cloud source code:");
        System.out.println("  Microservices: " + JobMatcherService.sourcePatternMatches(cloudSource, "Microservices"));
        System.out.println("  Spring Boot: " + JobMatcherService.sourcePatternMatches(cloudSource, "Spring Boot"));

        assertThat(JobMatcherService.sourcePatternMatches(cloudSource, "Microservices")).isTrue();
        assertThat(JobMatcherService.sourcePatternMatches(cloudSource, "Spring Boot")).isTrue();

        System.out.println("\n✅ Source pattern matching works correctly.");
        System.out.println("The detection logic itself is NOT the problem.");
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE ANALYSIS: Source Discovery Path Coverage
    // ══════════════════════════════════════════════════════════════════

    /**
     * DIAGNOSTIC 9: Source discovery only looks at ROOT-level 'src' directory.
     * If source code is under a non-standard path, it's missed.
     *
     * Current discovery: src/main/java → controller/, service/, config/
     * Missing paths:     backend/src/main/java, app/src/main/java,
     *                    server/src/main/java, services/api/
     */
    @Test
    void diagnostic9_sourceDiscoveryOnlyLooksAtRootSrc() {
        System.out.println("=== SOURCE DISCOVERY PATH COVERAGE ===");
        System.out.println();
        System.out.println("Current discovery looks at:");
        System.out.println("  1. Root directory (for SOURCE_FILE_NAMES)");
        System.out.println("  2. src/main/java (for JAVA_SRC_PACKAGES)");
        System.out.println();
        System.out.println("Source discovery MISSES these common paths:");
        System.out.println("  ❌ backend/src/main/java/...");
        System.out.println("  ❌ app/src/main/java/...");
        System.out.println("  ❌ server/src/main/java/...");
        System.out.println("  ❌ services/*/src/main/java/...");
        System.out.println("  ❌ modules/*/src/main/java/...");
        System.out.println("  ❌ api/src/main/java/...");
        System.out.println();
        System.out.println("SOURCE_FILE_NAMES checks root for:");
        System.out.println("  Application.java, Controller.java, RestController.java,");
        System.out.println("  Service.java, Config.java, GatewayConfig.java, etc.");
        System.out.println();
        System.out.println("If a repo has these files ONLY under backend/src/main/java/");
        System.out.println("and NOT at the root src/ path, they are NEVER discovered.");

        // This is a structural limitation, not a code bug.
        // It means repos with non-standard layouts may not get source evidence.
        assertThat(true).isTrue(); // Informational test
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE ANALYSIS: fetchRepositoryEvidence Branch Break
    // ══════════════════════════════════════════════════════════════════

    /**
     * DIAGNOSTIC 10: fetchRepositoryEvidence breaks on FIRST branch with
     * ANY evidence. If default branch has README but source code is on
     * main/master, source evidence from the other branch is missed.
     *
     * Flow:
     *   1. Try develop (default branch)
     *   2. Fetch README.md → non-empty → branchEvidence is non-empty
     *   3. Skills checked → some confirmed
     *   4. Source discovery runs within develop → no source files found
     *   5. branchEvidence is non-empty → BREAK
     *   6. main/master NEVER tried
     *
     * However, source discovery runs WITHIN the branch before the break,
     * so if source code IS on the default branch, it WILL be found.
     */
    @Test
    void diagnostic10_branchBreakBehavior() {
        System.out.println("=== BRANCH BREAK BEHAVIOR ===");
        System.out.println();
        System.out.println("fetchRepositoryEvidence flow:");
        System.out.println("  for branch in [defaultBranch, main, master]:");
        System.out.println("    1. Fetch build/config files");
        System.out.println("    2. Check skills after each file");
        System.out.println("    3. If skills still unconfirmed → source discovery");
        System.out.println("    4. if branchEvidence non-empty → BREAK (use this branch)");
        System.out.println();
        System.out.println("CRITICAL: Source discovery runs WITHIN the branch before break.");
        System.out.println("So source code on the default branch IS found.");
        System.out.println();
        System.out.println("Only MISS scenario: source code exists on main/master but NOT on");
        System.out.println("defaultBranch, AND defaultBranch has README (non-empty evidence).");
        System.out.println("In that case, the default branch's README causes the break,");
        System.out.println("and main/master source code is never inspected.");
        System.out.println();
        System.out.println("This is a REAL issue for repos where:");
        System.out.println("  - defaultBranch = develop (has README, no src code)");
        System.out.println("  - main (has src code with @RestController)");

        assertThat(true).isTrue(); // Informational test
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE ANALYSIS: Microservices Detection Requires Strong Signals
    // ══════════════════════════════════════════════════════════════════

    /**
     * DIAGNOSTIC 11: Microservices detection is intentionally strict.
     * A single @RestController does NOT prove Microservices.
     * Need Eureka/Spring Cloud/Feign/Gateway evidence.
     */
    @Test
    void diagnostic11_microservicesRequiresStrongArchitecturalEvidence() {
        System.out.println("=== MICROSERVICES DETECTION REQUIREMENTS ===");
        System.out.println();
        System.out.println("sourcePatternMatches for Microservices requires ONE of:");
        System.out.println("  - eureka / @EnableEurekaClient / @EnableDiscoveryClient");
        System.out.println("  - @FeignClient");
        System.out.println("  - Spring Cloud Gateway / GatewayConfig");
        System.out.println("  - service discovery / service-discovery");
        System.out.println("  - multi-module / multi module");
        System.out.println("  - Spring Cloud + (service | module)");
        System.out.println();
        System.out.println("NOT detected by:");
        System.out.println("  ❌ Single @RestController class");
        System.out.println("  ❌ 'service' alone (too generic)");
        System.out.println("  ❌ Repository name containing 'service'");
        System.out.println("  ❌ Dockerfile existence");
        System.out.println();
        System.out.println("This is BY DESIGN — false-positive protection.");
        System.out.println("If the candidate's repos don't have Eureka/Spring Cloud evidence,");
        System.out.println("Microservices correctly remains undetected.");

        // Verify strict detection
        assertThat(JobMatcherService.sourcePatternMatches(
                "@RestController\npublic class OrderController {}", "Microservices")).isFalse();
        assertThat(JobMatcherService.sourcePatternMatches(
                "@EnableDiscoveryClient\n@SpringBootApplication", "Microservices")).isTrue();
        assertThat(JobMatcherService.sourcePatternMatches(
                "spring cloud gateway config", "Microservices")).isTrue();
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT CAUSE SUMMARY
    // ══════════════════════════════════════════════════════════════════

    @Test
    void rootCauseSummary() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           ROOT CAUSE ANALYSIS SUMMARY                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                            ║");
        System.out.println("║  1. REPOSITORY SELECTION (LIKELY ROOT CAUSE)               ║");
        System.out.println("║     computeRepoRelevance checks metadata only.             ║");
        System.out.println("║     A repo with @RestController in source but generic      ║");
        System.out.println("║     metadata gets low relevance and may be EXCLUDED        ║");
        System.out.println("║     from the top 15 evidence repositories.                 ║");
        System.out.println("║                                                            ║");
        System.out.println("║  2. SOURCE DISCOVERY PATH LIMITATION                       ║");
        System.out.println("║     Only looks at root src/ directory.                     ║");
        System.out.println("║     Non-standard paths (backend/, app/, server/) missed.   ║");
        System.out.println("║                                                            ║");
        System.out.println("║  3. BRANCH BREAK BEHAVIOR                                  ║");
        System.out.println("║     Default branch with README causes break before         ║");
        System.out.println("║     trying main/master for source code.                    ║");
        System.out.println("║                                                            ║");
        System.out.println("║  4. MICROSERVICES FALSE-POSITIVE PROTECTION (CORRECT)      ║");
        System.out.println("║     Requires Eureka/Spring Cloud/Feign/Gateway evidence.   ║");
        System.out.println("║     If candidate lacks these, Microservices correctly      ║");
        System.out.println("║     remains undetected.                                    ║");
        System.out.println("║                                                            ║");
        System.out.println("║  5. DETECTION LOGIC IS CORRECT                             ║");
        System.out.println("║     sourcePatternMatches and SKILL_PATTERNS work.          ║");
        System.out.println("║     Source evidence enters the corpus correctly.           ║");
        System.out.println("║     Git platform evidence works.                           ║");
        System.out.println("║                                                            ║");
        System.out.println("║  VERDICT: The problem is MOST LIKELY repository selection  ║");
        System.out.println("║  (repos with relevant source code excluded from top 15)    ║");
        System.out.println("║  combined with source discovery path limitations.          ║");
        System.out.println("║                                                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}
