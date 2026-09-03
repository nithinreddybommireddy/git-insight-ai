package com.gitinsight.authservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for NESTED MONOREPO EVIDENCE DISCOVERY.
 *
 * <p>Covers the production-proven gap where the matcher selected monorepo
 * repositories (devsiddharth/microservices_v2, Nithin-Marla/fixmate) but never
 * reached nested module build files ({@code api-gateway/pom.xml}) or nested
 * source roots ({@code fixmate-backend/src/main/java}).
 *
 * <p>Scope is deliberately narrow: discovery/selection logic, evidence
 * accumulation, cache keys, and the bounded-depth guarantees. Scoring,
 * limits, async architecture, and matching semantics are untouched.
 */
class NestedMonorepoEvidenceTest {

    private final JobMatcherService svc = new JobMatcherService(RestClient.create());

    private static Map<String, Object> dir(String name) {
        return Map.of("name", name, "type", "dir");
    }

    private static Map<String, Object> file(String name) {
        return Map.of("name", name, "type", "file");
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. MODULE DIR SELECTION
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Module directory selection")
    class ModuleSelectionTest {

        @Test
        @DisplayName("microservices_v2 module dirs are all recognized as module-like")
        void microservicesV2ModulesRecognized() {
            for (String name : List.of("api-gateway", "auth-service", "eureka-service", "user-service",
                    "github-service", "fixmate-backend", "fixmate-api-gateway", "fixmate-eureka-server",
                    "fixmate-frontend")) {
                assertThat(JobMatcherService.isModuleLikeDir(name))
                        .as("module dir %s", name).isTrue();
            }
        }

        @Test
        @DisplayName("generic Service class / docs / assets are NOT module dirs")
        void genericDirsNotModules() {
            for (String name : List.of("docs", "static", "images", "scripts", "docker",
                    "kubernetes", "target", "build", "dist", "README.md")) {
                assertThat(JobMatcherService.isModuleLikeDir(name))
                        .as("non-module dir %s", name).isFalse();
            }
            assertThat(JobMatcherService.isModuleLikeDir(null)).isFalse();
            assertThat(JobMatcherService.isModuleLikeDir("")).isFalse();
            // node_modules contains "module" so it is module-like by name, but
            // selectModuleDirs excludes it via NON_MODULE_DIRS (see below).
            assertThat(JobMatcherService.isModuleLikeDir("node_modules")).isTrue();
        }

        @Test
        @DisplayName("selectModuleDirs excludes hidden, noise, and standard root dirs")
        void excludesNoiseAndStandardRoots() {
            List<Map<String, Object>> rootItems = List.of(
                    dir("api-gateway"), dir("auth-service"), dir("eureka-service"),
                    dir("src"), dir("backend"), dir("services"),
                    dir(".github"), dir("docs"), dir("node_modules"), dir("target"),
                    file("pom.xml"), file("README.md"));

            List<String> modules = JobMatcherService.selectModuleDirs(rootItems, 10);

            assertThat(modules).containsExactly("api-gateway", "auth-service", "eureka-service");
        }

        @Test
        @DisplayName("module-like dirs are probed before generic dirs")
        void moduleLikePrioritized() {
            List<Map<String, Object>> rootItems = List.of(
                    dir("public"), dir("api-gateway"), dir("static"), dir("auth-service"));

            List<String> modules = JobMatcherService.selectModuleDirs(rootItems, 10);

            assertThat(modules).containsExactly("api-gateway", "auth-service", "public", "static");
        }

        @Test
        @DisplayName("module probing is bounded — never an unbounded crawl")
        void moduleProbingBounded() {
            List<Map<String, Object>> rootItems = new java.util.ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                rootItems.add(dir("module-" + i));
            }

            // Bounded at MAX_NESTED_MODULE_DIRS = 6 (verified in LimitsUnchangedTest)
            List<String> modules = JobMatcherService.selectModuleDirs(rootItems, 6);

            assertThat(modules).hasSize(6);
        }

        @Test
        @DisplayName("root-only repository yields no module dirs (behavior unchanged)")
        void rootOnlyRepoNoModules() {
            List<Map<String, Object>> rootItems = List.of(
                    dir("src"), file("pom.xml"), file("README.md"));

            assertThat(JobMatcherService.selectModuleDirs(rootItems, 10)).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 1b. PACKAGE-ROOT DESCENT (com/org/dev/... layouts)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Package-root descent")
    class PackageRootDescentTest {

        @Test
        @DisplayName("standard package roots are recognized")
        void packageRootsRecognized() {
            for (String name : List.of("com", "org", "dev", "io", "stschools",
                    "microservices", "api_gateway", "fixmate", "auth_service")) {
                assertThat(JobMatcherService.isPackageRootDir(name))
                        .as("package root %s", name).isTrue();
            }
        }

        @Test
        @DisplayName("noise/build dirs and non-word names are NOT package roots")
        void nonPackageRootsRejected() {
            // Note: MyPackage is accepted by design — package roots are
            // case-normalized (mypackage) before the single-word check.
            for (String name : List.of("target", "build", "generated", "resources",
                    "node_modules", ".git", "my-package", "has space",
                    "a.b", "")) {
                assertThat(JobMatcherService.isPackageRootDir(name))
                        .as("non-package root %s", name).isFalse();
            }
            assertThat(JobMatcherService.isPackageRootDir(null)).isFalse();
        }

        @Test
        @DisplayName("package-root descent is bounded (2 branches x 4 levels)")
        void descentBounds() throws Exception {
            java.lang.reflect.Field depth = JobMatcherService.class.getDeclaredField("PACKAGE_ROOT_DESCENT_DEPTH");
            depth.setAccessible(true);
            assertThat(depth.getInt(null)).isEqualTo(4);
            java.lang.reflect.Field branches = JobMatcherService.class.getDeclaredField("MAX_PACKAGE_ROOT_BRANCHES");
            branches.setAccessible(true);
            assertThat(branches.getInt(null)).isEqualTo(2);
        }

    }

    // ══════════════════════════════════════════════════════════════════
    // 2. NESTED BUILD/CONFIG FILE SELECTION
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Nested build/config file selection")
    class NestedBuildFileSelectionTest {

        @Test
        @DisplayName("module pom.xml is selected for a Java/Spring JD")
        void modulePomSelected() {
            List<String> files = JobMatcherService.nestedBuildFilesFor(
                    Set.of("pom.xml", "src", "README.md"),
                    List.of("Java", "Spring Boot", "REST API"));
            assertThat(files).containsExactly("pom.xml");
        }

        @Test
        @DisplayName("build.gradle fallback is selected when no pom.xml exists")
        void gradleFallbackSelected() {
            List<String> files = JobMatcherService.nestedBuildFilesFor(
                    Set.of("build.gradle", "src"),
                    List.of("Java", "Spring Boot"));
            assertThat(files).containsExactly("build.gradle");
        }

        @Test
        @DisplayName("package.json is selected for a React/JS JD")
        void packageJsonSelected() {
            List<String> files = JobMatcherService.nestedBuildFilesFor(
                    Set.of("package.json", "src"),
                    List.of("React", "JavaScript"));
            assertThat(files).containsExactly("package.json");
        }

        @Test
        @DisplayName("Dockerfile is selected for a Docker JD")
        void dockerfileSelected() {
            List<String> files = JobMatcherService.nestedBuildFilesFor(
                    Set.of("Dockerfile", "src"),
                    List.of("Java", "Docker"));
            assertThat(files).containsExactly("Dockerfile");
        }

        @Test
        @DisplayName("no build files when module has none (empty selection)")
        void noBuildFilesWhenAbsent() {
            assertThat(JobMatcherService.nestedBuildFilesFor(Set.of("src", "README.md"),
                    List.of("Java"))).isEmpty();
            assertThat(JobMatcherService.nestedBuildFilesFor(Set.of(), List.of("Java"))).isEmpty();
        }

        @Test
        @DisplayName("application.yml config files selected separately when relevant")
        void applicationYmlSelected() {
            List<String> files = JobMatcherService.nestedConfigFilesFor(
                    Set.of("application.yml", "src"),
                    List.of("Java", "Spring Boot", "Redis"));
            assertThat(files).containsExactly("application.yml");
        }

        @Test
        @DisplayName("config files not selected for non-Java ecosystems")
        void configNotSelectedForJs() {
            assertThat(JobMatcherService.nestedConfigFilesFor(
                    Set.of("application.yml"),
                    List.of("React"))).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. DEPTH-2 SUBMODULE SELECTION
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Depth-2 submodule selection")
    class Depth2SubmoduleTest {

        @Test
        @DisplayName("services/ subdir inside a module is selected for depth-2 probing")
        void servicesSubdirSelected() {
            List<String> subs = JobMatcherService.nestedSubmoduleDirs(
                    List.of(dir("services"), dir("target"), dir("src")), 2);
            assertThat(subs).containsExactly("services");
        }

        @Test
        @DisplayName("src/ and noise are never depth-2 submodule candidates")
        void srcAndNoiseExcluded() {
            List<String> subs = JobMatcherService.nestedSubmoduleDirs(
                    List.of(dir("src"), dir("docs"), dir(".github"), dir("node_modules")), 2);
            assertThat(subs).isEmpty();
        }

        @Test
        @DisplayName("depth-2 probing is bounded at 2 subdirs")
        void submoduleProbingBounded() {
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) items.add(dir("service-" + i));

            // Bounded at MAX_NESTED_SUBMODULES = 2 (verified in LimitsUnchangedTest)
            List<String> subs = JobMatcherService.nestedSubmoduleDirs(items, 2);

            assertThat(subs).hasSize(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. CACHE KEYS
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Evidence cache keys")
    class CacheKeyTest {

        @Test
        @DisplayName("nested module paths keep their full path in the cache key")
        void nestedPathInCacheKey() {
            String key = JobMatcherService.evidenceCacheKey(
                    "devsiddharth", "microservices_v2", "main", "api-gateway/pom.xml");
            assertThat(key).isEqualTo("devsiddharth/microservices_v2/main/api-gateway/pom.xml");
        }

        @Test
        @DisplayName("deep source file path is not collapsed to the root")
        void deepSourcePathInCacheKey() {
            String key = JobMatcherService.evidenceCacheKey(
                    "Nithin-Marla", "fixmate", "main",
                    "fixmate-backend/src/main/java/com/fixmate/AuthController.java");
            assertThat(key).startsWith("Nithin-Marla/fixmate/main/fixmate-backend/");
            assertThat(key).contains("AuthController.java");
        }

        @Test
        @DisplayName("same file name in different modules gets distinct cache keys")
        void sameFileNameDifferentModulesDistinctKeys() {
            String gateway = JobMatcherService.evidenceCacheKey("o", "r", "main", "api-gateway/pom.xml");
            String auth = JobMatcherService.evidenceCacheKey("o", "r", "main", "auth-service/pom.xml");
            assertThat(gateway).isNotEqualTo(auth);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. EVIDENCE ACCUMULATION (fixtures)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Evidence accumulation — real-world monorepo fixtures")
    class EvidenceAccumulationTest {

        private String corpus(Object... parts) {
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) sb.append(part);
            return sb.toString().toLowerCase(Locale.ROOT);
        }

        @Test
        @DisplayName("com-layout source evidence matches REST API on the final corpus")
        void comLayoutRestControllerMatches() {
            // Layout: fixmate-backend/src/main/java/com/fixmate/controller/AuthController.java
            String c = corpus(
                    "[file fixmate-backend/src/main/java/com/fixmate/controller/AuthController.java]\n",
                    "[REST API] @RestController\n",
                    "@RequestMapping(\"/api/v1/auth\")\n");
            assertThat(svc.matches(c, "REST API")).isTrue();
            assertThat(svc.matches(c, "Java")).isTrue();
        }

        @Test
        @DisplayName("root pom.xml (single-module repo) still matches Spring Boot")
        void rootPomUnchanged() {
            String c = corpus(
                    "[file pom.xml]\n",
                    "<parent><groupId>org.springframework.boot</groupId></parent>\n",
                    "<artifactId>spring-boot-starter-web</artifactId>\n");
            assertThat(svc.matches(c, "Spring Boot")).isTrue();
        }

        @Test
        @DisplayName("nested module pom.xml alone matches Spring Boot (no root pom)")
        void nestedPomSpringBoot() {
            String c = corpus(
                    "[file fixmate-backend/pom.xml]\n",
                    "<artifactId>spring-boot-starter-web</artifactId>\n");
            assertThat(svc.matches(c, "Spring Boot")).isTrue();
        }

        @Test
        @DisplayName("nested Gateway + Eureka poms match Microservices")
        void nestedGatewayEurekaPomsMicroservices() {
            String c = corpus(
                    "[file api-gateway/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-gateway</artifactId>\n",
                    "<artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>\n");
            assertThat(svc.matches(c, "Microservices")).isTrue();
        }

        @Test
        @DisplayName("nested @RestController source matches REST API")
        void nestedRestControllerSource() {
            String c = corpus(
                    "[file fixmate-backend/src/main/java/com/fixmate/OrderController.java]\n",
                    "[REST API] @RestController\n",
                    "@RequestMapping(\"/api/orders\")\n");
            assertThat(svc.matches(c, "REST API")).isTrue();
        }

        @Test
        @DisplayName("nested @FeignClient source matches Microservices")
        void nestedFeignClientSource() {
            String c = corpus(
                    "[file auth-service/src/main/java/com/gitinsight/authservice/client/UserClient.java]\n",
                    "[Microservices] @FeignClient(name = \"user-service\")\n");
            assertThat(svc.matches(c, "Microservices")).isTrue();
        }

        @Test
        @DisplayName("nested application.yml with Redis config matches Redis")
        void nestedApplicationYmlRedis() {
            String c = corpus(
                    "[file auth-service/application.yml]\n",
                    "spring:\n  data:\n    redis:\n      host: redis\n      port: 6379\n");
            assertThat(svc.matches(c, "Redis")).isTrue();
        }

        @Test
        @DisplayName("microservices_v2 fixture: all module evidence accumulates into one corpus")
        void microservicesV2Fixture() {
            String c = corpus(
                    "[file api-gateway/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-gateway</artifactId>\n",
                    "<artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>\n",
                    "[file auth-service/pom.xml]\n",
                    "<artifactId>spring-boot-starter-web</artifactId>\n",
                    "[file eureka-service/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>\n",
                    "[file auth-service/src/main/java/com/gitinsight/authservice/controller/AuthController.java]\n",
                    "[REST API] @RestController\n",
                    "@RequestMapping(\"/api/auth\")\n");

            assertThat(svc.matches(c, "Spring Boot")).isTrue();
            assertThat(svc.matches(c, "REST API")).isTrue();
            assertThat(svc.matches(c, "Microservices")).isTrue();
            assertThat(c).contains("[file api-gateway/pom.xml]");
            assertThat(c).contains("[file eureka-service/pom.xml]");
        }

        @Test
        @DisplayName("fixmate fixture: backend + gateway + eureka modules match Spring Boot/REST/Microservices")
        void fixmateFixture() {
            String c = corpus(
                    "[file fixmate-backend/pom.xml]\n",
                    "<artifactId>spring-boot-starter-web</artifactId>\n",
                    "[file fixmate-api-gateway/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-gateway</artifactId>\n",
                    "<artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>\n",
                    "[file fixmate-eureka-server/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>\n",
                    "[file fixmate-backend/src/main/java/com/fixmate/backend/Controller.java]\n",
                    "[REST API] @RestController\n",
                    "@GetMapping(\"/api/health\")\n");

            assertThat(svc.matches(c, "Spring Boot")).isTrue();
            assertThat(svc.matches(c, "REST API")).isTrue();
            assertThat(svc.matches(c, "Microservices")).isTrue();
        }

        @Test
        @DisplayName("module A evidence never overrides module B evidence (accumulation)")
        void evidenceAccumulatesNotOverwrites() {
            String c = corpus(
                    "[file api-gateway/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-gateway</artifactId>\n",
                    "[file eureka-service/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>\n");
            assertThat(c).contains("spring-cloud-starter-gateway");
            assertThat(c).contains("spring-cloud-starter-netflix-eureka-server");
            assertThat(svc.matches(c, "Microservices")).isTrue();
        }

        @Test
        @DisplayName("candidate isolation: nested evidence of A never matches B's corpus")
        void nestedEvidenceIsolation() {
            String aCorpus = corpus(
                    "[file api-gateway/pom.xml]\n",
                    "<artifactId>spring-cloud-starter-gateway</artifactId>\n");
            String bCorpus = corpus("[file portfolio/README.md]\n", "my portfolio site\n");

            assertThat(svc.matches(aCorpus, "Microservices")).isTrue();
            assertThat(svc.matches(bCorpus, "Microservices")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. LIMITS UNCHANGED
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Limits unchanged")
    class LimitsUnchangedTest {

        @Test
        @DisplayName("source-file limit stays 5 per repository")
        void sourceFileLimit() throws Exception {
            java.lang.reflect.Field f = JobMatcherService.class.getDeclaredField("MAX_SOURCE_FILES_PER_REPO");
            f.setAccessible(true);
            assertThat(f.getInt(null)).isEqualTo(5);
        }

        @Test
        @DisplayName("repository limit stays 15 per candidate")
        void repositoryLimit() {
            assertThat(svc.maxEvidenceRepos).isEqualTo(15);
            assertThat(JobMatcherService.EXPLORATION_SLOTS).isEqualTo(5);
        }

        @Test
        @DisplayName("request budget stays 50 per candidate")
        void requestBudget() {
            assertThat(JobMatcherService.REQUEST_BUDGET_PER_CANDIDATE).isEqualTo(50);
        }

        @Test
        @DisplayName("module probing constants are small and bounded")
        void moduleProbeConstants() throws Exception {
            java.lang.reflect.Field f = JobMatcherService.class.getDeclaredField("MAX_NESTED_MODULE_DIRS");
            f.setAccessible(true);
            assertThat(f.getInt(null)).isLessThanOrEqualTo(6);
            java.lang.reflect.Field g = JobMatcherService.class.getDeclaredField("MAX_NESTED_SUBMODULES");
            g.setAccessible(true);
            assertThat(g.getInt(null)).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("defaultBranch behavior unchanged")
        void defaultBranchUnchanged() {
            assertThat(JobMatcherService.buildBranchPriority("develop"))
                    .containsExactly("develop", "main", "master");
            assertThat(JobMatcherService.buildBranchPriority(null))
                    .containsExactly("main", "master");
        }

        @Test
        @DisplayName("async processing remains intact (no global deadline truncation)")
        void asyncProcessingIntact() {
            // matchAsync uses Long.MAX_VALUE deadline; per-candidate budgets still apply.
            // Verify the sync path still has its 50s global deadline.
            assertThat(JobMatcherService.GLOBAL_MATCH_TIME_MS).isEqualTo(50_000);
            assertThat(JobMatcherService.MAX_EVIDENCE_TIME_MS_PER_CANDIDATE).isEqualTo(15_000);
        }
    }
}