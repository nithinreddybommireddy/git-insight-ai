package com.gitinsight.authservice.service;

import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.dto.response.JobMatchResponse.AiExplanation;
import com.gitinsight.authservice.dto.response.JobMatchResponse.JobMatchCandidate;
import com.gitinsight.common.dto.response.ApiResponse;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");

    /**
     * Canonical skill → aliases used to detect it in the job description.
     * Ordered: required skills are reported in this dictionary order.
     */
    private static final Map<String, List<String>> SKILL_ALIASES = buildSkillAliases();

    /** Canonical skill → word-boundary regex over its aliases. */
    private static final Map<String, Pattern> SKILL_PATTERNS = buildSkillPatterns();

    private final RestClient githubClient;

    public JobMatcherService(@Autowired @Value("${app.github-service-url:http://localhost:8081}") String githubServiceUrl) {
        this(buildClient(githubServiceUrl));
    }

    /** Package-private constructor for tests. */
    JobMatcherService(RestClient githubClient) {
        this.githubClient = githubClient;
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
        List<String> required = extractRequiredSkills(jdText);
        List<JobMatchCandidate> results = new ArrayList<>();
        int failed = 0;

        for (String username : usernames) {
            try {
                results.add(analyzeCandidate(username, required));
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

        try {
            AiMatchView ai = fetchAiExplanations(jobTitle, jdText, required, results);
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
            String jobTitle, String jdText, List<String> required, List<JobMatchCandidate> results) {
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

        ApiResponse<AiMatchView> response = githubClient.post()
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

    private JobMatchCandidate analyzeCandidate(String username, List<String> required) {
        ScoreView score = fetch("/api/github/{u}/score", username, ScoreView.class);
        ProfileView profile = fetch("/api/github/{u}/profile", username, ProfileView.class);
        List<LanguageView> languages = fetchList("/api/github/{u}/languages/weighted", username, LanguageView.class);
        List<RepoView> repos = fetchList("/api/github/{u}/repos", username, RepoView.class);

        String corpus = buildCandidateCorpus(profile, languages, repos);

        List<String> matched = required.stream().filter(s -> matches(corpus, s)).collect(Collectors.toList());
        List<String> missing = required.stream().filter(s -> !matched.contains(s)).collect(Collectors.toList());

        int skillMatchPercent = required.isEmpty() ? 100
                : (int) Math.round(matched.size() * 100.0 / required.size());
        int developerScore = score.overallScore();

        List<String> topLanguages = languages.stream()
                .sorted(Comparator.comparingDouble(LanguageView::percentage).reversed())
                .limit(8)
                .map(LanguageView::language)
                .collect(Collectors.toList());
        List<String> topRepos = repos.stream()
                .sorted(Comparator.comparingInt(RepoView::stars).reversed())
                .limit(5)
                .map(RepoView::name)
                .collect(Collectors.toList());

        return new JobMatchCandidate(username, profile.name(), profile.avatarUrl(), profile.bio(),
                developerScore, score.level(), computeMatchScore(skillMatchPercent, developerScore),
                skillMatchPercent, matched, missing, topLanguages, topRepos);
    }

    private String buildCandidateCorpus(ProfileView profile, List<LanguageView> languages, List<RepoView> repos) {
        StringBuilder sb = new StringBuilder(512);
        if (profile.bio() != null) sb.append(profile.bio()).append(' ');
        for (LanguageView l : languages) sb.append(l.language()).append(' ');
        for (RepoView r : repos) {
            sb.append(r.name()).append(' ');
            if (r.description() != null) sb.append(r.description()).append(' ');
            if (r.language() != null) sb.append(r.language()).append(' ');
            if (r.topics() != null) sb.append(String.join(" ", r.topics())).append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private <T> T fetch(String path, String username, Class<T> type) {
        ApiResponse<T> response = githubClient.get()
                .uri(path, username)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<T>>() {});
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("github-service returned no data for " + username);
        }
        return response.getData();
    }

    private <T> List<T> fetchList(String path, String username, Class<T> elementType) {
        ApiResponse<List<T>> response = githubClient.get()
                .uri(path, username)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<T>>>() {});
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    private static RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private static String extractPdfText(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the PDF job description: " + e.getMessage(), e);
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
        skills.put("Spring Boot", List.of("spring boot", "springboot", "spring framework", "spring mvc", "spring cloud", "spring security", "spring data"));
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
        skills.put("REST API", List.of("rest api", "restful", "rest apis", "rest services"));
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
        skills.put("Docker", List.of("docker", "docker compose", "docker-compose"));
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
        skills.put("Microservices", List.of("microservice", "microservices", "micro-services"));
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

    record RepoView(String name, String description, String language, List<String> topics, int stars) {
    }

    // ── github-service AI response projections ──

    record AiMatchView(boolean enabled, String model, List<AiExplanationView> explanations) {
    }

    record AiExplanationView(String username, Integer aiRank, String fitLabel, String explanation,
                             List<String> strengths, List<String> gaps, String recommendation) {
    }
}
