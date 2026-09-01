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

    /** Maximum source files to fetch per repository (bounded GitHub requests). */
    private static final int MAX_SOURCE_FILES_PER_REPO = 5;

    /** Maximum directory depth when browsing the repo tree for source files. */
    private static final int SOURCE_TREE_DEPTH = 3;

    /** High-signal file names to look for during source discovery. */
    private static final Set<String> SOURCE_FILE_NAMES = Set.of(
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

        MatchContext(long deadlineNanos, int evidenceRequestBudget) {
            this.deadlineNanos = deadlineNanos;
            this.evidenceRequestBudget = evidenceRequestBudget;
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
         * 2. Per-candidate evidence time limit (capped to remaining global time)
         * 3. Per-candidate request count
         *
         * If ANY guard triggers, optional evidence collection stops.
         */
        boolean evidenceBudgetExhausted() {
            long now = System.nanoTime();
            long remainingGlobalNanos = deadlineNanos - now;
            long perCandidateMaxNanos = TimeUnit.MILLISECONDS.toNanos(
                    Math.min(MAX_EVIDENCE_TIME_MS_PER_CANDIDATE,
                            TimeUnit.NANOSECONDS.toMillis(remainingGlobalNanos)));
            return now >= deadlineNanos
                    || (now - evidenceStartNanos) > perCandidateMaxNanos
                    || evidenceRequestBudget <= 0;
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
        this.githubServiceBaseUrl = githubServiceUrl;
        this.githubApiKey = internalApiKey;
        this.githubClient = buildClient(githubServiceUrl, internalApiKey);
        this.rawGithubClient = buildRawGithubClient();
        this.objectMapper = objectMapper;
        this.maxEvidenceRepos = Math.min(maxEvidenceRepos, HARD_MAX_EVIDENCE_REPOS);
    }

    /** Package-private constructor for tests. */
    JobMatcherService(RestClient githubClient) {
        this.githubClient = githubClient;
        this.rawGithubClient = buildRawGithubClient();
        this.objectMapper = new ObjectMapper();
        this.maxEvidenceRepos = HARD_MAX_EVIDENCE_REPOS;
        this.githubServiceBaseUrl = "http://localhost:8081";
        this.githubApiKey = "";
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
            out.add(new AiExplanation(
                    c.username(),
                    v.aiRank() != null ? v.aiRank() : 0,
                    nz(v.fitLabel(), "Partial fit"),
                    nz(v.explanation(), ""),
                    v.strengths() == null ? List.of() : v.strengths(),
                    v.gaps() == null ? List.of() : v.gaps(),
                    nz(v.recommendation(), "")));
        }
        return out;
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
        ctx.evidenceRequestBudget = REQUEST_BUDGET_PER_CANDIDATE;
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
        String corpus = buildCandidateCorpus(username, profile, languages, repos, required, stats, ctx);

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
                skillMatchPercent, matched, missing, topLanguages, topRepos);
    }

    /**
     * Build a deterministic skill corpus from public GitHub evidence.
     * In addition to the existing profile/repository metadata, we inspect a small
     * bounded set of README/build/deployment files and high-signal source files
     * from the top repositories.
     */
    private String buildCandidateCorpus(
            String username,
            ProfileView profile,
            List<LanguageView> languages,
            List<RepoView> repos,
            List<String> required,
            EvidenceStats stats,
            MatchContext ctx) {

        StringBuilder sb = new StringBuilder(16_000);

        if (profile != null && profile.bio() != null) {
            sb.append(profile.bio()).append(' ');
        }

        for (LanguageView l : languages) {
            if (l != null && l.language() != null) {
                sb.append(l.language()).append(' ');
            }
        }

        // Metadata is cheap and remains the primary evidence source.
        // Repos are ranked by relevance to required skills (metadata match)
        // then by stars as a tiebreaker, so skill-relevant repos are inspected first.
        List<RepoView> topRepos = repos.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((RepoView r) -> -computeRepoRelevance(r, required))
                        .thenComparingInt(r -> -r.stars()))
                .limit(maxEvidenceRepos)
                .toList();

        stats.reposAttempted = topRepos.size();

        for (RepoView r : topRepos) {
            sb.append(r.name()).append(' ');
            if (r.description() != null) sb.append(r.description()).append(' ');
            if (r.language() != null) sb.append(r.language()).append(' ');
            if (r.topics() != null) sb.append(String.join(" ", r.topics())).append(' ');

            EvidenceResult er = fetchRepositoryEvidence(username, r.name(), r.defaultBranch(), required, stats, ctx);
            if (!er.content.isBlank()) {
                sb.append(' ').append(er.content).append(' ');
                stats.reposWithEvidence++;
            }
            stats.filesFound += er.filesFound;
            stats.filesMissing += er.filesMissing;
        }

        return sb.toString().toLowerCase(Locale.ROOT);
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
    static List<String> evidenceFilesFor(List<String> required) {
        List<String> files = new ArrayList<>();
        files.add("README.md");

        Set<String> normalized = required == null
                ? Set.of()
                : required.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

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

        boolean dockerEcosystem = normalized.stream().anyMatch(s ->
                s.contains("docker") ||
                        s.contains("kubernetes") ||
                        s.contains("ci/cd"));

        if (javaEcosystem) {
            // Primary build file first, then fallbacks
            files.add("pom.xml");
            files.add("build.gradle");
            files.add("build.gradle.kts");
            // Configuration files (fetched only if still needed)
            files.add("application.yml");
            files.add("application.yaml");
            files.add("application.properties");
        }

        if (javascriptEcosystem) {
            files.add("package.json");
        }

        if (dockerEcosystem) {
            // Primary Docker file first, then fallbacks
            files.add("Dockerfile");
            files.add("docker-compose.yml");
            files.add("docker-compose.yaml");
        }

        return List.copyOf(files);
    }

    private record EvidenceResult(String content, String branchUsed, int filesFound, int filesMissing) {}

    private static class EvidenceStats {
        int reposAttempted;
        int reposWithEvidence;
        int filesFound;
        int filesMissing;
        int sourceFilesDiscovered;
        int sourceFilesFound;
        int sourceFilesMissing;
        List<String> sourceEvidenceSkills = new ArrayList<>();
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
     */
    private FileResult budgetedFetch(String owner, String repo, String branch, String file, MatchContext ctx) {
        if (ctx.evidenceBudgetExhausted()) return null;
        ctx.evidenceRequestBudget--;
        return fetchRawRepositoryFile(owner, repo, branch, file, ctx);
    }

    /**
     * Budget-aware directory API call for source discovery. Returns null if
     * budget (count or time) is exhausted.
     */
    private Object budgetedDirFetch(String owner, String repo, String branch, String path, MatchContext ctx) {
        if (ctx.evidenceBudgetExhausted()) return null;
        ctx.evidenceRequestBudget--;
        try {
            RestClient client = dynamicTimeoutClient(ctx.remainingTimeMs(), 5_000, 20_000);
            return client.get()
                    .uri("/api/github/{owner}/{repo}/contents/{path}?ref={branch}", owner, repo, path, branch)
                    .retrieve()
                    .body(Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch evidence for a single repository with progressive fetching and
     * per-candidate request budgeting.
     *
     * <p>Cache design: NO repo-level aggregate cache. Each evidence file is cached
     * independently at the file level (owner/repo/branch/file).
     *
     * <p>Evidence pipeline per branch (progressive, budget-aware):
     * <ol>
     *   <li>README (always fetched — broad evidence)</li>
     *   <li>Primary build file (pom.xml / package.json / Dockerfile)</li>
     *   <li>Fallback build files ONLY if primary missing AND skills still uncovered</li>
     *   <li>Config files ONLY if skills still uncovered</li>
     *   <li>Source discovery ONLY if all build/config evidence insufficient</li>
     * </ol>
     */
    private EvidenceResult fetchRepositoryEvidence(String owner, String repo, String defaultBranch,
                                                   List<String> required, EvidenceStats stats,
                                                   MatchContext ctx) {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            return new EvidenceResult("", "", 0, 0);
        }

        List<String> branches = buildBranchPriority(defaultBranch);
        String bestEvidence = "";
        String branchUsed = "";
        int totalFound = 0;
        int totalMissing = 0;

        // Track confirmed skills ACROSS all branch attempts.
        // Once a skill is confirmed from any branch's evidence, it stays confirmed.
        // Stopping is based on skill confirmation, not file existence.
        Set<String> confirmedSkills = new HashSet<>();

        for (String branch : branches) {
            if (ctx.evidenceBudgetExhausted()) break;

            // If ALL required skills are already confirmed from a previous branch, skip
            boolean allSkillsConfirmed = !required.isEmpty()
                    && required.stream().allMatch(confirmedSkills::contains);
            if (allSkillsConfirmed) break;

            StringBuilder branchEvidence = new StringBuilder();
            int found = 0;
            int missing = 0;
            StringBuilder corpusBuilder = new StringBuilder();

            // Progressive evidence fetching: fetch files in priority order,
            // check skills after each file, stop when all skills are confirmed.
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
                    corpusBuilder.append(fr.content).append(' ');
                    found++;

                    // Skill-based early stopping: check which skills are confirmed
                    if (!required.isEmpty()) {
                        String corpus = corpusBuilder.toString().toLowerCase(Locale.ROOT);
                        for (String skill : required) {
                            if (!confirmedSkills.contains(skill) && matches(corpus, skill)) {
                                confirmedSkills.add(skill);
                            }
                        }
                    }
                } else {
                    missing++;
                }

                // If all skills confirmed, skip remaining build/config files for this branch
                allSkillsConfirmed = !required.isEmpty()
                        && required.stream().allMatch(confirmedSkills::contains);
                if (allSkillsConfirmed) break;
            }

            // Source discovery: ONLY when skills are still unconfirmed
            if (!allSkillsConfirmed && !required.isEmpty() && !ctx.evidenceBudgetExhausted()) {
                SourceEvidenceResult ser = fetchSourceEvidence(owner, repo, branch, required, stats, ctx);
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

            if (!branchEvidence.isEmpty()) {
                bestEvidence = branchEvidence.toString();
                branchUsed = branch;
                totalFound = found;
                totalMissing = missing;
                break;
            }
            if (branchUsed.isEmpty()) {
                branchUsed = branch;
                totalFound = found;
                totalMissing = missing;
            }
        }

        if (bestEvidence.length() > MAX_TOTAL_EVIDENCE_PER_REPO) {
            bestEvidence = bestEvidence.substring(0, MAX_TOTAL_EVIDENCE_PER_REPO);
        }

        log.debug("repo={} defaultBranch={} branchUsed={} evidenceFilesFound={} evidenceFilesMissing={}",
                owner + "/" + repo, defaultBranch, branchUsed, totalFound, totalMissing);
        return new EvidenceResult(bestEvidence, branchUsed, totalFound, totalMissing);
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
                                                     MatchContext ctx) {
        // Discover source file paths (bounded directory API calls)
        List<String> sourcePaths = discoverSourceFiles(owner, repo, branch, required, ctx);
        if (sourcePaths.isEmpty()) {
            return new SourceEvidenceResult("", 0, 0, List.of());
        }

        StringBuilder evidence = new StringBuilder();
        int fetched = 0;
        int failed = 0;
        List<String> skillsDetected = new ArrayList<>();

        for (String path : sourcePaths) {
            if (fetched >= MAX_SOURCE_FILES_PER_REPO) break;

            // Early stopping: stop once all required skills are covered
            if (required.stream().allMatch(s -> skillsDetected.contains(s))) {
                break;
            }

            FileResult fr = budgetedFetch(owner, repo, branch, path, ctx);
            if (fr == null) break; // budget exhausted
            if (!fr.content.isBlank()) {
                String evidenceText = extractSourceEvidence(fr.content, path, required);
                if (!evidenceText.isBlank()) {
                    evidence.append("\n[file ").append(path).append("]\n");
                    evidence.append(evidenceText).append('\n');
                    fetched++;
                    // Track which skills this source file helped detect
                    for (String skill : required) {
                        if (!skillsDetected.contains(skill) && sourcePatternMatches(fr.content, skill)) {
                            skillsDetected.add(skill);
                        }
                    }
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
     */    private List<String> discoverSourceFiles(String owner, String repo, String branch, List<String> required, MatchContext ctx) {
        List<String> paths = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        try {
            // Budget-aware root directory listing
            Object rawResponse = budgetedDirFetch(owner, repo, branch, "", ctx);
            if (rawResponse == null) return paths;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rootItems = objectMapper.convertValue(
                    rawResponse,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            List<String> javaSrcDirs = new ArrayList<>();

            for (Map<String, Object> item : rootItems) {
                String name = (String) item.get("name");
                String type = (String) item.get("type");
                if (name == null || type == null) continue;

                if ("file".equals(type) && SOURCE_FILE_NAMES.contains(name)) {
                    if (seen.add(name)) paths.add(name);
                }
                if ("dir".equals(type) && "src".equals(name)) {
                    javaSrcDirs.add(name);
                }
            }

            // Explore src/main/java — only relevant subdirectories
            for (String srcDir : javaSrcDirs) {
                if (ctx.evidenceRequestBudget <= 0) break;
                List<String> javaPaths = discoverJavaSourcePaths(owner, repo, branch,
                        srcDir + "/main/java", required, ctx);
                for (String p : javaPaths) {
                    if (seen.add(p)) paths.add(p);
                }
            }

        } catch (Exception e) {
            log.debug("Source discovery failed for {}/{} branch={}: {}", owner, repo, branch, e.getMessage());
        }

        if (paths.size() > MAX_SOURCE_FILES_PER_REPO) {
            paths = paths.subList(0, MAX_SOURCE_FILES_PER_REPO);
        }
        return paths;
    }

    private List<String> discoverJavaSourcePaths(String owner, String repo, String branch,
                                                  String basePath, List<String> required,
                                                  MatchContext ctx) {
        List<String> paths = new ArrayList<>();
        Set<String> normalizedRequired = required == null ? Set.of()
                : required.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> relevantPackages = filterRelevantPackages(normalizedRequired);

        try {
            Object rawResponse = budgetedDirFetch(owner, repo, branch, basePath, ctx);
            if (rawResponse == null) return paths;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = objectMapper.convertValue(
                    rawResponse,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

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

            int depth = 0;
            while (!dirsToExplore.isEmpty() && depth < SOURCE_TREE_DEPTH
                    && paths.size() < MAX_SOURCE_FILES_PER_REPO && ctx.evidenceRequestBudget > 0) {
                String dir = dirsToExplore.poll();
                try {
                    Object dirResponse = budgetedDirFetch(owner, repo, branch, dir, ctx);
                    if (dirResponse == null) continue;

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dirItems = objectMapper.convertValue(
                            dirResponse,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

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
                s.contains("gateway") || s.contains("eureka"));
        boolean needsRepository = normalizedRequired.stream().anyMatch(s ->
                s.contains("sql") || s.contains("database") || s.contains("jpa") ||
                s.contains("hibernate") || s.contains("data"));
        boolean needsGateway = normalizedRequired.stream().anyMatch(s ->
                s.contains("gateway") || s.contains("microservice") || s.contains("routing"));

        if (needsControllers) relevant.add("controller");
        if (needsServices) relevant.add("service");
        if (needsConfig) { relevant.add("config"); relevant.add("configuration"); }
        if (needsRepository) { relevant.add("repository"); relevant.add("repo"); }
        if (needsGateway) { relevant.add("gateway"); relevant.add("filter"); }
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
            if (sourcePatternMatches(content, skill)) {
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
                boolean hasEureka = lower.contains("eureka") || lower.contains("enableeurekaclient") || lower.contains("enablediscoveryclient");
                boolean hasFeign = lower.contains("@feignclient");
                boolean hasGateway = lower.contains("spring cloud gateway") || lower.contains("spring-cloud-gateway") || lower.contains("gatewayconfig");
                boolean hasServiceDiscovery = lower.contains("service discovery") || lower.contains("service-discovery");
                boolean hasMultipleModules = lower.contains("multi-module") || lower.contains("multi module");

                // Strong single signals
                if (hasEureka || hasFeign || hasGateway || hasServiceDiscovery || hasMultipleModules) return true;

                // Spring Cloud + any service pattern
                if (hasSpringCloud && (lower.contains("service") || lower.contains("module"))) return true;

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
                // HIGH-evidence: JPA/SQL annotations, .sql files, Spring Data repositories
                return lower.contains("@entity") ||
                        lower.contains("@table") ||
                        lower.contains("@column") ||
                        lower.contains("@repository") ||
                        lower.contains("@query") ||
                        lower.contains("@jpql") ||
                        lower.contains("jparepository") ||
                        lower.contains("crudrepository") ||
                        lower.contains("jdbc") ||
                        lower.contains("datasource") ||
                        lower.contains("flyway") ||
                        lower.contains("liquibase") ||
                        lower.contains("create table") ||
                        lower.contains("select ") ||
                        lower.contains("insert into");
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
                        lowerLine.contains("spring cloud") || lowerLine.contains("gatewayconfig");
                case "spring boot" -> lowerLine.contains("@springbootapplication") ||
                        lowerLine.contains("spring-boot-starter") || lowerLine.contains("springapplication.run");
                case "sql" -> lowerLine.contains("@entity") || lowerLine.contains("@table") ||
                        lowerLine.contains("@query") || lowerLine.contains("create table");
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
        return "";
    }

    // ────────────────────────── Raw file fetching ──────────────────────────

    private record FileResult(String content, String errorType) {}

    private FileResult fetchRawRepositoryFile(String owner, String repo, String branch, String file,
                                                MatchContext ctx) {
        String key = owner + "/" + repo + "/" + branch + "/" + file;
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
