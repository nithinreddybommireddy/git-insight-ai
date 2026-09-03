package com.gitinsight.authservice.service;

import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.dto.response.JobMatchResponse.AiExplanation;
import com.gitinsight.authservice.dto.response.JobMatchResponse.JobMatchCandidate;
import com.gitinsight.common.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recruiter job-description matching.
 *
 * <p>Parses an uploaded job-description file (.txt / .md / .pdf), extracts the
 * required tech skills, then runs a fresh candidate search: for every username
 * (uploaded CSV/TXT or the recruiter's saved candidates) it pulls the live
 * developer score, weighted language stack, and repositories from
 * github-service and ranks each candidate by job fit.
 *
 * <p>Match score = 60% skill match (required skills found in the candidate's
 * stack) + 40% developer score. All matching is deterministic and word-boundary
 * aware, so it works offline and is fully unit-testable.
 *
 * <p>Optionally (AI mode), the deterministic results are sent to github-service's
 * Gemini job-match endpoint, which returns per-candidate fit explanations.
 * AI failures degrade gracefully: the deterministic ranking is always returned.
 */
@Service
public class JobMatcherService {

    private static final Logger log = LoggerFactory.getLogger(JobMatcherService.class);

    /** Maximum candidates analyzed per match request (rate-limit friendly). */
    public static final int MAX_CANDIDATES = 25;

    /** Candidates sent to Gemini for AI explanations (token-budget friendly). */
    private static final int AI_CANDIDATE_LIMIT = 10;

    private static final int MAX_JOB_DESCRIPTION_CHARS = 3500;

    /** PDF uploads are parsed with PDFBox, which warns that untrusted files can
     *  consume unexpected CPU/memory — cap the page count so a crafted 5 MB PDF
     *  cannot become a resource-exhaustion vector. */
    private static final int MAX_PDF_PAGES = 50;

    /** Only inspect the most relevant repositories for source-level evidence. */
    private static final int HARD_MAX_EVIDENCE_REPOS = 15;
    final int maxEvidenceRepos;

    /** Keep raw evidence bounded so one repository cannot dominate matching. */
    private static final int MAX_EVIDENCE_CHARS_PER_FILE = 8_000;
    private static final int MAX_TOTAL_EVIDENCE_PER_REPO = 24_000;

    /** Maximum source files to fetch per repository in SYNC mode (bounded GitHub requests). */
    private static final int MAX_SOURCE_FILES_PER_REPO = 5;

    /**
     * Maximum source files to fetch per repository in ASYNC FULL-EVIDENCE mode.
     * Deliberately separate from {@link #MAX_SOURCE_FILES_PER_REPO}: the async
     * worker runs in the background (no 60s HTTP deadline), so it may fetch
     * deeper evidence without affecting the synchronous endpoint's latency.
     * Configurable via FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO.
     */
    static final int FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO = 20;

    /**
     * Minimum number of repositories that must receive FULL evidence analysis
     * per candidate (deep/async mode): at least 10 repositories when the
     * candidate has >= 10, otherwise ALL available repositories. "Analyzed"
     * means actual evidence inspection (docs/build/config/nested-module/source),
     * not merely metadata retrieval. Configurable via MIN_REPOSITORY_COVERAGE.
     */
    static final int MIN_REPOSITORY_COVERAGE = 10;

    /**
     * Per-candidate evidence request budget in ASYNC FULL-EVIDENCE mode.
     * Background jobs are not bound by the 50-request sync budget; this deeper
     * budget lets every candidate search ALL relevant evidence categories
     * before any skill is declared missing. Configurable via
     * FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE.
     */
    static final int FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE = 250;

    /**
     * Per-candidate analysis time cap in ASYNC FULL-EVIDENCE mode (ms).
     * Background jobs have no 60-second HTTP deadline, so the 15s sync cap is
     * replaced by a safe deep-analysis cap. Configurable via
     * FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS.
     */
    static final long FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS = 120_000;

    /** Maximum directory depth when browsing the repo tree for source files. */
    private static final int SOURCE_TREE_DEPTH = 3; // max descent levels for package exploration

    /**
     * Maximum root-level module directories probed per repository for nested
     * monorepo evidence (build files, config files, source roots).
     */
    private static final int MAX_NESTED_MODULE_DIRS = 6;

    /** Maximum subdirectories explored inside a module (depth-2 layouts, e.g. services/*). */
    private static final int MAX_NESTED_SUBMODULES = 2;

    /**
     * Maximum descent levels through package-root directories (com/org/dev/...)
     * to reach relevant packages (e.g. com/stschools/microservices/controller).
     */
    private static final int PACKAGE_ROOT_DESCENT_DEPTH = 4;

    /** Maximum package-root branches followed per level during source descent. */
    private static final int MAX_PACKAGE_ROOT_BRANCHES = 2;

    /** Directory names never treated as package roots during source descent. */
    private static final Set<String> NON_PACKAGE_DIRS = Set.of(
            "target", "build", "generated", "resources", "webapp", "node_modules", "test"
    );

    /** Directory names never treated as candidate modules (noise / build output / docs). */
    private static final Set<String> NON_MODULE_DIRS = Set.of(
            ".git", ".github", ".idea", ".vscode",
            "docs", "documentation", "scripts", "docker",
            "kubernetes", "k8s", "deploy", "infra", "terraform",
            "node_modules", "target", "build", "dist", "out",
            "venv", ".venv", "assets", "images", "img", "fonts"
    );

    /**
     * Root-level directory names already handled as standard source roots by
     * root discovery — excluded from nested module probing to avoid double work.
     */
    private static final Set<String> STANDARD_ROOT_TOP_DIRS = Set.of(
            "src", "backend", "app", "server", "api", "service", "services"
    );

    /** Substrings that make a directory name a likely project module. */
    private static final List<String> MODULE_SIGNAL_SUBSTRINGS = List.of(
            "service", "server", "backend", "frontend", "gateway", "eureka",
            "client", "core", "common", "config", "web", "app", "auth",
            "user", "module", "worker", "job", "consumer", "producer",
            "scheduler", "repo", "api", "model", "domain"
    );

    /**
     * Legacy exact-name files to look for during source discovery. Real-world
     * source files almost never carry these bare names (they are
     * AuthController.java, UserService.java, ...), so exact matching alone is
     * deliberately supplemented by the bounded suffix patterns in
     * {@link #SOURCE_FILE_SUFFIXES}. These exact names are still honored so
     * nothing that previously matched regresses — including non-Java entry
     * files (index.js, App.tsx, app.py) and Docker files that live at the
     * repository root.
     */
    private static final Set<String> LEGACY_EXACT_FILE_NAMES = Set.of(
            "Application.java", "Application.kt",
            "Controller.java", "RestController.java",
            "Service.java", "ServiceImpl.java",
            "Config.java", "Configuration.java",
            "Repository.java", "Mapper.java",
            "GatewayConfig.java", "GatewayRouteConfig.java",
            "Client.java", "FeignClient.java",
            "Main.java",
            "App.java", "index.js", "index.ts",
            "App.jsx", "App.tsx",
            "index.py", "app.py", "main.py",
            "Dockerfile", "docker-compose.yml", "docker-compose.yaml"
    );

    /**
     * Bounded suffix patterns that make a Java source file a high-signal
     * evidence candidate, ordered by signal strength (lower index = higher
     * priority). Matching is suffix-only and deliberately finite — arbitrary
     * {@code *.java} files are never crawled. *RestController.java files are
     * matched by the "Controller.java" entry (RestController ends with
     * Controller), and *FeignClient.java / *GatewayConfig.java are matched by
     * the "Client.java" / "Config.java" entries.
     *
     * <p>Selection only: a file name never proves a skill. The fetched CONTENT
     * still has to match {@link #sourcePatternMatches(String, String, String)}
     * (e.g. AuthController.java must actually contain @RestController before
     * REST API is credited, and *Service.java content must contain Eureka /
     * Feign / Gateway signals before Microservices is credited).
     */
    private static final List<String> SOURCE_FILE_SUFFIXES = List.of(
            "Controller.java",      // *Controller.java / *RestController.java
            "Resource.java",        // JAX-RS style *Resource.java endpoints
            "Application.java",     // Spring Boot entry points (ApiGatewayApplication.java)
            "Gateway.java",         // API gateway classes
            "Client.java",          // Feign / HTTP clients
            "Config.java",          // Spring configuration (RedisConfig.java, GatewayConfig.java)
            "Configuration.java",
            "Service.java",         // service-layer classes
            "ServiceImpl.java",
            "Repository.java",      // Spring Data repositories
            "Mapper.java",
            "Application.kt"        // Kotlin entry points
    );

    /** Rank below every suffix match, used for legacy exact-name files. */
    private static final int LEGACY_EXACT_RANK = SOURCE_FILE_SUFFIXES.size();

    /**
     * High-signal source-file name set used by the discovery call sites
     * ({@code SOURCE_FILE_NAMES.contains(name)}). Exact legacy names plus
     * bounded suffix-pattern matching, so real-world files such as
     * AuthController.java, UserRestController.java, ApiGatewayApplication.java,
     * RedisConfig.java or UserServiceImpl.java are selected everywhere — at the
     * repository root, inside {@code src/main/java}, during package descent and
     * inside nested module trees — while arbitrary {@code *.java} files are
     * never crawled.
     */
    private static final Set<String> SOURCE_FILE_NAMES = new SourceFilePatternSet();

    /**
     * Set whose {@code contains} is the exact-name OR bounded-suffix test from
     * {@link #isHighSignalSourceFile(String)}. Iteration/size expose only the
     * legacy exact names (the suffix space is unbounded by design); nothing in
     * the discovery code iterates the set.
     */
    private static final class SourceFilePatternSet extends java.util.AbstractSet<String> {
        @Override
        public boolean contains(Object o) {
            if (!(o instanceof String name)) return false;
            return LEGACY_EXACT_FILE_NAMES.contains(name) || sourceFileSuffixRank(name) >= 0;
        }

        @Override
        public java.util.Iterator<String> iterator() {
            return LEGACY_EXACT_FILE_NAMES.iterator();
        }

        @Override
        public int size() {
            return LEGACY_EXACT_FILE_NAMES.size();
        }
    }

    /**
     * Whether a file name is a high-signal source evidence candidate: either a
     * legacy exact name (Dockerfile, index.js, App.java, ...) or a file ending
     * with a recognized high-signal suffix (AuthController.java, OrderService.java,
     * ApiGatewayApplication.java, RedisConfig.java, ...).
     */
    static boolean isHighSignalSourceFile(String name) {
        if (name == null) return false;
        return LEGACY_EXACT_FILE_NAMES.contains(name) || sourceFileSuffixRank(name) >= 0;
    }

    /**
     * Index of the first matching high-signal suffix, or -1 when the name does
     * not end with any recognized suffix.
     */
    static int sourceFileSuffixRank(String name) {
        if (name == null) return -1;
        for (int i = 0; i < SOURCE_FILE_SUFFIXES.size(); i++) {
            if (name.endsWith(SOURCE_FILE_SUFFIXES.get(i))) return i;
        }
        return -1;
    }

    /**
     * Evidence priority of a source file name — lower is fetched first. Suffix
     * matches rank by {@link #SOURCE_FILE_SUFFIXES} order (controllers before
     * services); legacy exact-name files rank below all suffix matches;
     * non-candidates return {@link Integer#MAX_VALUE}.
     */
    static int sourceFilePriority(String name) {
        int rank = sourceFileSuffixRank(name);
        if (rank >= 0) return rank;
        return LEGACY_EXACT_FILE_NAMES.contains(name) ? LEGACY_EXACT_RANK : Integer.MAX_VALUE;
    }

    /** Java package directories that signal source code presence. */
    private static final Set<String> JAVA_SRC_PACKAGES = Set.of(
            "controller", "service", "config", "configuration",
            "repository", "model", "entity", "dto", "mapper",
            "filter", "security", "client", "gateway", "util", "utils"
    );

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");

    /**
     * Canonical skill → aliases used to detect it in the job description.
     * Ordered: required skills are reported in this dictionary order.
     */
    private static final Map<String, List<String>> SKILL_ALIASES = buildSkillAliases();

    /** Canonical skill → word-boundary regex over its aliases. */
    private static final Map<String, Pattern> SKILL_PATTERNS = buildSkillPatterns();

    private final RestClient githubClient;
    private final RestClient rawGithubClient;
    private final ObjectMapper objectMapper;

    /** Stored config for creating deadline-aware RestClient clones. */
    private final String githubServiceBaseUrl;
    private final String githubApiKey;

    /** Instance (environment-configurable) FULL-EVIDENCE deep budgets. */
    private final int fullEvidenceRequestBudget;
    private final long fullEvidenceAnalysisTimeMs;
    private final int fullEvidenceSourceFileCap;
    private final int minRepositoryCoverage;

    /**
     * Global deadline for an entire Job Match request. Covers all candidates,
     * evidence collection, scoring, and the optional AI step. Must be less than
     * the Gateway's 60-second recruiter timeout to leave margin for network
     * overhead, JSON serialization, and gateway processing.
     */
    static final long GLOBAL_MATCH_TIME_MS = 50_000;

    /**
     * Maximum elapsed time (ms) for evidence collection per candidate.
     * Capped dynamically to the remaining global time. This is a per-candidate
     * cap, not the normal budget — the global deadline always takes priority.
     */
    static final long MAX_EVIDENCE_TIME_MS_PER_CANDIDATE = 15_000;

    /**
     * Hard per-candidate evidence request budget. Secondary guard — the time
     * budget is the primary timeout safeguard.
     */
    static final int REQUEST_BUDGET_PER_CANDIDATE = 50;

    /**
     * Per-request mutable context for a single Job Match invocation.
     * Eliminates shared mutable state on the singleton {@code JobMatcherService}
     * so concurrent match requests are safe. Created once per {@code match()} call
     * and threaded through all candidate analysis, evidence collection, and AI steps.
     *
 * <p>All elapsed-time calculations use {@code System.nanoTime()} (monotonic)
     * rather than {@code System.currentTimeMillis()} (wall-clock, NTP-adjustable).
     */
    static class MatchContext {
        /** Absolute deadline in nanoTime — highest priority budget guard. */
        final long deadlineNanos;
        /** Start of current candidate's evidence collection (nanoTime). */
        long evidenceStartNanos;
        /** Remaining evidence requests for current candidate (mutable, decremented). */
        int evidenceRequestBudget;
        /**
         * Deep (async) mode: uses the FULL-EVIDENCE budgets instead of the
         * sync budgets. Deep mode also enforces the minimum repository
         * coverage rule. Budgets are instance-configurable
         * (environment overridable) so background jobs can run deeper than the
         * synchronous endpoint without any code change.
         */
        final boolean deep;
        /** Initial per-candidate request budget (mode dependent). */
        final int budgetCap;
        /** Per-repository source-file cap for this run. */
        final int sourceCap;
        /** Per-candidate evidence time cap (ms) for this run. */
        final long perCandidateTimeCapMs;
        /** Minimum repositories that must receive full evidence analysis. */
        final int minRepositoryCoverage;

        MatchContext(long deadlineNanos, int evidenceRequestBudget) {
            this(deadlineNanos, evidenceRequestBudget, false);
        }

        MatchContext(long deadlineNanos, int evidenceRequestBudget, boolean deep) {
            this(deadlineNanos, evidenceRequestBudget, deep,
                    deep ? FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO : MAX_SOURCE_FILES_PER_REPO,
                    deep ? FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS : MAX_EVIDENCE_TIME_MS_PER_CANDIDATE,
                    deep ? MIN_REPOSITORY_COVERAGE : Integer.MAX_VALUE);
        }

        MatchContext(long deadlineNanos, int evidenceRequestBudget, boolean deep,
                     int sourceFileCap, long perCandidateTimeCapMs, int minRepositoryCoverage) {
            this.deadlineNanos = deadlineNanos;
            this.evidenceRequestBudget = evidenceRequestBudget;
            this.budgetCap = evidenceRequestBudget;
            this.deep = deep;
            this.sourceCap = sourceFileCap;
            this.perCandidateTimeCapMs = perCandidateTimeCapMs;
            this.minRepositoryCoverage = minRepositoryCoverage;
        }

        /** Check if global deadline has been reached. */
        boolean isDeadlineReached() {
            return System.nanoTime() >= deadlineNanos;
        }

        /** Remaining time in milliseconds (min 0). */
        long remainingTimeMs() {
            return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
        }

        /**
         * Check if evidence budget is exhausted. Three independent guards:
         * 1. Global match deadline (highest priority — covers ALL candidates)
         * 2. Per-candidate evidence time limit (capped to remaining global time;
         *    deep/async mode uses the 120s full-evidence cap)
         * 3. Per-candidate request count (deep/async mode uses the 250-request
         *    full-evidence budget)
         *
         * If ANY guard triggers, optional evidence collection stops.
         */
        boolean evidenceBudgetExhausted() {
            long now = System.nanoTime();
            long remainingGlobalNanos = deadlineNanos - now;
            long perCandidateMaxNanos = TimeUnit.MILLISECONDS.toNanos(
                    Math.min(perCandidateTimeCapMs,
                            TimeUnit.NANOSECONDS.toMillis(remainingGlobalNanos)));
            return now >= deadlineNanos
                    || (now - evidenceStartNanos) > perCandidateMaxNanos
                    || evidenceRequestBudget <= 0;
        }

        /** Per-repository source-file cap for the current mode. */
        int sourceFileCap() {
            return sourceCap;
        }
    }

    /** Small per-process cache for public repository evidence to avoid repeated raw-file calls. */
    private final Map<String, String> evidenceCache = new ConcurrentHashMap<>();

    @Autowired
    public JobMatcherService(
            @Value("${app.github-service-url:http://localhost:8081}") String githubServiceUrl,
            @Value("${app.internal-api-key:}") String internalApiKey,
            @Value("${app.job-matching.max-evidence-repos:${JOB_MATCHING_MAX_EVIDENCE_REPOS:15}}") int maxEvidenceRepos,
            ObjectMapper objectMapper) {
        this(githubServiceUrl, internalApiKey, maxEvidenceRepos,
                FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE,
                FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS,
                FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO,
                MIN_REPOSITORY_COVERAGE,
                objectMapper);
    }

    /**
     * Full constructor with environment-configurable FULL-EVIDENCE budgets:
     * {@code FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE},
     * {@code FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS},
     * {@code FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO},
     * {@code MIN_REPOSITORY_COVERAGE}. Kept public so tests and ops tooling can
     * run deeper/looser background analysis without touching sync defaults.
     */
    public JobMatcherService(
            String githubServiceUrl,
            String internalApiKey,
            int maxEvidenceRepos,
            int fullEvidenceRequestBudget,
            long fullEvidenceAnalysisTimeMs,
            int fullEvidenceSourceFileCap,
            int minRepositoryCoverage,
            ObjectMapper objectMapper) {
        this.githubServiceBaseUrl = githubServiceUrl;
        this.githubApiKey = internalApiKey;
        this.githubClient = buildClient(githubServiceUrl, internalApiKey);
        this.rawGithubClient = buildRawGithubClient();
        this.objectMapper = objectMapper;
        this.maxEvidenceRepos = Math.min(maxEvidenceRepos, HARD_MAX_EVIDENCE_REPOS);
        this.fullEvidenceRequestBudget = Math.max(fullEvidenceRequestBudget,
                FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE);
        this.fullEvidenceAnalysisTimeMs = Math.max(fullEvidenceAnalysisTimeMs,
                FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS);
        this.fullEvidenceSourceFileCap = Math.max(fullEvidenceSourceFileCap,
                MAX_SOURCE_FILES_PER_REPO);
        this.minRepositoryCoverage = Math.max(minRepositoryCoverage, 1);
    }

    /** Package-private constructor for tests. */
    JobMatcherService(RestClient githubClient) {
        this.githubClient = githubClient;
        this.rawGithubClient = buildRawGithubClient();
        this.objectMapper = new ObjectMapper();
        this.maxEvidenceRepos = HARD_MAX_EVIDENCE_REPOS;
        this.githubServiceBaseUrl = "http://localhost:8081";
        this.githubApiKey = "";
        this.fullEvidenceRequestBudget = FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE;
        this.fullEvidenceAnalysisTimeMs = FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS;
        this.fullEvidenceSourceFileCap = FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO;
        this.minRepositoryCoverage = MIN_REPOSITORY_COVERAGE;
    }

    // ────────────────────────── Public API ──────────────────────────

    /**
     * Run the match: extract required skills from the JD and rank every
     * candidate by job fit (deterministic mode, no AI).
     */
    public JobMatchResponse match(String jdText, List<String> usernames, String source) {
        return match(jdText, usernames, source, false);
    }

    /**
     * Run the match. When {@code includeAi} is true, the deterministic results
     * are enriched with per-candidate Gemini explanations (best effort — a
     * missing API key or AI failure never breaks the deterministic result).
     */
    public JobMatchResponse match(String jdText, List<String> usernames, String source, boolean includeAi) {
        // Create per-request context with monotonic-clock deadline.
        // This context is local to this call and thread-safe: concurrent
        // match() invocations each get their own MatchContext.
        MatchContext ctx = new MatchContext(
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(GLOBAL_MATCH_TIME_MS),
                REQUEST_BUDGET_PER_CANDIDATE);
        return matchInternal(jdText, usernames, source, includeAi, ctx);
    }

    /**
     * Asynchronous match mode: processes ALL candidates without a global HTTP
     * deadline. Used by the async job-match worker — the 50s Gateway timeout is
     * irrelevant because this runs on a background thread.
     *
     * <p>Async mode is the PRIMARY deep path: it runs in FULL-EVIDENCE mode
     * with the deep budgets ({@link #FULL_EVIDENCE_REQUEST_BUDGET_PER_CANDIDATE}
     * requests, {@link #FULL_EVIDENCE_MAX_ANALYSIS_TIME_PER_CANDIDATE_MS} ms,
     * {@link #FULL_EVIDENCE_MAX_SOURCE_FILES_PER_REPO} source files per repo)
     * and enforces the minimum repository coverage rule
     * ({@link #MIN_REPOSITORY_COVERAGE} repos per candidate).
     */
    public JobMatchResponse matchAsync(String jdText, List<String> usernames, String source, boolean includeAi) {
        // Use Long.MAX_VALUE as deadline — effectively no global time limit.
        // Per-candidate evidence time and request budget still apply.
        MatchContext ctx = new MatchContext(
                Long.MAX_VALUE,
                fullEvidenceRequestBudget,
                true,
                fullEvidenceSourceFileCap,
                fullEvidenceAnalysisTimeMs,
                minRepositoryCoverage);
        return matchInternal(jdText, usernames, source, includeAi, ctx);
    }

    /**
     * Shared core logic for both sync and async matching. The only difference
     * is the {@code MatchContext} deadline: sync mode uses a 50s global
     * deadline; async mode uses {@code Long.MAX_VALUE}.
     */
    private JobMatchResponse matchInternal(String jdText, List<String> usernames, String source,
                                           boolean includeAi, MatchContext ctx) {
        List<String> required = extractRequiredSkills(jdText);
        List<JobMatchCandidate> results = new ArrayList<>();
        int failed = 0;

        for (String username : usernames) {
            // Check global deadline before each candidate (nanoTime — monotonic)
            if (ctx.isDeadlineReached()) {
                log.warn("Job match: global deadline reached after {} candidates, {} remaining skipped",
                        results.size(), usernames.size() - results.size() - failed);
                break;
            }
            try {
                results.add(analyzeCandidate(username, required, ctx));
            } catch (Exception e) {
                failed++;
                log.warn("Job match: failed to analyze candidate {}: {}", username, e.getMessage());
            }
        }

        results.sort(Comparator.comparingInt(JobMatchCandidate::matchScore).reversed());

        String jobTitle = inferJobTitle(jdText);
        JobMatchResponse base = new JobMatchResponse(jobTitle, required, source,
                usernames.size(), results.size(), failed, results, false, null, List.of());

        if (!includeAi || results.isEmpty()) {
            return base;
        }

        // Check global deadline before AI step — skip if insufficient time remains
        long aiTimeRemainingMs = ctx.remainingTimeMs();
        if (aiTimeRemainingMs < 5_000) {
            log.info("Job match: skipping AI explanations, {}ms remaining (need ≥5000ms)", aiTimeRemainingMs);
            return base;
        }

        try {
            AiMatchView ai = fetchAiExplanations(jobTitle, jdText, required, results, ctx);
            if (ai != null && ai.enabled() && ai.explanations() != null && !ai.explanations().isEmpty()) {
                Map<String, AiExplanationView> byUsername = ai.explanations().stream()
                        .filter(e -> e.username() != null)
                        .collect(Collectors.toMap(AiExplanationView::username, e -> e, (a, b) -> a));
                List<AiExplanation> explanations = mergeAiExplanations(results, byUsername);
                return new JobMatchResponse(jobTitle, required, source,
                        usernames.size(), results.size(), failed, results,
                        true, ai.model(), explanations);
            }
        } catch (Exception e) {
            log.warn("Job match: AI explanations unavailable: {}", e.getMessage());
        }
        return base;
    }

    /**
     * Merge AI explanations into the deterministic result order, attaching each
     * explanation to its candidate (candidates without one are skipped).
     */
    static List<AiExplanation> mergeAiExplanations(
            List<JobMatchCandidate> results, Map<String, AiExplanationView> byUsername) {
        List<AiExplanation> out = new ArrayList<>();
        for (JobMatchCandidate c : results) {
            AiExplanationView v = byUsername.get(c.username());
            if (v == null) continue;
            List<String> matched = c.matchedSkills() == null ? List.of() : c.matchedSkills();
            String explanation = nz(v.explanation(), "");
            // AI_CONTRADICTION GUARD: Gemini only explains deterministic results
            // and may never contradict them. If the AI claims a skill is missing
            // that the deterministic matchedSkills contains, the contradictory
            // text is dropped and the deterministic explanation is used instead.
            if (hasMissingClaim(explanation, matched)) {
                explanation = deterministicExplanation(matched, c.missingSkills());
            }
            List<String> gaps = v.gaps() == null ? List.of() : v.gaps();
            gaps = gaps.stream()
                    .filter(g -> !hasMissingClaim(g, matched))
                    .collect(Collectors.toList());
            out.add(new AiExplanation(
                    c.username(),
                    v.aiRank() != null ? v.aiRank() : 0,
                    nz(v.fitLabel(), "Partial fit"),
                    explanation,
                    v.strengths() == null ? List.of() : v.strengths(),
                    gaps,
                    nz(v.recommendation(), "")));
        }
        return out;
    }

    /**
     * Whether text contains an absence claim (missing/lacks/no experience...) for
     * any of the given skills that the deterministic matcher actually matched.
     * Matched skills are authoritative — an AI claim to the contrary is a
     * CONTRADICTION and is never surfaced to the user.
     */
    static boolean hasMissingClaim(String text, List<String> matchedSkills) {
        if (text == null || text.isBlank() || matchedSkills == null || matchedSkills.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : matchedSkills) {
            List<String> aliases = SKILL_ALIASES.get(skill);
            if (aliases == null) continue;
            for (String alias : aliases) {
                String a = alias.toLowerCase(Locale.ROOT);
                if (!Pattern.compile("(?<![a-z0-9])" + Pattern.quote(a) + "(?![a-z0-9])")
                        .matcher(lower).find()) {
                    continue;
                }
                // Absence claim patterns around the matched skill name.
                boolean absenceBefore = Pattern.compile(
                                "(?:no |missing |lacks |lack of |without |not familiar with |"
                                        + "does not have |doesn't have |never used |unfamiliar with |hasn't used |"
                                        + "no experience with |no exposure to |not used |not present)"
                                        + ".{0,60}" + Pattern.quote(a))
                        .matcher(lower).find();
                boolean absenceAfter = Pattern.compile(
                                Pattern.quote(a)
                                        + ".{0,60}(?:missing|absent|no experience|not present|not used|nowhere)")
                        .matcher(lower).find();
                if (absenceBefore || absenceAfter) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Deterministic fallback text when the AI contradicts deterministic matching. */
    static String deterministicExplanation(List<String> matched, List<String> missing) {
        String matchedPart = matched.isEmpty() ? "no repository evidence" : String.join(", ", matched);
        String missingPart = missing == null || missing.isEmpty()
                ? "none" : String.join(", ", missing);
        return "Skills verified from deterministic repository evidence: " + matchedPart
                + ". Unverified skills: " + missingPart + ".";
    }

    /**
     * Extract plain text from an uploaded job-description file.
     * Supports .txt, .md (UTF-8 text) and .pdf (PDFBox).
     *
     * @throws IllegalArgumentException for unsupported file types
     */
    public String extractText(String filename, byte[] bytes) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return readText(bytes);
        }
        if (lower.endsWith(".pdf")) {
            return extractPdfText(bytes);
        }
        throw new IllegalArgumentException(
                "Unsupported job description file type \"" + filename + "\". Use .txt, .md or .pdf.");
    }

    /** Read a UTF-8 text file (usernames CSV/TXT or plain JD). */
    public String readText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Parse a CSV/TXT of GitHub usernames — one per line and/or comma
     * separated, optional leading '@'. Invalid tokens are dropped.
     */
    public List<String> parseUsernames(String content) {
        if (content == null || content.isBlank()) return List.of();
        return java.util.Arrays.stream(content.split("[,\\r\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("@") ? s.substring(1) : s)
                .filter(s -> USERNAME_PATTERN.matcher(s).matches())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Extract the required tech skills from a job description using the
     * word-boundary-aware skill dictionary.
     */
    public List<String> extractRequiredSkills(String jdText) {
        if (jdText == null || jdText.isBlank()) return List.of();
        return SKILL_PATTERNS.entrySet().stream()
                .filter(e -> e.getValue().matcher(jdText).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** First non-blank, markdown-stripped line of the JD, capped at 80 chars. */
    public String inferJobTitle(String jdText) {
        if (jdText == null) return "Job Description";
        String line = jdText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");
        line = line.replaceAll("^[#*\\-\\s]+", "").trim();
        if (line.isEmpty()) return "Job Description";
        return line.length() > 80 ? line.substring(0, 80) : line;
    }

    /** Does the candidate's corpus contain the given canonical skill? */
    boolean matches(String corpus, String canonicalSkill) {
        if (corpus == null || corpus.isBlank()) return false;
        Pattern p = SKILL_PATTERNS.get(canonicalSkill);
        return p != null && p.matcher(corpus).find();
    }

    /** 60% skill match + 40% developer score, clamped 0-100. */
    static int computeMatchScore(int skillMatchPercent, int developerScore) {
        return Math.max(0, Math.min(100, (int) Math.round(0.6 * skillMatchPercent + 0.4 * developerScore)));
    }

    // ────────────────────────── AI step ──────────────────────────

    private AiMatchView fetchAiExplanations(
            String jobTitle, String jdText, List<String> required, List<JobMatchCandidate> results,
            MatchContext ctx) {
        List<JobMatchCandidate> top = results.stream().limit(AI_CANDIDATE_LIMIT).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobTitle", jobTitle);
        body.put("jobDescription", truncate(jdText, MAX_JOB_DESCRIPTION_CHARS));
        body.put("requiredSkills", required);
        body.put("candidates", top.stream()
                .map(c -> Map.<String, Object>of(
                        "username", c.username(),
                        "name", nz(c.name(), ""),
                        "bio", nz(c.bio(), ""),
                        "developerScore", c.developerScore(),
                        "level", nz(c.level(), ""),
                        "languages", c.languages(),
                        "matchedSkills", c.matchedSkills(),
                        "missingSkills", c.missingSkills(),
                        "topRepos", c.topRepos()))
                .toList());

        // AI request uses dynamic timeout based on remaining global time
        RestClient aiClient = dynamicTimeoutClient(ctx.remainingTimeMs(), 5_000, 20_000);
        ApiResponse<AiMatchView> response = aiClient.post()
                .uri("/api/ai/job-match")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AiMatchView>>() {});
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return null;
        }
        return response.getData();
    }

    // ────────────────────────── Internals ──────────────────────────

    private JobMatchCandidate analyzeCandidate(String username, List<String> required, MatchContext ctx) {
        long start = System.currentTimeMillis();
        ctx.evidenceRequestBudget = ctx.budgetCap;
        ctx.evidenceStartNanos = System.nanoTime();

        // Each API call uses a dynamic timeout based on remaining global time.
        // If the deadline is reached between calls, subsequent calls return null/empty.
        long remainingMs = ctx.remainingTimeMs();
        ScoreView score = fetch("/api/github/{u}/score", username, ScoreView.class, remainingMs);
        ProfileView profile = fetch("/api/github/profile/{u}", username, ProfileView.class, remainingMs);
        List<LanguageView> languages = fetchList("/api/github/{u}/languages/weighted", username, LanguageView.class, remainingMs);
        List<RepoView> repos = fetchList("/api/github/{u}/repos", username, RepoView.class, remainingMs);

        List<String> topRepos = repos.stream()
                .sorted(Comparator.comparingInt(RepoView::stars).reversed())
                .limit(5)
                .map(RepoView::name)
                .collect(Collectors.toList());

        List<String> topLanguages = languages.stream()
                .sorted(Comparator.comparingDouble(LanguageView::percentage).reversed())
                .limit(8)
                .map(LanguageView::language)
                .collect(Collectors.toList());

        log.debug("JobMatch candidate={} profileOk=true languages={} reposReturned={}",
                username, languages.size(), repos.size());

        EvidenceStats stats = new EvidenceStats();
        EvidenceTracker tracker = new EvidenceTracker();
        String corpus = buildCandidateCorpus(username, profile, languages, repos, required, stats, ctx, tracker);

        // ── Platform-level Git evidence: a GitHub-hosted repository IS evidence of Git ──
        if (!repos.isEmpty()) {
            corpus = corpus + " git_source:github_repository";
        }

        final String finalCorpus = corpus;
        List<String> matched = required.stream().filter(s -> matches(finalCorpus, s)).collect(Collectors.toList());
        List<String> missing = required.stream().filter(s -> !matched.contains(s)).collect(Collectors.toList());

        int skillMatchPercent = required.isEmpty() ? 100
                : (int) Math.round(matched.size() * 100.0 / required.size());
        // Null-safe: if deadline was reached during API calls, use defaults
        int developerScore = score != null ? score.overallScore() : 0;
        String level = score != null ? score.level() : "Unknown";

        long elapsed = System.currentTimeMillis() - start;
        log.info("JobMatch candidate={} reposReturned={} evidenceRepos={}/{} " +
                        "evidenceFilesOk={}/{} reposWithEvidence={} sourceFilesOk={}/{} " +
                        "sourceFilesDiscovered={} sourceEvidenceSkills={} matchedSkills={} missingSkills={} durationMs={}",
                username, repos.size(), stats.reposWithEvidence, stats.reposAttempted,
                stats.filesFound, stats.filesFound + stats.filesMissing,
                stats.reposWithEvidence,
                stats.sourceFilesFound, stats.sourceFilesFound + stats.sourceFilesMissing,
                stats.sourceFilesDiscovered, stats.sourceEvidenceSkills,
                matched, missing, elapsed);

        return new JobMatchCandidate(username,
                profile != null ? profile.name() : username,
                profile != null ? profile.avatarUrl() : null,
                profile != null ? profile.bio() : null,
                developerScore, level, computeMatchScore(skillMatchPercent, developerScore),
                skillMatchPercent, matched, missing, topLanguages, topRepos,
                tracker.views());
    }

    /**
     * Language → ecosystem mapping for exploration eligibility.
     * A repo whose language is in this set is considered a plausible
     * candidate for the corresponding ecosystem, even if its metadata
     * keywords don't match required skills.
     */
    private static final Map<String, Set<String>> LANGUAGE_ECOSYSTEMS;
    static {
        Map<String, Set<String>> m = new java.util.LinkedHashMap<>();
        m.put("Java", Set.of("java", "spring", "hibernate", "microservice", "rest api", "quarkus", "micronaut"));
        m.put("JavaScript", Set.of("javascript", "node.js", "nodejs", "react", "vue", "angular", "express"));
        m.put("TypeScript", Set.of("javascript", "typescript", "node.js", "nodejs", "react", "vue", "angular", "next.js"));
        m.put("Python", Set.of("python", "django", "flask", "fastapi", "machine learning", "data science"));
        m.put("Go", Set.of("go", "golang", "kubernetes", "docker", "microservice"));
        m.put("Rust", Set.of("rust", "systems"));
        m.put("Ruby", Set.of("ruby", "rails"));
        m.put("PHP", Set.of("php", "laravel"));
        m.put("C#", Set.of("c#", ".net", "asp.net"));
        m.put("Kotlin", Set.of("kotlin", "java", "spring", "android"));
        m.put("Swift", Set.of("swift", "ios"));
        m.put("Shell", Set.of("docker", "ci/cd", "devops", "bash"));
        m.put("Dockerfile", Set.of("docker", "ci/cd", "devops"));
        m.put("HTML", Set.of("react", "vue", "angular", "frontend"));
        LANGUAGE_ECOSYSTEMS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Check whether a repository's language makes it an ecosystem-compatible
     * candidate for any of the required skills.
     */
    private static boolean isEcosystemCompatible(RepoView repo, Set<String> normalizedRequired) {
        if (repo == null || repo.language() == null) return false;
        Set<String> ecosystemSkills = LANGUAGE_ECOSYSTEMS.get(repo.language());
        if (ecosystemSkills == null) return false;
        return normalizedRequired.stream().anyMatch(ecosystemSkills::contains);
    }

    /**
     * Check whether a repository has technical/project signals (non-trivial
     * code: has description, topics, stars, or is a known project pattern).
     */
    private static boolean hasProjectSignals(RepoView repo) {
        if (repo == null) return false;
        if (repo.stars() > 0) return true;
        if (repo.description() != null && !repo.description().isBlank()) return true;
        if (repo.topics() != null && !repo.topics().isEmpty()) return true;
        return false;
    }

    /**
     * Build a deterministic skill corpus from public GitHub evidence.
     *
     * <p>Repository selection uses a TWO-STAGE strategy:
     * <ol>
     *   <li><b>Metadata relevance:</b> repos ranked by keyword match to required skills</li>
     *   <li><b>Exploration quota:</b> reserve {@link #EXPLORATION_SLOTS} slots for
     *       ecosystem-compatible repos with weak metadata but strong project signals</li>
     * </ol>
     *
     * <p>Exploration slots ensure a generic Java repo with @RestController source
     * code is not excluded solely because its name/description doesn't mention REST.
     */
    private String buildCandidateCorpus(
            String username,
            ProfileView profile,
            List<LanguageView> languages,
            List<RepoView> repos,
            List<String> required,
            EvidenceStats stats,
            MatchContext ctx,
            EvidenceTracker tracker) {

        StringBuilder sb = new StringBuilder(32_000);

        if (profile != null && profile.bio() != null) {
            sb.append(profile.bio()).append(' ');
        }

        for (LanguageView l : languages) {
            if (l != null && l.language() != null) {
                sb.append(l.language()).append(' ');
            }
        }

        // ── Two-stage repository selection ──
        Set<String> normalizedRequired = required == null ? Set.of()
                : required.stream().filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        List<RepoView> selected = new ArrayList<>(selectEvidenceRepos(repos, required, normalizedRequired));
        stats.reposAttempted = selected.size();

        log.debug("JobMatch candidate={} reposReturned={} metadataPool={} evidenceRepos={}",
                username, repos.size(), repos.size(), selected.size());

        // Track which required skills are already confirmed by evidence seen so
        // far. This NEVER stops analysis (minimum coverage is enforced first);
        // it only guides which remaining repositories are inspected next.
        Set<String> confirmedSkills = new HashSet<>();

        int coverage = Math.min(ctx.minRepositoryCoverage, selected.size());

        for (int i = 0; i < selected.size(); i++) {
            if (ctx.evidenceBudgetExhausted()) {
                log.info("JobMatch candidate={} evidence budget exhausted at repo {}/{}",
                        username, i, selected.size());
                break;
            }

            // After the minimum coverage is complete, if required skills remain
            // unresolved, rank the remaining repositories specifically for those
            // unresolved skills (repos 11-15 targeted search).
            if (i == coverage && !confirmedSkills.containsAll(required)
                    && i < selected.size()) {
                List<String> unresolved = unresolvedSkills(required, confirmedSkills); // re-rank driver
                List<RepoView> remaining = selected.subList(i, selected.size());
                remaining.sort(Comparator
                        .comparingInt((RepoView r) -> -computeRepoRelevance(r, unresolved))
                        .thenComparingInt(RepoView::stars)
                        .reversed());
                log.debug("JobMatch candidate={} unresolvedSkills={} re-ranked repos {}+ for targeted search",
                        username, unresolved, i + 1);
            }

            RepoView r = selected.get(i);
            sb.append(r.name()).append(' ');
            if (r.description() != null) sb.append(r.description()).append(' ');
            if (r.language() != null) sb.append(r.language()).append(' ');
            if (r.topics() != null) sb.append(String.join(" ", r.topics())).append(' ');

            // Repository metadata is LOW-confidence evidence (name/description/topics).
            if (required != null) {
                String repoMeta = (r.description() == null ? "" : r.description())
                        + ' ' + (r.language() == null ? "" : r.language())
                        + ' ' + (r.topics() == null ? "" : String.join(" ", r.topics()));
                recordChunkEvidence(repoMeta, r.name(), "-", "-", "-",
                        "METADATA", EvidenceTracker.LOW, required, confirmedSkills, tracker);
            }

            EvidenceResult er = fetchRepositoryEvidence(
                    username, r.name(), r.defaultBranch(), required, stats, ctx, confirmedSkills, tracker);
            stats.reposFullyAnalyzed++;
            if (!er.content.isBlank()) {
                sb.append(' ').append(er.content).append(' ');
                stats.reposWithEvidence++;
            }
            stats.filesFound += er.filesFound;
            stats.filesMissing += er.filesMissing;
        }

        log.info("JobMatch candidate={} reposReturned={} reposSelected={} reposFullyAnalyzed={} " +
                        "documentsInspected={} buildFilesInspected={} configFilesInspected={} " +
                        "modulesDiscovered={} sourceRootsDiscovered={} sourceFilesFetched={} skillsConfirmed={}",
                username, repos.size(), selected.size(), stats.reposFullyAnalyzed,
                stats.documentsInspected, stats.buildFilesInspected, stats.configFilesInspected,
                stats.modulesDiscovered, stats.sourceRootsDiscovered, stats.sourceFilesFound,
                new ArrayList<>(confirmedSkills));

        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Required skills not yet confirmed by candidate evidence. */
    private static List<String> unresolvedSkills(List<String> required, Set<String> confirmedSkills) {
        if (required == null || required.isEmpty()) return List.of();
        return required.stream().filter(s -> !confirmedSkills.contains(s)).collect(Collectors.toList());
    }

    /**
     * Record evidence for every required skill that this evidence chunk proves
     * (chunk-level {@link #matches}). Recording is idempotent: the tracker keeps
     * the highest-confidence record per skill, and {@code confirmedSkills} is
     * only ever additive.
     */
    private static void recordChunkEvidence(
            String chunk,
            String repository,
            String module,
            String file,
            String branch,
            String evidenceType,
            int confidenceRank,
            List<String> required,
            Set<String> confirmedSkills,
            EvidenceTracker tracker) {
        if (chunk == null || chunk.isBlank() || required == null) return;
        String lower = chunk.toLowerCase(Locale.ROOT);
        for (String skill : required) {
            if (matchesStatic(lower, skill)) {
                confirmedSkills.add(skill);
                tracker.record(skill, confidenceRank, repository, module, file, branch, evidenceType);
            }
        }
    }

    /**
     * Record evidence found inside a nested module build/config file, with the
     * module path kept in the evidence record, and classify the file for the
     * diagnostic counters (build vs configuration).
     */
    private static void recordModuleEvidence(
            String content, String repo, String module, String file, String branch,
            List<String> required, EvidenceStats stats,
            Set<String> confirmedSkills, EvidenceTracker tracker) {
        if (content == null || content.isBlank()) return;
        String lower = file == null ? "" : file.toLowerCase(Locale.ROOT);
        if (lower.startsWith("application.") || lower.startsWith("application-") || lower.equals("bootstrap.yml")) {
            stats.configFilesInspected++;
            recordChunkEvidence(content, repo, module, file, branch,
                    "CONFIGURATION", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
        } else {
            stats.buildFilesInspected++;
            recordChunkEvidence(content, repo, module, file, branch,
                    "BUILD", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
        }
    }

    /** Static {@link #matches} variant usable from static helpers. */
    static boolean matchesStatic(String lowerCorpus, String canonicalSkill) {
        if (lowerCorpus == null || lowerCorpus.isBlank()) return false;
        Pattern p = SKILL_PATTERNS.get(canonicalSkill);
        return p != null && p.matcher(lowerCorpus).find();
    }

    /**
     * Slots reserved for exploration: repos with weak metadata but strong
     * ecosystem/project signals. Ensures generic repos with relevant source
     * code are not excluded solely because their name/description lacks keywords.
     */
    static final int EXPLORATION_SLOTS = 5;

    /**
     * Two-stage repository selection.
     *
     * <p>Stage 1: Score all repos by metadata keyword relevance.
     * <p>Stage 2: Fill up to {@link #maxEvidenceRepos} slots. Majority from
     * highest metadata relevance; remaining from ecosystem-compatible repos
     * with project signals (deterministic — no randomness).
     *
     * @return selected repos, never exceeding maxEvidenceRepos
     */
    static List<RepoView> selectEvidenceRepos(
            List<RepoView> allRepos, List<String> required, Set<String> normalizedRequired) {
        if (allRepos == null || allRepos.isEmpty()) return List.of();
        List<RepoView> nonNull = allRepos.stream().filter(Objects::nonNull).toList();
        if (nonNull.isEmpty()) return List.of();

        // Stage 1: score all repos by metadata keyword relevance
        List<RepoView> ranked = nonNull.stream()
                .sorted(Comparator
                        .comparingInt((RepoView r) -> -computeRepoRelevance(r, required))
                        .thenComparingInt(RepoView::stars)
                        .reversed())
                .toList();

        int limit = Math.min(nonNull.size(), HARD_MAX_EVIDENCE_REPOS);

        // Stage 2: select top metadata-relevant, fill remaining with exploration
        int metadataSlots = Math.max(1, limit - EXPLORATION_SLOTS);
        Set<String> selectedNames = new LinkedHashSet<>();
        List<RepoView> selected = new ArrayList<>();

        // Majority: highest metadata relevance
        for (RepoView r : ranked) {
            if (selected.size() >= metadataSlots) break;
            if (selectedNames.add(r.name())) selected.add(r);
        }

        // Exploration: ecosystem-compatible repos with project signals, not already selected
        for (RepoView r : ranked) {
            if (selected.size() >= limit) break;
            if (selectedNames.contains(r.name())) continue;
            if (isEcosystemCompatible(r, normalizedRequired) && hasProjectSignals(r)) {
                if (selectedNames.add(r.name())) selected.add(r);
            }
        }

        // If still under limit, fill with remaining ranked repos
        for (RepoView r : ranked) {
            if (selected.size() >= limit) break;
            if (selectedNames.add(r.name())) selected.add(r);
        }

        return List.copyOf(selected);
    }

    /**
     * Fetch a single object from github-service with a dynamic timeout that
     * respects the remaining global match deadline. The socket timeout ensures
     * the HTTP request cannot outlive the deadline.
     */
    private <T> T fetch(String path, String username, Class<T> type, long remainingMs) {
        if (remainingMs <= 0) return null;

        RestClient client = dynamicTimeoutClient(remainingMs, 5_000, 20_000);
        ApiResponse<?> response = client.get()
                .uri(path, username)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<Object>>() {});

        if (response == null || !response.isSuccess() || response.getData() == null) {
            return null;
        }

        return objectMapper.convertValue(response.getData(), type);
    }

    /**
     * Fetch a list from github-service with a dynamic timeout.
     */
    private <T> List<T> fetchList(String path, String username, Class<T> elementType, long remainingMs) {
        if (remainingMs <= 0) return List.of();

        RestClient client = dynamicTimeoutClient(remainingMs, 5_000, 20_000);
        ApiResponse<?> response = client.get()
                .uri(path, username)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<Object>>() {});

        if (response == null || !response.isSuccess() || response.getData() == null) {
            return List.of();
        }

        return objectMapper.convertValue(
                response.getData(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)
        );
    }

    /**
     * Score how relevant a repository is to the required skills based on
     * its name, description, primary language, and topics.
     * Higher score = more relevant = inspected first for evidence.
     */
    static int computeRepoRelevance(RepoView repo, List<String> required) {
        if (required == null || required.isEmpty() || repo == null) return 0;
        int score = 0;
        Set<String> keywords = new java.util.HashSet<>();
        if (repo.name() != null) keywords.add(repo.name().toLowerCase(Locale.ROOT));
        if (repo.description() != null) keywords.add(repo.description().toLowerCase(Locale.ROOT));
        if (repo.language() != null) keywords.add(repo.language().toLowerCase(Locale.ROOT));
        if (repo.topics() != null) {
            for (String t : repo.topics()) {
                if (t != null) keywords.add(t.toLowerCase(Locale.ROOT));
            }
        }
        String joined = String.join(" ", keywords);
        for (String skill : required) {
            Pattern p = SKILL_PATTERNS.get(skill);
            if (p != null && p.matcher(joined).find()) {
                score++;
            }
        }
        return score;
    }

    /**
     * High-signal files to inspect for technology detection, returned in
     * progressive priority order. The caller fetches files sequentially and
     * stops early once all required skills are covered.
     *
     * <p>Order ensures the highest-signal file is tried first:
     * <ul>
     *   <li>README first (broad evidence for all skills)</li>
     *   <li>Primary build file (pom.xml / package.json / Dockerfile)</li>
     *   <li>Fallback build files only if primary is missing</li>
     *   <li>Configuration files only if still needed</li>
     * </ul>
     */
    /** Documentation files inspected for every repository (MEDIUM confidence). */
    static final List<String> DOCUMENTATION_FILES = List.of(
            "README.md", "README", "ARCHITECTURE.md", "DESIGN.md", "API.md");

    /** Java build/dependency files (HIGH confidence build evidence). */
    static final List<String> JAVA_BUILD_FILES = List.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "gradle.properties");

    /** Spring configuration files (HIGH confidence configuration evidence). */
    static final List<String> SPRING_CONFIG_FILES = List.of(
            "application.yml", "application.yaml", "application.properties", "bootstrap.yml");

    /** Container/deployment files (HIGH confidence build/deploy evidence). */
    static final List<String> DEPLOYMENT_FILES = List.of(
            "Dockerfile", "docker-compose.yml", "docker-compose.yaml", "docker-compose.prod.yml");

    /** CI/CD files (MEDIUM confidence when present at root). */
    static final List<String> CI_FILES = List.of("Jenkinsfile", ".gitlab-ci.yml");

    /**
     * Bounded directory probes for evidence categories that live in
     * subdirectories (CI workflows, docs, Kubernetes/deploy manifests).
     * Each probe is a single Contents API call plus at most
     * {@link #MAX_PROBED_FILES_PER_DIR} file fetches.
     */
    static final List<String> EVIDENCE_PROBE_DIRS = List.of(
            ".github/workflows", "docs", "k8s", "deploy", "manifests");

    /** Maximum files fetched per probed evidence directory. */
    static final int MAX_PROBED_FILES_PER_DIR = 2;

    /**
     * High-signal files to inspect for technology detection, returned in
     * progressive priority order. Covers ALL relevant evidence categories:
     * documentation, build/dependency files, configuration files, Docker/
     * deployment files, and CI/CD files. Fetching is budget-aware — the
     * per-candidate request/time budget (not skill confirmation) bounds how
     * far down this list analysis proceeds.
     */
    static List<String> evidenceFilesFor(List<String> required) {
        Set<String> normalized = required == null
                ? Set.of()
                : required.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // With no required skills there is nothing to search for — keep the
        // historical README-only behavior (nothing else is ever consulted).
        if (normalized.isEmpty()) {
            return List.of("README.md");
        }

        List<String> files = new ArrayList<>();
        files.addAll(DOCUMENTATION_FILES);

        boolean javaEcosystem = normalized.stream().anyMatch(s ->
                s.contains("java") ||
                        s.contains("spring") ||
                        s.contains("hibernate") ||
                        s.contains("microservice") ||
                        s.contains("rest api") ||
                        s.contains("quarkus") ||
                        s.contains("micronaut"));

        boolean javascriptEcosystem = normalized.stream().anyMatch(s ->
                s.contains("javascript") ||
                        s.contains("typescript") ||
                        s.equals("react") ||
                        s.contains("node.js") ||
                        s.contains("nodejs") ||
                        s.contains("express"));

        boolean pythonEcosystem = normalized.stream().anyMatch(s ->
                s.contains("python") || s.contains("django") || s.contains("flask") ||
                        s.contains("fastapi") || s.contains("machine learning") ||
                        s.contains("data science") || s.contains("pandas") ||
                        s.contains("tensorflow") || s.contains("pytorch"));

        boolean dockerEcosystem = normalized.stream().anyMatch(s ->
                s.contains("docker") ||
                        s.contains("kubernetes") ||
                        s.contains("ci/cd"));

        boolean ciEcosystem = normalized.stream().anyMatch(s ->
                s.contains("ci/cd") || s.contains("jenkins") ||
                        s.contains("github actions") || s.contains("gitlab") ||
                        s.contains("deployment") || s.contains("devops"));

        if (javaEcosystem) {
            // Build/dependency files first, then configuration files
            files.addAll(JAVA_BUILD_FILES);
            files.addAll(SPRING_CONFIG_FILES);
        }

        if (javascriptEcosystem) {
            files.add("package.json");
        }

        if (pythonEcosystem) {
            files.add("requirements.txt");
            files.add("pyproject.toml");
            files.add("setup.py");
        }

        if (dockerEcosystem) {
            files.addAll(DEPLOYMENT_FILES);
        }

        if (ciEcosystem) {
            files.addAll(CI_FILES);
        }

        return List.copyOf(files);
    }

    private record EvidenceResult(String content, String branchUsed, int filesFound, int filesMissing) {}

    private static class EvidenceStats {
        int reposAttempted;
        int reposWithEvidence;
        int reposFullyAnalyzed;
        int filesFound;
        int filesMissing;
        int documentsInspected;
        int buildFilesInspected;
        int configFilesInspected;
        int modulesDiscovered;
        int sourceRootsDiscovered;
        int sourceFilesDiscovered;
        int sourceFilesFound;
        int sourceFilesMissing;
        List<String> sourceEvidenceSkills = new ArrayList<>();
    }

    /**
     * Per-candidate, per-skill evidence records. Each skill keeps the
     * highest-confidence evidence that proved it (HIGH = source/build/config,
     * MEDIUM = documentation, LOW = metadata). Candidate state is fully
     * isolated: a fresh tracker is created per candidate and never shared.
     */
    static final class EvidenceTracker {
        static final int HIGH = 3;
        static final int MEDIUM = 2;
        static final int LOW = 1;

        private static final String[] CONFIDENCE_NAMES = {"LOW", "MEDIUM", "HIGH"};

        private final Map<String, JobMatchResponse.SkillEvidenceView> bySkill = new LinkedHashMap<>();
        private final Map<String, Integer> bySkillRank = new java.util.HashMap<>();

        void record(String skill, int confidenceRank, String repository, String module,
                    String file, String branch, String evidenceType) {
            if (skill == null) return;
            Integer existing = bySkillRank.get(skill);
            if (existing != null && existing >= confidenceRank) {
                return; // keep the highest-confidence evidence already recorded
            }
            bySkill.put(skill, new JobMatchResponse.SkillEvidenceView(
                    skill,
                    CONFIDENCE_NAMES[Math.max(0, Math.min(2, confidenceRank - 1))],
                    repository == null ? "-" : repository,
                    module == null ? "-" : module,
                    file == null ? "-" : file,
                    branch == null ? "-" : branch,
                    evidenceType == null ? "METADATA" : evidenceType,
                    "-"));
            bySkillRank.put(skill, confidenceRank);
        }

        /** Snapshot of recorded evidence in skill order. */
        List<JobMatchResponse.SkillEvidenceView> views() {
            return List.copyOf(bySkill.values());
        }
    }

    private record SourceEvidenceResult(String content, int filesFetched, int filesFailed, List<String> skillsDetected) {}

    /**
     * Check if the evidence budget is exhausted. Three independent guards:
     * 1. Global match deadline (highest priority — covers ALL candidates)
     * 2. Per-candidate evidence time limit
     * 3. Per-candidate request count
     *
     * If ANY guard triggers, optional evidence collection stops.
     */
    /**
     * Budget-aware file fetch. Decrements the per-candidate request budget and
     * returns null if the budget (count or time) is exhausted.
     * Package-private so tests can override it with a canned file provider and
     * exercise the REAL discovery → selection → fetch → detection pipeline.
     */
    FileResult budgetedFetch(String owner, String repo, String branch, String file, MatchContext ctx) {
        if (ctx.evidenceBudgetExhausted()) return null;
        ctx.evidenceRequestBudget--;
        return fetchRawRepositoryFile(owner, repo, branch, file, ctx);
    }

    /**
     * Budget-aware GitHub Contents API call for source/build discovery.
     * Returns null if the budget (count or time) is exhausted, the directory
     * does not exist, or github-service is unreachable. The path is sent as a
     * query parameter so nested paths (e.g. "api-gateway/src/main/java")
     * survive URL encoding intact and the github-service endpoint can forward
     * them to the GitHub Contents API as a literal path.
     */
    List<Map<String, Object>> budgetedDirFetch(String owner, String repo, String branch, String path, MatchContext ctx) {
        if (ctx.evidenceBudgetExhausted()) return null;
        ctx.evidenceRequestBudget--;
        try {
            RestClient client = dynamicTimeoutClient(ctx.remainingTimeMs(), 5_000, 20_000);
            ApiResponse<?> response = client.get()
                    .uri("/api/github/{owner}/{repo}/contents?path={path}&ref={branch}", owner, repo, path, branch)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Object>>() {});
            if (response == null || !response.isSuccess() || response.getData() == null) {
                return null;
            }
            return objectMapper.convertValue(
                    response.getData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch evidence for a single repository across ALL relevant evidence
     * categories: documentation, build/dependency files, configuration files,
     * Docker/deployment files, CI/CD files, bounded subdirectory probes
     * (.github/workflows, docs, k8s, deploy, manifests), nested monorepo
     * modules, and high-signal source files.
     *
     * <p>FULL-EVIDENCE RULE: evidence collection NEVER stops just because every
     * currently visible skill is confirmed. The only stops are the per-candidate
     * request/time budgets and the per-repository evidence size cap. A skill
     * found in repo 1 must not prevent repos 2-15 from being inspected.
     *
     * <p>Cache design: NO repo-level aggregate cache. Each evidence file is cached
     * independently at the file level (owner/repo/branch/file).
     */
    private EvidenceResult fetchRepositoryEvidence(String owner, String repo, String defaultBranch,
                                                   List<String> required, EvidenceStats stats,
                                                   MatchContext ctx, Set<String> confirmedSkills,
                                                   EvidenceTracker tracker) {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            return new EvidenceResult("", "", 0, 0);
        }

        List<String> branches = buildBranchPriority(defaultBranch);
        StringBuilder combinedEvidence = new StringBuilder();
        String branchUsed = "";
        int totalFound = 0;
        int totalMissing = 0;
        List<String> branchesChecked = new ArrayList<>();

        for (String branch : branches) {
            if (ctx.evidenceBudgetExhausted()) break;

            branchesChecked.add(branch);
            StringBuilder branchEvidence = new StringBuilder();
            int found = 0;
            int missing = 0;
            int skillsBefore = confirmedSkills.size();

            // ── 1) Documentation + build + config + docker + CI files (full categories) ──
            // No early stopping on confirmed skills: every evidence category is
            // inspected while the budget allows.
            List<String> evidenceFiles = evidenceFilesFor(required);
            for (String file : evidenceFiles) {
                if (ctx.evidenceBudgetExhausted()) break;

                FileResult fr = budgetedFetch(owner, repo, branch, file, ctx);
                if (fr == null) break; // budget exhausted

                if (!fr.content.isBlank()) {
                    branchEvidence.append("\n[file ")
                            .append(file)
                            .append("]\n")
                            .append(fr.content)
                            .append('\n');
                    found++;
                    recordEvidenceFile(file, fr.content, owner, repo, branch,
                            required, stats, confirmedSkills, tracker);
                } else {
                    missing++;
                }
            }

            // ── 2) Bounded subdirectory probes (CI workflows, docs, k8s/deploy) ──
            if (!ctx.evidenceBudgetExhausted()) {
                probeEvidenceDirectories(owner, repo, branch, required, stats, ctx,
                        branchEvidence, confirmedSkills, tracker);
            }

            // ── 3) Nested module discovery (monorepos) ──
            if (!required.isEmpty() && !ctx.evidenceBudgetExhausted()) {
                NestedModuleResult nmr = fetchNestedModuleEvidence(
                        owner, repo, branch, required, stats, ctx, confirmedSkills, tracker);
                if (!nmr.content.isBlank()) {
                    branchEvidence.append("\n[nested-modules]\n").append(nmr.content).append('\n');
                    found += nmr.buildFilesFound() + nmr.sourceFilesFetched();
                }
            }

            // ── 4) Root source discovery ──
            if (!required.isEmpty() && !ctx.evidenceBudgetExhausted()) {
                SourceEvidenceResult ser = fetchSourceEvidence(
                        owner, repo, branch, required, stats, ctx, confirmedSkills, tracker);
                if (!ser.content.isBlank()) {
                    branchEvidence.append("\n[source-evidence]\n")
                            .append(ser.content)
                            .append('\n');
                    found += ser.filesFetched;
                    stats.sourceFilesDiscovered += ser.filesFetched + ser.filesFailed;
                    stats.sourceFilesFound += ser.filesFetched;
                    stats.sourceFilesMissing += ser.filesFailed;
                    stats.sourceEvidenceSkills.addAll(ser.skillsDetected);
                }
            }

            // Accumulate evidence from this branch
            if (!branchEvidence.isEmpty()) {
                combinedEvidence.append(branchEvidence);
                if (branchUsed.isEmpty()) branchUsed = branch;
            }
            totalFound += found;
            totalMissing += missing;

            log.debug("repo={}/{} branch={} skillsBefore={} skillsAfter={} found={} missing={}",
                    owner, repo, branch, skillsBefore, confirmedSkills.size(), found, missing);
        }

        String bestEvidence = combinedEvidence.toString();
        if (bestEvidence.length() > MAX_TOTAL_EVIDENCE_PER_REPO) {
            bestEvidence = bestEvidence.substring(0, MAX_TOTAL_EVIDENCE_PER_REPO);
        }

        log.debug("repo={} defaultBranch={} branchesChecked={} branchUsed={} evidenceFilesFound={} evidenceFilesMissing={}",
                owner + "/" + repo, defaultBranch, branchesChecked, branchUsed, totalFound, totalMissing);
        return new EvidenceResult(bestEvidence, branchUsed, totalFound, totalMissing);
    }

    /**
     * Classify one fetched evidence file by category, count it in the
     * diagnostic stats, and record HIGH/MEDIUM evidence for every required
     * skill it proves.
     */
    private static void recordEvidenceFile(
            String file, String content, String owner, String repo, String branch,
            List<String> required, EvidenceStats stats,
            Set<String> confirmedSkills, EvidenceTracker tracker) {
        String lower = file.toLowerCase(Locale.ROOT);
        if (DOCUMENTATION_FILES.contains(file) || lower.startsWith("docs/") || lower.contains("/docs/")) {
            stats.documentsInspected++;
            recordChunkEvidence(content, repo, "-", file, branch,
                    "DOCUMENTATION", EvidenceTracker.MEDIUM, required, confirmedSkills, tracker);
        } else if (lower.startsWith("application.") || lower.equals("bootstrap.yml")
                || lower.startsWith("application-")) {
            stats.configFilesInspected++;
            recordChunkEvidence(content, repo, "-", file, branch,
                    "CONFIGURATION", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
        } else {
            // build files, dependency files, Docker/deployment files, CI files
            stats.buildFilesInspected++;
            recordChunkEvidence(content, repo, "-", file, branch,
                    "BUILD", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
        }
    }

    /**
     * Bounded directory probes for evidence that lives in subdirectories:
     * {@code .github/workflows} (CI), {@code docs} (documentation),
     * {@code k8s} / {@code deploy} / {@code manifests} (Kubernetes/deployment
     * manifests). Each probe costs 1 Contents API call plus at most
     * {@link #MAX_PROBED_FILES_PER_DIR} file fetches. This is deliberately
     * bounded — no recursive crawl.
     */
    private void probeEvidenceDirectories(
            String owner, String repo, String branch, List<String> required, EvidenceStats stats,
            MatchContext ctx, StringBuilder branchEvidence,
            Set<String> confirmedSkills, EvidenceTracker tracker) {
        for (String dir : EVIDENCE_PROBE_DIRS) {
            if (ctx.evidenceBudgetExhausted()) break;
            List<Map<String, Object>> items = budgetedDirFetch(owner, repo, branch, dir, ctx);
            if (items == null) continue;

            List<String> files = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item == null) continue;
                if ("file".equals(item.get("type")) && item.get("name") instanceof String n) {
                    if (!n.equalsIgnoreCase("README.md")) files.add(n);
                }
            }
            if (files.isEmpty()) continue;

            int fetched = 0;
            for (String f : files) {
                if (fetched >= MAX_PROBED_FILES_PER_DIR || ctx.evidenceBudgetExhausted()) break;
                FileResult fr = budgetedFetch(owner, repo, branch, dir + "/" + f, ctx);
                if (fr == null) break; // budget exhausted
                if (!fr.content.isBlank()) {
                    branchEvidence.append("\n[file ").append(dir).append("/").append(f).append("]\n")
                            .append(fr.content).append('\n');
                    fetched++;
                    String lowerDir = dir.toLowerCase(Locale.ROOT);
                    if (lowerDir.equals("docs")) {
                        stats.documentsInspected++;
                        recordChunkEvidence(fr.content, repo, dir, f, branch,
                                "DOCUMENTATION", EvidenceTracker.MEDIUM, required, confirmedSkills, tracker);
                    } else if (lowerDir.startsWith(".github")) {
                        stats.buildFilesInspected++;
                        recordChunkEvidence(fr.content, repo, dir, f, branch,
                                "BUILD", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
                    } else {
                        // k8s / deploy / manifests — deployment evidence
                        stats.buildFilesInspected++;
                        recordChunkEvidence(fr.content, repo, dir, f, branch,
                                "BUILD", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
                    }
                }
            }
        }
    }

    /**
     * Build branch priority: defaultBranch first (if non-null/non-blank),
     * then fallback to main/master, avoiding duplicates.
     */
    static List<String> buildBranchPriority(String defaultBranch) {
        java.util.LinkedHashSet<String> branches = new java.util.LinkedHashSet<>();
        if (defaultBranch != null && !defaultBranch.isBlank()) {
            branches.add(defaultBranch.trim());
        }
        branches.add("main");
        branches.add("master");
        return List.copyOf(branches);
    }

    // ────────────────────────── Source evidence discovery ──────────────────────────

    /**
     * Discover and fetch targeted high-signal source files for skill detection.
     * Returns at most {@link #MAX_SOURCE_FILES_PER_REPO} source files.
     *
     * <p>Discovery strategy (bounded GitHub Contents API calls):
     * <ol>
     *   <li>Fetch root directory listing (1 API call)</li>
     *   <li>Identify high-signal files and Java src directories</li>
     *   <li>If Java project: explore src/main/java + known package dirs
     *       (typically 2-6 additional API calls, bounded by {@link #SOURCE_TREE_DEPTH})</li>
     *   <li>Fetch up to 5 highest-signal files (raw.githubusercontent.com)</li>
     * </ol>
     */
    private SourceEvidenceResult fetchSourceEvidence(String owner, String repo, String branch,
                                                     List<String> required, EvidenceStats stats,
                                                     MatchContext ctx, Set<String> confirmedSkills,
                                                     EvidenceTracker tracker) {
        // Discover source file paths (bounded directory API calls)
        List<String> sourcePaths = discoverSourceFiles(owner, repo, branch, required, ctx);
        if (sourcePaths.isEmpty()) {
            return new SourceEvidenceResult("", 0, 0, List.of());
        }

        StringBuilder evidence = new StringBuilder();
        int fetched = 0;
        int failed = 0;
        List<String> skillsDetected = new ArrayList<>();
        int cap = ctx.sourceFileCap();

        for (String path : sourcePaths) {
            // Per-repository cap shared with nested-module source discovery.
            // No skill-based early stopping: high-signal files keep being
            // fetched up to the cap / budget so every required skill gets a
            // targeted source search.
            if (fetched >= cap || stats.sourceFilesFound >= cap) break;

            FileResult fr = budgetedFetch(owner, repo, branch, path, ctx);
            if (fr == null) break; // budget exhausted
            if (!fr.content.isBlank()) {
                String evidenceText = extractSourceEvidence(fr.content, path, required);
                if (!evidenceText.isBlank()) {
                    evidence.append("\n[file ").append(path).append("]\n");
                    evidence.append(evidenceText).append('\n');
                    fetched++;                        // Track which skills this source file helped detect (filename-aware for Docker)
                    for (String skill : required) {
                        if (!skillsDetected.contains(skill) && sourcePatternMatches(fr.content, path, skill)) {
                            skillsDetected.add(skill);
                        }
                    }
                    recordChunkEvidence(fr.content, repo, "-", path, branch,
                            "SOURCE", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
                }
            } else {
                failed++;
            }
        }

        log.debug("owner/{}/{} branch={} sourceFilesDiscovered={} sourceFilesFetched={} sourceFilesFailed={} sourceEvidenceSkills={}",
                owner, repo, branch, sourcePaths.size(), fetched, failed, skillsDetected);
        return new SourceEvidenceResult(evidence.toString(), fetched, failed, skillsDetected);
    }

    /**
     * Discover high-signal source file paths using bounded GitHub Contents API calls.
     * Only explores directories relevant to the required skills.
     * Stops early once enough high-signal files are located.
     */    /**
     * Candidate Java source roots to explore, in priority order.
     * The discovery checks which of these exist at the repository root
     * and explores the first ones that are found, bounded by budget.
     */
    private static final List<String> JAVA_SOURCE_ROOTS = List.of(
            "src/main/java",
            "backend/src/main/java",
            "app/src/main/java",
            "server/src/main/java",
            "api/src/main/java",
            "service/src/main/java"
    );

    /**
     * Multi-module root pattern: "services" directory containing sub-modules.
     * Each sub-module may have its own src/main/java tree.
     */
    private static final String MULTI_MODULE_SERVICES_ROOT = "services";

    /**
     * Discover high-signal source file paths using bounded GitHub Contents API calls.
     * Supports multiple source roots and multi-module project structures.
     * Only explores directories relevant to the required skills.
     * Stops early once enough high-signal files are located.
     */
    private List<String> discoverSourceFiles(String owner, String repo, String branch,
                                              List<String> required, MatchContext ctx) {
        List<String> paths = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> rootsChecked = new ArrayList<>();

        try {
            // Budget-aware root directory listing
            List<Map<String, Object>> rootItems = budgetedDirFetch(owner, repo, branch, "", ctx);
            if (rootItems == null) return paths;

            // Collect available source roots and root-level high-signal files
            Set<String> availableRoots = new LinkedHashSet<>();
            boolean hasMultiModuleServices = false;

            for (Map<String, Object> item : rootItems) {
                String name = (String) item.get("name");
                String type = (String) item.get("type");
                if (name == null || type == null) continue;

                if ("file".equals(type) && isHighSignalSourceFile(name)) {
                    if (seen.add(name)) paths.add(name);
                }

                // Check which known source roots exist
                if ("dir".equals(type)) {
                    for (String root : JAVA_SOURCE_ROOTS) {
                        String rootTopDir = root.split("/")[0];
                        if (name.equals(rootTopDir)) {
                            availableRoots.add(root);
                        }
                    }
                    // Multi-module detection
                    if (name.equals(MULTI_MODULE_SERVICES_ROOT)) {
                        hasMultiModuleServices = true;
                    }
                }
            }

            // Explore each available source root (bounded by budget)
            for (String root : availableRoots) {
                if (paths.size() >= ctx.sourceFileCap()) break;
                if (ctx.evidenceRequestBudget <= 0) break;
                rootsChecked.add(root);
                List<String> javaPaths = discoverJavaSourcePaths(
                        owner, repo, branch, root, required, ctx);
                for (String p : javaPaths) {
                    if (paths.size() >= ctx.sourceFileCap()) break;
                    if (seen.add(p)) paths.add(p);
                }
            }

            // Multi-module: explore services/*/src/main/java if not yet at limit
            if (hasMultiModuleServices && paths.size() < ctx.sourceFileCap()
                    && ctx.evidenceRequestBudget > 0) {
                rootsChecked.add(MULTI_MODULE_SERVICES_ROOT + "/*");
                discoverMultiModulePaths(owner, repo, branch, required, ctx, paths, seen);
            }

        } catch (Exception e) {
            log.debug("Source discovery failed for {}/{} branch={}: {}", owner, repo, branch, e.getMessage());
        }

        log.debug("repo={}/{} sourceRootsChecked={} sourcePathsFound={}",
                owner, repo, rootsChecked, paths.size());

        // Highest-signal files first (controllers/applications before services),
        // then cap at the per-repository source-file budget.
        if (paths.size() > 1) {
            paths.sort(Comparator.comparingInt(JobMatcherService::sourceFilePriority));
        }
        if (paths.size() > ctx.sourceFileCap()) {
            paths = paths.subList(0, ctx.sourceFileCap());
        }
        return paths;
    }

    /**
     * Explore multi-module projects (e.g., services/auth-service/src/main/java/...).
     * Lists the services/ directory, then explores each sub-module's src/main/java.
     */
    private void discoverMultiModulePaths(String owner, String repo, String branch,
                                            List<String> required, MatchContext ctx,
                                            List<String> paths, Set<String> seen) {
        try {
            List<Map<String, Object>> items = budgetedDirFetch(owner, repo, branch, MULTI_MODULE_SERVICES_ROOT, ctx);
            if (items == null) return;

            for (Map<String, Object> item : items) {
                if (paths.size() >= ctx.sourceFileCap()) break;
                if (ctx.evidenceRequestBudget <= 0) break;

                String name = (String) item.get("name");
                String type = (String) item.get("type");
                if (name == null || type == null || !"dir".equals(type)) continue;

                String moduleSrc = MULTI_MODULE_SERVICES_ROOT + "/" + name + "/src/main/java";
                List<String> modulePaths = discoverJavaSourcePaths(
                        owner, repo, branch, moduleSrc, required, ctx);
                for (String p : modulePaths) {
                    if (paths.size() >= ctx.sourceFileCap()) break;
                    if (seen.add(p)) paths.add(p);
                }
            }
        } catch (Exception e) {
            // Non-fatal
        }
    }

    // ────────────────────────── Nested module discovery ──────────────────────────

    /**
     * A directory name is a likely project module when it contains a common
     * module signal (service, gateway, backend, api, ...). This is a pure
     * discovery/selection signal — directory names alone never prove
     * Microservices; actual evidence must come from Eureka / Spring Cloud /
     * Feign / Gateway content inside the module.
     */
    static boolean isModuleLikeDir(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String signal : MODULE_SIGNAL_SUBSTRINGS) {
            if (lower.contains(signal)) return true;
        }
        return false;
    }

    /**
     * A directory is a package root when its name is a single lowercase word
     * (letters/digits/underscores) — e.g. com, org, dev, stschools,
     * microservices, api_gateway. Used to descend through standard Java
     * package layouts that hide relevant packages (controller/service/...)
     * below the domain root. Bounded callers: never an unbounded crawl.
     */
    static boolean isPackageRootDir(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith(".")) return false;
        if (NON_PACKAGE_DIRS.contains(lower)) return false;
        if (lower.length() > 32) return false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    /**
     * Select root-level module directories to probe for nested evidence.
     * Excludes hidden dirs, non-module noise, and standard source roots already
     * handled by root discovery. Module-like names (api-gateway, auth-service)
     * are probed before generic dirs. Bounded by {@code limit} — never an
     * unbounded crawl.
     */
    static List<String> selectModuleDirs(List<Map<String, Object>> rootItems, int limit) {
        if (rootItems == null || rootItems.isEmpty() || limit <= 0) return List.of();
        List<String> moduleLike = new ArrayList<>();
        List<String> generic = new ArrayList<>();
        for (Map<String, Object> item : rootItems) {
            if (item == null) continue;
            Object nameObj = item.get("name");
            Object typeObj = item.get("type");
            if (!(nameObj instanceof String name) || !"dir".equals(typeObj)) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(".")) continue;
            if (NON_MODULE_DIRS.contains(lower)) continue;
            if (STANDARD_ROOT_TOP_DIRS.contains(lower)) continue;
            (isModuleLikeDir(lower) ? moduleLike : generic).add(name);
        }
        List<String> ordered = new ArrayList<>(moduleLike);
        ordered.addAll(generic);
        return ordered.size() > limit ? List.copyOf(ordered.subList(0, limit)) : List.copyOf(ordered);
    }

    /**
     * Select subdirectories inside a module for depth-2 probing
     * (e.g. services/auth-service, backend/services). Bounded by {@code limit}.
     */
    static List<String> nestedSubmoduleDirs(List<Map<String, Object>> moduleItems, int limit) {
        if (moduleItems == null || moduleItems.isEmpty() || limit <= 0) return List.of();
        List<String> dirs = new ArrayList<>();
        for (Map<String, Object> item : moduleItems) {
            if (item == null) continue;
            Object nameObj = item.get("name");
            Object typeObj = item.get("type");
            if (!(nameObj instanceof String name) || !"dir".equals(typeObj)) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(".")) continue;
            if (NON_MODULE_DIRS.contains(lower)) continue;
            if (lower.equals("src")) continue; // handled by source-root discovery
            if (lower.equals("services") || isModuleLikeDir(lower)) dirs.add(name);
        }
        return dirs.size() > limit ? List.copyOf(dirs.subList(0, limit)) : List.copyOf(dirs);
    }

    /**
     * Which build files to fetch inside a module, given the module's directory
     * listing and the JD's required skills. Mirrors the root-level
     * {@link #evidenceFilesFor} priority: primary build file first, then
     * fallbacks. Config files (application.yml etc.) are returned by
     * {@link #nestedConfigFilesFor} and fetched separately while skills remain
     * unconfirmed.
     */
    static List<String> nestedBuildFilesFor(Set<String> entryNames, List<String> required) {
        if (entryNames == null || entryNames.isEmpty()) return List.of();
        Set<String> normalized = required == null ? Set.of()
                : required.stream().filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        boolean javaEcosystem = normalized.stream().anyMatch(s ->
                s.contains("java") || s.contains("spring") || s.contains("hibernate") ||
                s.contains("microservice") || s.contains("rest api") || s.contains("quarkus") ||
                s.contains("micronaut"));
        boolean javascriptEcosystem = normalized.stream().anyMatch(s ->
                s.contains("javascript") || s.contains("typescript") || s.equals("react") ||
                s.contains("node.js") || s.contains("nodejs") || s.contains("express"));
        boolean dockerEcosystem = normalized.stream().anyMatch(s ->
                s.contains("docker") || s.contains("kubernetes") || s.contains("ci/cd"));

        List<String> files = new ArrayList<>();
        if (javaEcosystem) {
            for (String f : List.of("pom.xml", "build.gradle", "build.gradle.kts")) {
                if (entryNames.contains(f)) files.add(f);
            }
        }
        if (javascriptEcosystem && entryNames.contains("package.json")) files.add("package.json");
        if (dockerEcosystem) {
            for (String f : List.of("Dockerfile", "docker-compose.yml", "docker-compose.yaml")) {
                if (entryNames.contains(f)) files.add(f);
            }
        }
        return List.copyOf(files);
    }

    /**
     * Config files to fetch inside a module when build files did not confirm
     * all skills (e.g. application.yml carrying Redis/PostgreSQL configuration).
     */
    static List<String> nestedConfigFilesFor(Set<String> entryNames, List<String> required) {
        if (entryNames == null || entryNames.isEmpty()) return List.of();
        Set<String> normalized = required == null ? Set.of()
                : required.stream().filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        boolean configRelevant = normalized.stream().anyMatch(s ->
                s.contains("java") || s.contains("spring") || s.contains("hibernate") ||
                s.contains("microservice") || s.contains("rest api") || s.contains("redis") ||
                s.contains("quarkus") || s.contains("micronaut"));
        if (!configRelevant) return List.of();
        List<String> files = new ArrayList<>();
        for (String f : List.of("application.yml", "application.yaml", "application.properties")) {
            if (entryNames.contains(f)) files.add(f);
        }
        return List.copyOf(files);
    }

    private static boolean allSkillsConfirmed(List<String> required, Set<String> confirmedSkills) {
        return !required.isEmpty() && required.stream().allMatch(confirmedSkills::contains);
    }

    private void updateConfirmedSkills(StringBuilder corpus, List<String> required, Set<String> confirmedSkills) {
        if (corpus == null || required == null || required.isEmpty()) return;
        String lower = corpus.toString().toLowerCase(Locale.ROOT);
        for (String skill : required) {
            if (!confirmedSkills.contains(skill) && matches(lower, skill)) {
                confirmedSkills.add(skill);
            }
        }
    }

    private record NestedModuleResult(String content, int modulesDiscovered, int buildFilesFound,
                                      int sourceRootsDiscovered, int sourceFilesFetched) {
        NestedModuleResult() { this("", 0, 0, 0, 0); }
    }

    /**
     * Discover evidence inside nested module directories (monorepos) such as
     * {@code api-gateway/pom.xml}, {@code fixmate-backend/src/main/java/.../Controller.java}.
     *
     * <p>Bounded discovery — maximum depth 2 below the repository root:
     * <ol>
     *   <li>Root listing → up to {@link #MAX_NESTED_MODULE_DIRS} module dirs</li>
     *   <li>Per module: build file (pom.xml/gradle/package.json/Dockerfile), then
     *       config files (application.yml) while skills remain unconfirmed</li>
     *   <li>Depth-2: up to {@link #MAX_NESTED_SUBMODULES} submodule dirs per module
     *       (pom.xml/package.json + nested source root)</li>
     *   <li>Nested source roots under module directories, capped per repository</li>
     * </ol>
     *
     * <p>All calls decrement the per-candidate request budget. Evidence from
     * multiple modules accumulates into the SAME per-repository corpus (module
     * A's evidence is never overwritten by module B's). Cache keys keep the
     * full nested path: {@code owner/repo/branch/module/path/file}.
     */
    private NestedModuleResult fetchNestedModuleEvidence(String owner, String repo, String branch,
                                                         List<String> required, EvidenceStats stats,
                                                         MatchContext ctx, Set<String> confirmedSkills,
                                                         EvidenceTracker tracker) {
        NestedModuleResult result = new NestedModuleResult();
        if (ctx.evidenceBudgetExhausted() || required.isEmpty()) return result;

        List<Map<String, Object>> rootItems = budgetedDirFetch(owner, repo, branch, "", ctx);
        if (rootItems == null) return result;

        List<String> moduleDirs = selectModuleDirs(rootItems, MAX_NESTED_MODULE_DIRS);
        if (moduleDirs.isEmpty()) return result;

        StringBuilder evidence = new StringBuilder();
        StringBuilder corpus = new StringBuilder();
        int modulesProbed = 0;
        int buildFilesFound = 0;
        int sourceRootsDiscovered = 0;
        int sourceFilesFetched = 0;

        for (String module : moduleDirs) {
            if (ctx.evidenceBudgetExhausted()) break;

            List<Map<String, Object>> moduleItems = budgetedDirFetch(owner, repo, branch, module, ctx);
            if (moduleItems == null) continue;
            modulesProbed++;

            Set<String> names = new HashSet<>();
            for (Map<String, Object> item : moduleItems) {
                if (item != null && item.get("name") instanceof String n) names.add(n);
            }

            // 1) Module build files (primary evidence: spring-boot-starter-web → Spring Boot,
            //    spring-cloud-starter-gateway + eureka → Microservices, Dockerfile → Docker)
            for (String f : nestedBuildFilesFor(names, required)) {
                if (ctx.evidenceBudgetExhausted()) break;
                FileResult fr = budgetedFetch(owner, repo, branch, module + "/" + f, ctx);
                if (fr == null) break; // budget exhausted
                if (!fr.content.isBlank()) {
                    evidence.append("\n[file ").append(module).append("/").append(f).append("]\n")
                            .append(fr.content).append('\n');
                    corpus.append(fr.content).append(' ');
                    buildFilesFound++;
                    stats.filesFound++;
                    recordModuleEvidence(fr.content, repo, module, f, branch,
                            required, stats, confirmedSkills, tracker);
                } else {
                    stats.filesMissing++;
                }
            }

            // 2) Module config files while skills remain unconfirmed
            if (!ctx.evidenceBudgetExhausted()) {
                for (String f : nestedConfigFilesFor(names, required)) {
                    if (ctx.evidenceBudgetExhausted()) break;
                    FileResult fr = budgetedFetch(owner, repo, branch, module + "/" + f, ctx);
                    if (fr == null) break; // budget exhausted
                    if (!fr.content.isBlank()) {
                        evidence.append("\n[file ").append(module).append("/").append(f).append("]\n")
                                .append(fr.content).append('\n');
                        corpus.append(fr.content).append(' ');
                        buildFilesFound++;
                        stats.filesFound++;
                        recordModuleEvidence(fr.content, repo, module, f, branch,
                            required, stats, confirmedSkills, tracker);
                    } else {
                        stats.filesMissing++;
                    }
                }
            }

            // 3) Depth-2 submodules (services/auth-service, backend/services/...)
            if (!ctx.evidenceBudgetExhausted()) {
                for (String sub : nestedSubmoduleDirs(moduleItems, MAX_NESTED_SUBMODULES)) {
                    if (ctx.evidenceBudgetExhausted()) break;
                    List<Map<String, Object>> subItems = budgetedDirFetch(owner, repo, branch, module + "/" + sub, ctx);
                    if (subItems == null) continue;

                    Set<String> subNames = new HashSet<>();
                    for (Map<String, Object> item : subItems) {
                        if (item != null && item.get("name") instanceof String n) subNames.add(n);
                    }

                    // Depth-2 build file: pom.xml / package.json only (bounded)
                    for (String f : List.of("pom.xml", "package.json")) {
                        if (!subNames.contains(f)) continue;
                        if (ctx.evidenceBudgetExhausted()) break;
                        FileResult fr = budgetedFetch(owner, repo, branch, module + "/" + sub + "/" + f, ctx);
                        if (fr == null) break; // budget exhausted
                        if (!fr.content.isBlank()) {
                            evidence.append("\n[file ").append(module).append("/").append(sub).append("/").append(f).append("]\n")
                                    .append(fr.content).append('\n');
                            corpus.append(fr.content).append(' ');
                            buildFilesFound++;
                            stats.filesFound++;
                            recordModuleEvidence(fr.content, repo, module, f, branch,
                            required, stats, confirmedSkills, tracker);
                        } else {
                            stats.filesMissing++;
                        }
                    }

                    // Depth-2 nested source root
                    if (subNames.contains("src")
                            && stats.sourceFilesFound < ctx.sourceFileCap()) {
                        sourceRootsDiscovered++;
                        sourceFilesFetched += fetchNestedSourceFiles(owner, repo, branch, required, stats, ctx,
                                confirmedSkills, tracker, evidence,
                                module + "/" + sub + "/src/main/java", ctx.sourceFileCap());
                    }
                }
            }

            // 4) Nested source root (*/src/main/java) with per-repo file cap
            if (names.contains("src")
                    && !ctx.evidenceBudgetExhausted()
                    && stats.sourceFilesFound < ctx.sourceFileCap()) {
                sourceRootsDiscovered++;
                sourceFilesFetched += fetchNestedSourceFiles(owner, repo, branch, required, stats, ctx,
                        confirmedSkills, tracker, evidence,
                        module + "/src/main/java", ctx.sourceFileCap());
            }
        }

        stats.modulesDiscovered += moduleDirs.size();
        stats.sourceRootsDiscovered += sourceRootsDiscovered;
        log.debug("repo={}/{} defaultBranch={} modulesDiscovered={} modulesProbed={} buildFilesDiscovered={} " +
                        "sourceRootsDiscovered={} sourceFilesFetched={} skillsAdded={}",
                owner, repo, branch, moduleDirs.size(), modulesProbed, buildFilesFound,
                sourceRootsDiscovered, sourceFilesFetched, new ArrayList<>(confirmedSkills));

        return new NestedModuleResult(evidence.toString(), moduleDirs.size(), buildFilesFound,
                sourceRootsDiscovered, sourceFilesFetched);
    }

    /**
     * Discover and fetch source files under a nested Java source root.
     * Honors the per-repository {@link #MAX_SOURCE_FILES_PER_REPO} cap via
     * {@code stats.sourceFilesFound} (shared with root source discovery).
     * Returns the number of files actually fetched.
     */
    private int fetchNestedSourceFiles(String owner, String repo, String branch, List<String> required,
                                       EvidenceStats stats, MatchContext ctx, Set<String> confirmedSkills,
                                       EvidenceTracker tracker, StringBuilder evidence,
                                       String sourceRoot, int cap) {
        int fetched = 0;
        List<String> paths = discoverJavaSourcePaths(owner, repo, branch, sourceRoot, required, ctx);
        for (String p : paths) {
            if (ctx.evidenceBudgetExhausted()) break;
            if (stats.sourceFilesFound >= cap) break;
            FileResult fr = budgetedFetch(owner, repo, branch, p, ctx);
            if (fr == null) break; // budget exhausted
            if (!fr.content.isBlank()) {
                String ev = extractSourceEvidence(fr.content, p, required);
                if (!ev.isBlank()) {
                    evidence.append("\n[file ").append(p).append("]\n").append(ev).append('\n');
                    fetched++;
                    stats.sourceFilesFound++;
                    recordChunkEvidence(fr.content, repo, "-", p, branch,
                            "SOURCE", EvidenceTracker.HIGH, required, confirmedSkills, tracker);
                }
            } else {
                stats.sourceFilesMissing++;
            }
        }
        return fetched;
    }

    private List<String> discoverJavaSourcePaths(String owner, String repo, String branch,
                                                  String basePath, List<String> required,
                                                  MatchContext ctx) {
        List<String> paths = new ArrayList<>();
        Set<String> normalizedRequired = required == null ? Set.of()
                : required.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> relevantPackages = filterRelevantPackages(normalizedRequired);

        try {
            List<Map<String, Object>> items = budgetedDirFetch(owner, repo, branch, basePath, ctx);
            if (items == null) return paths;

            Queue<String> dirsToExplore = new java.util.LinkedList<>();

            for (Map<String, Object> item : items) {
                String name = (String) item.get("name");
                String type = (String) item.get("type");
                if (name == null || type == null) continue;

                if ("file".equals(type) && SOURCE_FILE_NAMES.contains(name)) {
                    paths.add(basePath + "/" + name);
                }
                if ("dir".equals(type) && relevantPackages.contains(name.toLowerCase(Locale.ROOT))) {
                    dirsToExplore.add(basePath + "/" + name);
                }
            }

            // Package-root descent: standard Java layouts hide relevant packages
            // below the domain root (com/stschools/microservices/controller, ...).
            // Bounded: at most MAX_PACKAGE_ROOT_BRANCHES branches per level,
            // at most PACKAGE_ROOT_DESCENT_DEPTH levels, all budget-decremented.
            if (dirsToExplore.isEmpty() && paths.size() < ctx.sourceFileCap()
                    && ctx.evidenceRequestBudget > 0) {
                Queue<String> packageRoots = new java.util.LinkedList<>();
                for (Map<String, Object> item : items) {
                    String name = (String) item.get("name");
                    String type = (String) item.get("type");
                    if (name == null || type == null || !"dir".equals(type)) continue;
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!relevantPackages.contains(lower) && isPackageRootDir(name)) {
                        packageRoots.add(basePath + "/" + name);
                        if (packageRoots.size() >= MAX_PACKAGE_ROOT_BRANCHES) break;
                    }
                }
                int descent = 0;
                while (!packageRoots.isEmpty() && descent < PACKAGE_ROOT_DESCENT_DEPTH
                        && dirsToExplore.isEmpty()
                        && paths.size() < ctx.sourceFileCap()
                        && ctx.evidenceRequestBudget > 0) {
                    String dir = packageRoots.poll();
                    List<Map<String, Object>> dirItems = budgetedDirFetch(owner, repo, branch, dir, ctx);
                    if (dirItems == null) continue;
                    int branchesAdded = 0;
                    for (Map<String, Object> di : dirItems) {
                        String name = (String) di.get("name");
                        String type = (String) di.get("type");
                        if (name == null || type == null) continue;
                        if ("file".equals(type) && SOURCE_FILE_NAMES.contains(name)
                                && paths.size() < ctx.sourceFileCap()) {
                            paths.add(dir + "/" + name);
                        }
                        if ("dir".equals(type)) {
                            String lower = name.toLowerCase(Locale.ROOT);
                            if (relevantPackages.contains(lower)) {
                                dirsToExplore.add(dir + "/" + name);
                            } else if (isPackageRootDir(name) && branchesAdded < MAX_PACKAGE_ROOT_BRANCHES) {
                                packageRoots.add(dir + "/" + name);
                                branchesAdded++;
                            }
                        }
                    }
                    descent++;
                }
            }

            int depth = 0;
            while (!dirsToExplore.isEmpty() && depth < SOURCE_TREE_DEPTH
                    && paths.size() < ctx.sourceFileCap() && ctx.evidenceRequestBudget > 0) {
                String dir = dirsToExplore.poll();
                try {
                    List<Map<String, Object>> dirItems = budgetedDirFetch(owner, repo, branch, dir, ctx);
                    if (dirItems == null) continue;

                    for (Map<String, Object> di : dirItems) {
                        String name = (String) di.get("name");
                        String type = (String) di.get("type");
                        if (name == null || type == null) continue;
                        if ("file".equals(type) && SOURCE_FILE_NAMES.contains(name)) {
                            paths.add(dir + "/" + name);
                        }
                    }
                } catch (Exception e) {
                    // Skip inaccessible directories
                }
                depth++;
            }

        } catch (Exception e) {
            // Non-fatal
        }
        return paths;
    }

    /**
     * Filter package directories to only those relevant to the required skills.
     * This reduces unnecessary directory exploration during source discovery.
     */
    private static Set<String> filterRelevantPackages(Set<String> normalizedRequired) {
        Set<String> relevant = new HashSet<>();
        boolean needsControllers = normalizedRequired.stream().anyMatch(s ->
                s.contains("rest api") || s.contains("spring") || s.contains("java") ||
                s.contains("web") || s.contains("api"));
        boolean needsServices = normalizedRequired.stream().anyMatch(s ->
                s.contains("microservice") || s.contains("service") || s.contains("business"));
        boolean needsConfig = normalizedRequired.stream().anyMatch(s ->
                s.contains("spring") || s.contains("config") || s.contains("security") ||
                s.contains("gateway") || s.contains("eureka") || s.contains("redis") ||
                s.contains("kafka"));
        boolean needsRepository = normalizedRequired.stream().anyMatch(s ->
                s.contains("sql") || s.contains("database") || s.contains("jpa") ||
                s.contains("hibernate") || s.contains("data") || s.contains("postgres") ||
                s.contains("mysql") || s.contains("mongodb"));
        boolean needsGateway = normalizedRequired.stream().anyMatch(s ->
                s.contains("gateway") || s.contains("microservice") || s.contains("routing") ||
                s.contains("eureka") || s.contains("feign") || s.contains("discovery"));
        boolean needsMessaging = normalizedRequired.stream().anyMatch(s ->
                s.contains("kafka") || s.contains("rabbitmq") || s.contains("messaging") ||
                s.contains("queue"));
        boolean needsCloud = normalizedRequired.stream().anyMatch(s ->
                s.contains("aws") || s.contains("azure") || s.contains("gcp") ||
                s.contains("cloud") || s.contains("s3"));

        // TARGETED SOURCE SEARCH: when a required skill is unresolved, the
        // source discovery deliberately descends into the packages where that
        // skill's evidence typically lives (Redis config, Kafka consumers,
        // JPA repositories, Feign clients, cloud clients...).
        if (needsControllers) { relevant.add("controller"); relevant.add("controllers"); }
        if (needsServices) { relevant.add("service"); relevant.add("services"); }
        if (needsConfig) {
            relevant.add("config"); relevant.add("configuration");
            if (normalizedRequired.stream().anyMatch(s -> s.contains("redis"))) {
                relevant.add("redis"); relevant.add("cache");
            }
            if (normalizedRequired.stream().anyMatch(s -> s.contains("kafka"))) {
                relevant.add("kafka");
            }
        }
        if (needsRepository) {
            relevant.add("repository"); relevant.add("repo");
            if (normalizedRequired.stream().anyMatch(s ->
                    s.contains("sql") || s.contains("database") || s.contains("jpa") ||
                            s.contains("hibernate"))) {
                relevant.add("dao"); relevant.add("jdbc"); relevant.add("entity");
                relevant.add("model"); relevant.add("domain"); relevant.add("persistence");
            }
        }
        if (needsGateway) {
            relevant.add("gateway"); relevant.add("filter");
            relevant.add("client"); relevant.add("discovery");
        }
        if (needsMessaging) {
            relevant.add("consumer"); relevant.add("producer");
            relevant.add("listener"); relevant.add("messaging");
        }
        if (needsCloud) {
            relevant.add("client"); relevant.add("cloud"); relevant.add("aws");
            relevant.add("s3"); relevant.add("config");
        }
        // Always explore model/entity/dto for skill detection
        relevant.add("model");
        relevant.add("entity");
        relevant.add("dto");

        return relevant;
    }

    // ────────────────────────── Source evidence extraction ──────────────────────────

    /**
     * Extract skill-relevant evidence from a source file's content.
     * Returns evidence text snippets only for skills that are in the required list.
     * Empty string means no relevant source evidence found in this file.
     */
    private static String extractSourceEvidence(String content, String path, List<String> required) {
        if (content == null || content.isBlank()) return "";
        if (required == null || required.isEmpty()) return "";

        StringBuilder evidence = new StringBuilder();
        Set<String> detected = new HashSet<>();

        for (String skill : required) {
            if (sourcePatternMatches(content, path, skill)) {
                detected.add(skill);
                // Append the specific annotation/pattern line for HIGH-evidence priority
                String snippet = extractSourceSnippet(content, skill);
                if (!snippet.isBlank()) {
                    evidence.append("[").append(skill).append("] ").append(snippet).append('\n');
                }
            }
        }

        return evidence.toString();
    }

    /**
     * Check whether a source file's content matches skill-specific patterns.
     * Uses HIGH-evidence priority: direct source annotations/keywords.
     * FALSE-POSITIVE PROTECTION: patterns require word boundaries or specific contexts.
     */
    static boolean sourcePatternMatches(String content, String skill) {
        if (content == null || skill == null) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        String normalized = skill.toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "rest api" -> {
                // HIGH-evidence: Spring MVC annotations (word-boundary safe, @ prefix prevents false matches)
                return lower.contains("@restcontroller") ||
                        lower.contains("@restcontrolleradvice") ||
                        lower.contains("@requestmapping") ||
                        lower.contains("@getmapping") ||
                        lower.contains("@postmapping") ||
                        lower.contains("@putmapping") ||
                        lower.contains("@patchmapping") ||
                        lower.contains("@deletemapping") ||
                        lower.contains("@requestparam") ||
                        lower.contains("@pathvariable") ||
                        lower.contains("@responseentity") ||
                        // Express/Node.js REST patterns
                        lower.contains("app.get(") ||
                        lower.contains("app.post(") ||
                        lower.contains("app.put(") ||
                        lower.contains("app.delete(") ||
                        lower.contains("router.get(") ||
                        lower.contains("router.post(") ||
                        // Python FastAPI/Flask REST patterns
                        lower.contains("@app.get(") ||
                        lower.contains("@app.post(") ||
                        lower.contains("@app.put(") ||
                        lower.contains("@app.delete(") ||
                        lower.contains("@router.get(") ||
                        lower.contains("@router.post(");
            }
            case "microservices" -> {
                // HIGH-evidence: architectural patterns, NOT just the word "service"
                // Require multiple signals OR one very strong signal
                boolean hasSpringCloud = lower.contains("spring cloud") || lower.contains("spring-cloud");
                boolean hasEureka = lower.contains("@enableeurekaclient") || lower.contains("@enablediscoveryclient") ||
                        lower.contains("eureka");
                boolean hasFeign = lower.contains("@feignclient");
                boolean hasLoadBalanced = lower.contains("@loadbalanced");
                boolean hasGateway = lower.contains("spring cloud gateway") || lower.contains("spring-cloud-gateway") ||
                        lower.contains("@enablezuulproxy") || lower.contains("gatewayconfig") ||
                        lower.contains("apigateway") || lower.contains("api-gateway");
                boolean hasServiceDiscovery = lower.contains("service discovery") || lower.contains("service-discovery");
                boolean hasMultipleModules = lower.contains("multi-module") || lower.contains("multi module");

                // Strong single signals — Spring Cloud microservice annotations/patterns
                if (hasEureka || hasFeign || hasLoadBalanced || hasGateway || hasServiceDiscovery || hasMultipleModules) return true;

                // Spring Cloud is a strong standalone microservices signal
                if (hasSpringCloud) return true;

                return false;
            }
            case "spring boot" -> {
                // HIGH-evidence: @SpringBootApplication, Spring Boot starter dependencies
                return lower.contains("@springbootapplication") ||
                        lower.contains("@springbootconfiguration") ||
                        lower.contains("spring.application.name") ||
                        lower.contains("spring-boot-starter") ||
                        lower.contains("springapplication.run");
            }
            case "sql" -> {
                // HIGH-evidence: database systems, SQL queries, JPA annotations, migrations
                // Direct database names — strong SQL evidence
                boolean hasDbSystem = lower.contains("postgresql") || lower.contains("postgres") ||
                        lower.contains("mysql") || lower.contains("sql server") || lower.contains("sqlserver") ||
                        lower.contains("mariadb") || lower.contains("oracle") || lower.contains("mssql") ||
                        lower.contains("database") || lower.contains("datasource");
                // SQL operations and queries
                boolean hasSqlOps = lower.contains("select ") || lower.contains("insert into") ||
                        lower.contains("update ") || lower.contains("delete from") ||
                        lower.contains("create table") || lower.contains("alter table") ||
                        lower.contains("drop table");
                // JPA/ORM annotations and patterns
                boolean hasJpaAnnotations = lower.contains("@query") || lower.contains("@nativequery") ||
                        lower.contains("@entity") || lower.contains("@table") ||
                        lower.contains("@column") || lower.contains("@jpql") ||
                        lower.contains("jparepository") || lower.contains("crudrepository") ||
                        lower.contains("spring data jpa");
                // Migration tools and JDBC
                boolean hasMigrations = lower.contains("jdbc") || lower.contains("flyway") ||
                        lower.contains("liquibase") || lower.contains("migration");
                return hasDbSystem || hasSqlOps || hasJpaAnnotations || hasMigrations;
            }
            case "react" -> {
                // HIGH-evidence: React imports and hooks
                return lower.contains("from 'react'") ||
                        lower.contains("from \"react\"") ||
                        lower.contains("import react") ||
                        lower.contains("usestate") ||
                        lower.contains("useeffect") ||
                        lower.contains("usecallback") ||
                        lower.contains("usememo") ||
                        lower.contains("usecontext") ||
                        lower.contains("useref") ||
                        lower.contains("react.component") ||
                        lower.contains("react.fragment") ||
                        lower.contains("<tsx") ||
                        lower.contains("<jsx");
            }
            case "docker" -> {
                // Docker evidence requires EITHER filename context OR explicit keyword evidence.
                // Standalone "FROM"/"COPY" in Java files must NOT be classified as Docker.
                // docker-compose files are strong Docker evidence.
                return lower.contains("docker") || lower.contains("dockerfile") ||
                        lower.contains("containerization") || lower.contains("containerized") ||
                        lower.contains("docker compose") || lower.contains("docker-compose") ||
                        lower.contains("dockerfile:") || lower.contains("image:");
            }
            case "java" -> {
                // Java source evidence: strong Java-specific imports and annotations
                return lower.contains("import java.") || lower.contains("import javax.") ||
                        lower.contains("import jakarta.") || lower.contains("java.util.") ||
                        lower.contains("java.lang.") || lower.contains("java.io.") ||
                        lower.contains("java.nio.") ||
                        lower.contains("@springbootapplication") ||
                        lower.contains("springapplication.run");
            }
            case "git" -> {
                // Git is handled at the platform level (buildCandidateCorpus adds "git_source:github_repository").
                // Source-level Git evidence: .gitignore, Git commands, CI/CD with git.
                return lower.contains(".gitignore") ||
                        lower.contains("git commit") ||
                        lower.contains("git push") ||
                        lower.contains("git pull") ||
                        lower.contains("git clone") ||
                        lower.contains("github actions") ||
                        lower.contains("github-action");
            }
            default -> {
                // For all other skills, fall back to the text-based SKILL_PATTERNS
                Pattern p = SKILL_PATTERNS.get(skill);
                return p != null && p.matcher(content).find();
            }
        }
    }

    /**
     * Filename-aware source pattern matching.
     * For Docker detection: the file path being a Dockerfile or docker-compose file
     * is itself strong evidence. For all other skills, delegates to the content-only version.
     */
    static boolean sourcePatternMatches(String content, String filename, String skill) {
        if (content == null || skill == null) return false;
        String normalized = skill.toLowerCase(Locale.ROOT);

        // Docker: filename itself is evidence
        if ("docker".equals(normalized) && filename != null) {
            String fnLower = filename.toLowerCase(Locale.ROOT);
            // Dockerfile (at any path level) is strong Docker evidence
            if (fnLower.contains("dockerfile")) return true;
            // docker-compose files are strong Docker evidence
            if (fnLower.contains("docker-compose") || fnLower.contains("docker_compose")) return true;
        }

        // For all other skills (or Docker without filename match), delegate to content matching
        return sourcePatternMatches(content, skill);
    }

    /**
     * Extract a short evidence snippet from source content for a given skill.
     * Returns the first matching annotation/line (truncated to MAX_EVIDENCE_CHARS_PER_FILE).
     * This provides the HIGH-evidence priority indicator.
     */
    private static String extractSourceSnippet(String content, String skill) {
        if (content == null || skill == null) return "";
        String normalized = skill.toLowerCase(Locale.ROOT);
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
            String lowerLine = trimmed.toLowerCase(Locale.ROOT);

            boolean match = switch (normalized) {
                case "rest api" -> lowerLine.contains("@restcontroller") ||
                        lowerLine.contains("@getmapping") || lowerLine.contains("@postmapping") ||
                        lowerLine.contains("@requestmapping") || lowerLine.contains("@putmapping") ||
                        lowerLine.contains("@deletemapping") || lowerLine.contains("@patchmapping");
                case "microservices" -> lowerLine.contains("eureka") || lowerLine.contains("@feignclient") ||
                        lowerLine.contains("@enablediscoveryclient") || lowerLine.contains("@loadbalanced") ||
                        lowerLine.contains("spring cloud") || lowerLine.contains("gatewayconfig");
                case "spring boot" -> lowerLine.contains("@springbootapplication") ||
                        lowerLine.contains("spring-boot-starter") || lowerLine.contains("springapplication.run");
                case "sql" -> lowerLine.contains("postgresql") || lowerLine.contains("postgres") ||
                        lowerLine.contains("mysql") || lowerLine.contains("select ") ||
                        lowerLine.contains("insert into") || lowerLine.contains("update ") ||
                        lowerLine.contains("delete from") ||
                        lowerLine.contains("@query") || lowerLine.contains("@nativequery") ||
                        lowerLine.contains("@entity") || lowerLine.contains("create table");
                case "docker" -> lowerLine.contains("docker") || lowerLine.contains("dockerfile") ||
                        lowerLine.contains("containerization") || lowerLine.contains("image:");
                case "java" -> lowerLine.contains("import java.") || lowerLine.contains("import javax.") ||
                        lowerLine.contains("import jakarta.") || lowerLine.contains("@springbootapplication");
                case "react" -> lowerLine.contains("from 'react'") || lowerLine.contains("from \"react\"") ||
                        lowerLine.contains("import react") || lowerLine.contains("usestate");
                case "git" -> lowerLine.contains("git commit") || lowerLine.contains("git push") ||
                        lowerLine.contains("github actions");
                default -> false;
            };

            if (match) {
                return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
            }
        }

        // Generic fallback for skills proven by alias text rather than by a
        // code annotation (Redis, Kafka, MongoDB, SQL Server, ...): return the
        // first line — comments and imports included — that contains a
        // standalone alias occurrence (e.g. "spring.data.redis"). Without this,
        // a fetched RedisConfig.java / KafkaConsumer.java would yield no
        // evidence text and the skill could never be credited.
        Pattern generic = SKILL_PATTERNS.get(skill);
        if (generic != null) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String lowerLine = trimmed.toLowerCase(Locale.ROOT);
                if (generic.matcher(lowerLine).find()) {
                    return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
                }
            }
        }
        return "";
    }

    // ────────────────────────── Raw file fetching ──────────────────────────

    record FileResult(String content, String errorType) {}

    private FileResult fetchRawRepositoryFile(String owner, String repo, String branch, String file,
                                                MatchContext ctx) {
        // Nested module paths keep their full path in the cache key:
        // owner/repo/branch/api-gateway/pom.xml — never collapsed to the root.
        String key = evidenceCacheKey(owner, repo, branch, file);
        String cached = evidenceCache.get(key);
        if (cached != null) {
            return new FileResult(cached, null);
        }

        try {
            // Use dynamic timeout that respects remaining global match deadline
            RestClient client = dynamicRawClient(ctx.remainingTimeMs());
            String content = client.get()
                    .uri("/{owner}/{repo}/{branch}/{file}", owner, repo, branch, file)
                    .retrieve()
                    .body(String.class);

            if (content == null) {
                content = "";
            }

            content = content.length() > MAX_EVIDENCE_CHARS_PER_FILE
                    ? content.substring(0, MAX_EVIDENCE_CHARS_PER_FILE)
                    : content;

            evidenceCache.put(key, content);
            return new FileResult(content, null);

        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            evidenceCache.put(key, "");
            return new FileResult("", "404");
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            // 429 is transient — do not permanently cache as missing evidence
            return new FileResult("", "429");
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            evidenceCache.put(key, "");
            return new FileResult("", "403");
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // 5xx is transient — do not permanently cache as missing evidence
            return new FileResult("", "5xx");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Timeout or connection failure — do not cache as permanent empty
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            String type = msg.contains("timeout") ? "timeout" : "connection_failure";
            return new FileResult("", type);
        } catch (Exception e) {
            // Any other transient failure — do not cache as permanent empty
            evidenceCache.put(key, "");
            return new FileResult("", "unknown");
        }
    }

    /** Cache key for a single evidence file: owner/repo/branch/full-path. */
    static String evidenceCacheKey(String owner, String repo, String branch, String file) {
        return owner + "/" + repo + "/" + branch + "/" + file;
    }

    private static RestClient buildRawGithubClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);

        return RestClient.builder()
                .baseUrl("https://raw.githubusercontent.com")
                .defaultHeader("Accept", "text/plain")
                .requestFactory(factory)
                .build();
    }

    /**
     * Create a RestClient with dynamic socket timeouts that respect the remaining
     * global match deadline. The actual HTTP request will be interrupted by the
     * socket timeout if it exceeds the remaining time, preventing any single
     * request from outliving the global deadline.
     *
     * @param remainingMs remaining time in milliseconds until the global deadline
     * @param defaultConn connect timeout fallback (ms)
     * @param defaultRead read timeout fallback (ms)
     */
    private RestClient dynamicTimeoutClient(long remainingMs, int defaultConn, int defaultRead) {
        int effectiveTimeout = (int) Math.max(100, Math.min(remainingMs, defaultRead));

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.max(100, Math.min(remainingMs, defaultConn)));
        factory.setReadTimeout(effectiveTimeout);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(githubServiceBaseUrl)
                .requestFactory(factory);
        if (githubApiKey != null && !githubApiKey.isBlank()) {
            builder.defaultHeader("X-Internal-Api-Key", githubApiKey);
        }
        return builder.build();
    }

    /**
     * Create a raw GitHub content client with dynamic timeout.
     */
    private RestClient dynamicRawClient(long remainingMs) {
        int effectiveTimeout = (int) Math.max(100, Math.min(remainingMs, 5_000));

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.max(100, Math.min(remainingMs, 3_000)));
        factory.setReadTimeout(effectiveTimeout);

        return RestClient.builder()
                .baseUrl("https://raw.githubusercontent.com")
                .defaultHeader("Accept", "text/plain")
                .requestFactory(factory)
                .build();
    }

    private static RestClient buildClient(String baseUrl, String internalApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        // Server-to-server credential for github-service's internal AI endpoints
        // (/api/ai/job-match). When INTERNAL_API_KEY is unset the header is
        // omitted and github-service rejects the call — AI explanations degrade
        // gracefully to the deterministic ranking.
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            builder.defaultHeader("X-Internal-Api-Key", internalApiKey);
        }
        return builder.build();
    }

    private static String extractPdfText(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException(
                        "PDF job descriptions are limited to " + MAX_PDF_PAGES + " pages.");
            }
            return new PDFTextStripper().getText(document);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Never echo PDFBox internals to the client.
            throw new IllegalArgumentException(
                    "Could not read the PDF job description. Please upload a text-based PDF or use a .txt/.md file.", e);
        }
    }

    private static Map<String, Pattern> buildSkillPatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : SKILL_ALIASES.entrySet()) {
            String aliases = entry.getValue().stream()
                    .map(a -> Pattern.quote(a.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.joining("|"));
            patterns.put(entry.getKey(), Pattern.compile("(?i)(?<![a-z0-9])(?:" + aliases + ")(?![a-z0-9])"));
        }
        return Collections.unmodifiableMap(patterns);
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    private static Map<String, List<String>> buildSkillAliases() {
        Map<String, List<String>> skills = new LinkedHashMap<>();
        // ── Languages ──
        skills.put("Java", List.of("java", "jdk"));
        skills.put("Kotlin", List.of("kotlin"));
        skills.put("Scala", List.of("scala"));
        skills.put("Groovy", List.of("groovy"));
        skills.put("TypeScript", List.of("typescript"));
        skills.put("JavaScript", List.of("javascript", "ecmascript"));
        skills.put("Python", List.of("python"));
        skills.put("Ruby", List.of("ruby"));
        skills.put("PHP", List.of("php"));
        skills.put("Go", List.of("golang", "go lang", "go programming", "go developer"));
        skills.put("Rust", List.of("rust"));
        skills.put("C++", List.of("c++", "c plus plus"));
        skills.put("C/C++", List.of("c/c++", "c/c plus plus"));
        skills.put("C#", List.of("c#", "c sharp"));
        skills.put("Swift", List.of("swift"));
        skills.put("Objective-C", List.of("objective-c", "objective c"));
        skills.put("Dart", List.of("dart"));
        skills.put("Flutter", List.of("flutter"));
        skills.put("Shell Scripting", List.of("bash", "shell scripting", "shell script", "powershell"));
        skills.put("SQL", List.of("sql"));
        skills.put("HTML", List.of("html", "html5"));
        skills.put("CSS", List.of("css", "css3"));
        skills.put("Sass", List.of("sass", "scss"));
        skills.put("R", List.of("r programming", "r language"));
        skills.put("MATLAB", List.of("matlab"));
        skills.put("Haskell", List.of("haskell"));
        skills.put("Lua", List.of("lua"));
        skills.put("Perl", List.of("perl"));
        skills.put("Julia", List.of("julia"));
        // ── Frameworks & platforms ──
        skills.put("Spring Boot", List.of("spring boot", "springboot", "spring-boot", "spring-boot-starter", "spring framework", "spring mvc", "spring cloud", "spring security", "spring data", "@springbootapplication"));
        skills.put("Hibernate", List.of("hibernate", "jpa"));
        skills.put("Node.js", List.of("node.js", "nodejs", "node js"));
        skills.put("Express", List.of("express.js", "expressjs", "express js"));
        skills.put("NestJS", List.of("nest.js", "nestjs", "nest js"));
        skills.put("Next.js", List.of("next.js", "nextjs", "next js"));
        skills.put("React", List.of("react", "react.js", "reactjs", "react js"));
        skills.put("React Native", List.of("react native"));
        skills.put("Angular", List.of("angular", "angularjs"));
        skills.put("Vue", List.of("vue.js", "vuejs", "vue js"));
        skills.put("Svelte", List.of("svelte"));
        skills.put("Django", List.of("django"));
        skills.put("Flask", List.of("flask"));
        skills.put("FastAPI", List.of("fastapi", "fast api"));
        skills.put("Laravel", List.of("laravel"));
        skills.put("Rails", List.of("ruby on rails", "rails"));
        skills.put("ASP.NET", List.of("asp.net", "asp net", ".net core", "dotnet", "dot net"));
        skills.put("Quarkus", List.of("quarkus"));
        skills.put("Micronaut", List.of("micronaut"));
        skills.put("Android", List.of("android"));
        skills.put("iOS", List.of("ios", "iphone"));
        skills.put("jQuery", List.of("jquery"));
        skills.put("GraphQL", List.of("graphql"));
        skills.put("gRPC", List.of("grpc"));
        skills.put("REST API", List.of("rest api", "restful", "rest apis", "rest services", "restcontroller", "rest controller", "@restcontroller", "requestmapping", "getmapping", "postmapping", "putmapping", "deletemapping"));
        // ── Data / ML / AI ──
        skills.put("Machine Learning", List.of("machine learning", "ml"));
        skills.put("Deep Learning", List.of("deep learning"));
        skills.put("Artificial Intelligence", List.of("artificial intelligence", "ai"));
        skills.put("NLP", List.of("nlp", "natural language processing"));
        skills.put("Computer Vision", List.of("computer vision"));
        skills.put("TensorFlow", List.of("tensorflow"));
        skills.put("PyTorch", List.of("pytorch"));
        skills.put("Keras", List.of("keras"));
        skills.put("scikit-learn", List.of("scikit-learn", "sklearn", "scikit learn"));
        skills.put("Pandas", List.of("pandas"));
        skills.put("NumPy", List.of("numpy"));
        skills.put("Data Science", List.of("data science"));
        skills.put("Data Engineering", List.of("data engineering"));
        skills.put("Data Analysis", List.of("data analysis", "data analytics"));
        skills.put("Big Data", List.of("big data"));
        skills.put("Apache Spark", List.of("spark", "apache spark"));
        skills.put("Hadoop", List.of("hadoop"));
        skills.put("Kafka", List.of("kafka"));
        skills.put("Airflow", List.of("airflow"));
        skills.put("ETL", List.of("etl"));
        skills.put("Tableau", List.of("tableau"));
        skills.put("Power BI", List.of("power bi", "powerbi"));
        // ── Databases ──
        skills.put("PostgreSQL", List.of("postgresql", "postgres"));
        skills.put("MySQL", List.of("mysql"));
        skills.put("MongoDB", List.of("mongodb", "mongo"));
        skills.put("Redis", List.of("redis"));
        skills.put("Elasticsearch", List.of("elasticsearch", "elastic search"));
        skills.put("Cassandra", List.of("cassandra"));
        skills.put("SQLite", List.of("sqlite"));
        skills.put("Oracle DB", List.of("oracle"));
        skills.put("SQL Server", List.of("sql server", "sqlserver", "ms sql"));
        skills.put("DynamoDB", List.of("dynamodb"));
        skills.put("Neo4j", List.of("neo4j"));
        skills.put("ClickHouse", List.of("clickhouse"));
        // ── DevOps / Cloud ──
        skills.put("Docker", List.of("docker", "docker compose", "docker-compose", "dockerfile", "containerization", "containerized"));
        skills.put("Kubernetes", List.of("kubernetes", "k8s"));
        skills.put("Terraform", List.of("terraform"));
        skills.put("Ansible", List.of("ansible"));
        skills.put("Jenkins", List.of("jenkins"));
        skills.put("CI/CD", List.of("ci/cd", "ci cd", "continuous integration", "continuous delivery", "continuous deployment"));
        skills.put("GitHub Actions", List.of("github actions"));
        skills.put("GitLab CI", List.of("gitlab ci", "gitlab-ci"));
        skills.put("AWS", List.of("aws", "amazon web services", "amazon s3", "s3", "ec2"));
        skills.put("Azure", List.of("azure", "microsoft azure"));
        skills.put("GCP", List.of("gcp", "google cloud", "google cloud platform"));
        skills.put("Helm", List.of("helm"));
        skills.put("Prometheus", List.of("prometheus"));
        skills.put("Grafana", List.of("grafana"));
        skills.put("Linux", List.of("linux"));
        skills.put("Nginx", List.of("nginx"));
        skills.put("Git", List.of("git"));
        skills.put("GitHub", List.of("github", "git hub"));
        skills.put("Serverless", List.of("serverless"));
        skills.put("Istio", List.of("istio"));
        skills.put("RabbitMQ", List.of("rabbitmq", "rabbit mq"));
        skills.put("WebSockets", List.of("websocket", "websockets", "web socket"));
        skills.put("OAuth", List.of("oauth", "oauth2", "oauth 2"));
        skills.put("JWT", List.of("jwt", "json web token"));
        skills.put("Microservices", List.of("microservice", "microservices", "micro-services", "microservices architecture", "spring cloud", "spring-cloud", "eureka", "service discovery", "api gateway", "api-gateway"));
        // ── Testing ──
        skills.put("JUnit", List.of("junit"));
        skills.put("Jest", List.of("jest"));
        skills.put("PyTest", List.of("pytest"));
        skills.put("Selenium", List.of("selenium"));
        skills.put("Cypress", List.of("cypress"));
        skills.put("Playwright", List.of("playwright"));
        skills.put("TestNG", List.of("testng", "test ng"));
        skills.put("Mockito", List.of("mockito"));
        skills.put("TDD", List.of("tdd", "test driven development"));
        skills.put("Test Automation", List.of("test automation", "automated testing"));
        // ── Domains & process ──
        skills.put("Agile", List.of("agile"));
        skills.put("Scrum", List.of("scrum"));
        skills.put("Jira", List.of("jira"));
        skills.put("Security", List.of("cybersecurity", "cyber security", "application security", "information security", "security"));
        skills.put("Blockchain", List.of("blockchain"));
        skills.put("Web3", List.of("web3"));
        skills.put("Fintech", List.of("fintech", "financial technology"));
        skills.put("E-commerce", List.of("e-commerce", "ecommerce", "e commerce"));
        skills.put("Payments", List.of("payments", "payment gateway", "payment processing"));
        skills.put("IoT", List.of("iot", "internet of things"));
        skills.put("Game Development", List.of("game development", "game dev", "unity", "unreal engine"));
        skills.put("Mobile Development", List.of("mobile development", "mobile app"));
        skills.put("Frontend", List.of("frontend", "front-end", "front end"));
        skills.put("Backend", List.of("backend", "back-end", "back end"));
        skills.put("Full Stack", List.of("full stack", "fullstack", "full-stack"));
        return Collections.unmodifiableMap(skills);
    }

    // ── github-service response projections (unknown fields ignored) ──

    record ScoreView(int overallScore, String level) {
        public ScoreView { Objects.requireNonNull(level); }
    }

    record ProfileView(String username, String name, String avatarUrl, String bio) {
    }

    record LanguageView(String language, double percentage) {
    }

    record RepoView(String name, String description, String language, List<String> topics, int stars, String defaultBranch) {
    }

    // ── github-service AI response projections ──

    record AiMatchView(boolean enabled, String model, List<AiExplanationView> explanations) {
    }

    record AiExplanationView(String username, Integer aiRank, String fitLabel, String explanation,
                             List<String> strengths, List<String> gaps, String recommendation) {
    }
}
