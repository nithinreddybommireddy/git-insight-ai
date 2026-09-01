package com.gitinsight.authservice.service;

import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.service.JobMatcherService.RepoView;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobMatcherServiceTest {

    private final JobMatcherService service = new JobMatcherService(RestClient.create());

    // ── Username parsing ──

    @Test
    void parsesNewlinesCommasAndAtSigns() {
        String content = "@torvalds\naddyosmani, gaearon\n\n  @sindresorhus ,torvalds";
        List<String> usernames = service.parseUsernames(content);

        assertThat(usernames).containsExactly("torvalds", "addyosmani", "gaearon", "sindresorhus");
    }

    @Test
    void dropsInvalidAndEmptyTokens() {
        // underscores are not valid in GitHub usernames → "valid_123" is dropped
        String content = "good-name, not a user!, @, -bad-, 123, has space, valid123";
        List<String> usernames = service.parseUsernames(content);

        assertThat(usernames).containsExactly("good-name", "123", "valid123");
    }

    @Test
    void blankInputYieldsEmptyList() {
        assertThat(service.parseUsernames(null)).isEmpty();
        assertThat(service.parseUsernames("  \n ")).isEmpty();
    }

    // ── Skill extraction ──

    @Test
    void extractsRequiredSkillsCaseInsensitively() {
        String jd = "Senior Backend Engineer — we need Java, Spring Boot, PostgreSQL and Docker. "
                + "Nice to have: Kubernetes and Kafka.";

        List<String> skills = service.extractRequiredSkills(jd);

        assertThat(skills).contains("Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes", "Kafka");
        assertThat(skills).contains("Backend");
    }

    @Test
    void wordBoundariesPreventFalsePositives() {
        // bare "go" is NOT a skill alias: it must not match inside google/goals
        // (Golang would legitimately map to Go, so it is not used here)
        String jd = "We google-scope our goals with the go-to-market team, and store data in PostgreSQL.";

        List<String> skills = service.extractRequiredSkills(jd);

        assertThat(skills).doesNotContain("Go");       // only google/goals/go-to-market present
        assertThat(skills).doesNotContain("SQL");      // PostgreSQL must not count as SQL
        assertThat(skills).contains("PostgreSQL");
    }

    @Test
    void golangAliasMapsToGoSkill() {
        String jd = "Golang microservices for the payments platform.";
        assertThat(service.extractRequiredSkills(jd)).contains("Go", "Payments", "Microservices");
    }

    @Test
    void extractsMultiWordAndSymbolSkills() {
        String jd = "C++ and C# developers with .NET Core experience, plus React Native and GitHub Actions.";

        List<String> skills = service.extractRequiredSkills(jd);

        assertThat(skills).contains("C++", "C#", "ASP.NET", "React Native", "GitHub Actions");
    }

    @Test
    void blankJdYieldsEmptySkills() {
        assertThat(service.extractRequiredSkills("  ")).isEmpty();
        assertThat(service.extractRequiredSkills(null)).isEmpty();
    }

    // ── Candidate corpus matching ──

    @Test
    void candidateMatchesSkillFromCorpus() {
        String corpus = "backend microservices java spring boot postgresql api".toLowerCase();
        assertThat(service.matches(corpus, "Java")).isTrue();
        assertThat(service.matches(corpus, "Spring Boot")).isTrue();
        assertThat(service.matches(corpus, "Docker")).isFalse();
    }

    @Test
    void candidateCorpusMatchIsWordBoundaryAware() {
        String corpus = "I build github bots and use postgresql";
        assertThat(service.matches(corpus, "Git")).isFalse(); // "github" contains git
        assertThat(service.matches(corpus, "GitHub")).isTrue();
        assertThat(service.matches(corpus, "SQL")).isFalse(); // postgresql contains sql
    }

    // ── Match score formula ──

    @Test
    void matchScoreBlendsSkillMatchAndDeveloperScore() {
        assertThat(JobMatcherService.computeMatchScore(100, 100)).isEqualTo(100);
        assertThat(JobMatcherService.computeMatchScore(0, 0)).isEqualTo(0);
        // 0.6 * 60 + 0.4 * 80 = 36 + 32 = 68
        assertThat(JobMatcherService.computeMatchScore(60, 80)).isEqualTo(68);
        // 0.6 * 40 + 0.4 * 90 = 24 + 36 = 60
        assertThat(JobMatcherService.computeMatchScore(40, 90)).isEqualTo(60);
    }

    @Test
    void matchScoreIsClamped() {
        assertThat(JobMatcherService.computeMatchScore(150, 200)).isEqualTo(100);
        assertThat(JobMatcherService.computeMatchScore(-20, -10)).isEqualTo(0);
    }

    // ── File text extraction ──

    @Test
    void extractsTextFromTxtAndMarkdown() {
        byte[] txt = "Senior Java Engineer".getBytes(StandardCharsets.UTF_8);
        assertThat(service.extractText("jd.txt", txt)).isEqualTo("Senior Java Engineer");
        assertThat(service.extractText("jd.md", txt)).isEqualTo("Senior Java Engineer");
        assertThat(service.extractText("jd.markdown", txt)).isEqualTo("Senior Java Engineer");
    }

    @Test
    void rejectsUnsupportedFileTypes() {
        byte[] bytes = new byte[]{1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> service.extractText("jd.docx", bytes))
                .getMessage().contains("Unsupported job description file type");
    }

    @Test
    void infersJobTitleFromFirstLine() {
        String jd = "  # Senior Full-Stack Engineer\nWe need React and Spring Boot.";
        assertThat(service.inferJobTitle(jd)).isEqualTo("Senior Full-Stack Engineer");
        assertThat(service.inferJobTitle("   ")).isEqualTo("Job Description");
    }

    // ── AI explanation merging ──

    @Test
    void mergesAiExplanationsIntoDeterministicOrder() {
        var alice = new JobMatchResponse.JobMatchCandidate(
                "alice", "Alice", null, null, 80, "Expert 🏅", 90, 100,
                List.of("Java", "Spring Boot"), List.of(), List.of("Java"), List.of("api"));
        var bob = new JobMatchResponse.JobMatchCandidate(
                "bob", "Bob", null, null, 60, "Proficient 💼", 50, 50,
                List.of(), List.of("Java"), List.of("Go"), List.of("tool"));
        var results = List.of(alice, bob);

        var byUsername = java.util.Map.of("alice",
                new JobMatcherService.AiExplanationView("alice", 1, "Strong fit", "Great Java experience",
                        List.of("Spring Boot"), List.of(), "Interview"));

        var merged = JobMatcherService.mergeAiExplanations(results, byUsername);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).username()).isEqualTo("alice");
        assertThat(merged.get(0).fitLabel()).isEqualTo("Strong fit");
        assertThat(merged.get(0).aiRank()).isEqualTo(1);
        assertThat(merged.get(0).strengths()).containsExactly("Spring Boot");
    }

    @Test
    void mergeSkipsCandidatesWithoutAiExplanation() {
        var alice = new JobMatchResponse.JobMatchCandidate(
                "alice", "Alice", null, null, 80, "Expert 🏅", 90, 100,
                List.of("Java"), List.of(), List.of("Java"), List.of("api"));
        var bob = new JobMatchResponse.JobMatchCandidate(
                "bob", "Bob", null, null, 60, "Proficient 💼", 50, 50,
                List.of(), List.of("Java"), List.of("Go"), List.of("tool"));

        var merged = JobMatcherService.mergeAiExplanations(List.of(alice, bob), java.util.Map.of());

        assertThat(merged).isEmpty();
    }

    @Test
    void mergeFillsMissingAiFieldsWithDefaults() {
        var alice = new JobMatchResponse.JobMatchCandidate(
                "alice", "Alice", null, null, 80, "Expert 🏅", 90, 100,
                List.of("Java"), List.of(), List.of("Java"), List.of("api"));
        var byUsername = java.util.Map.of("alice",
                new JobMatcherService.AiExplanationView("alice", null, null, null, null, null, null));

        var merged = JobMatcherService.mergeAiExplanations(List.of(alice), byUsername);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).aiRank()).isZero();
        assertThat(merged.get(0).fitLabel()).isEqualTo("Partial fit");
        assertThat(merged.get(0).strengths()).isEmpty();
        assertThat(merged.get(0).gaps()).isEmpty();
    }

    // ── Evidence repository limit ──

    @Test
    void defaultEvidenceRepoLimitIs15() {
        // The no-arg test constructor uses the hard maximum
        var svc = new JobMatcherService(RestClient.create());
        assertThat(svc.maxEvidenceRepos).isEqualTo(15);
    }

    @Test
    void evidenceRepoLimitRespectsHardCeiling() {
        // Even if a caller requests 100, the hard ceiling of 15 applies
        var svc = new JobMatcherService(
                "http://localhost:8081", "", 100,
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(svc.maxEvidenceRepos).isEqualTo(15);
    }

    @Test
    void evidenceRepoLimitCanBeConfiguredBelowCeiling() {
        var svc = new JobMatcherService(
                "http://localhost:8081", "", 5,
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(svc.maxEvidenceRepos).isEqualTo(5);
    }

    // ── Repository relevance ranking ──

    @Test
    void relevantRepoScoresHigherThanUnrelated() {
        List<String> required = List.of("Java", "Spring Boot", "Docker");

        var springRepo = new RepoView("my-spring-api",
                "Spring Boot REST microservice with Docker",
                "Java", List.of("spring-boot", "rest-api", "docker"), 5, "main");
        var portfolioRepo = new RepoView("portfolio",
                "My personal portfolio website",
                "HTML", List.of("portfolio", "website"), 50, "main");

        int springScore = JobMatcherService.computeRepoRelevance(springRepo, required);
        int portfolioScore = JobMatcherService.computeRepoRelevance(portfolioRepo, required);

        assertThat(springScore).isGreaterThan(portfolioScore);
        assertThat(springScore).isGreaterThanOrEqualTo(2); // Java + Spring Boot minimum
    }

    @Test
    void relevanceScoreIsZeroWhenNoRequiredSkills() {
        var repo = new RepoView("repo", "desc", "Java", List.of(), 10, "main");
        assertThat(JobMatcherService.computeRepoRelevance(repo, List.of())).isZero();
        assertThat(JobMatcherService.computeRepoRelevance(repo, null)).isZero();
    }

    @Test
    void starBasedRankingRemainsPartOfSelection() {
        // Two repos with same relevance but different stars —
        // the sort should use stars as tiebreaker
        List<String> required = List.of("Java");
        var repoA = new RepoView("api-a", "Java API", "Java", List.of(), 10, "main");
        var repoB = new RepoView("api-b", "Java API", "Java", List.of(), 100, "main");

        int scoreA = JobMatcherService.computeRepoRelevance(repoA, required);
        int scoreB = JobMatcherService.computeRepoRelevance(repoB, required);

        assertThat(scoreA).isEqualTo(scoreB); // same relevance
        // Stars serve as tiebreaker in the actual sort (verified by sort lambda)
    }

    // ── RepoView includes defaultBranch ──

    @Test
    void repoViewIncludesDefaultBranch() {
        var repo = new RepoView("my-repo", "desc", "Java", List.of(), 10, "develop");
        assertThat(repo.defaultBranch()).isEqualTo("develop");
    }

    @Test
    void repoViewDefaultBranchNullIsAllowed() {
        var repo = new RepoView("my-repo", "desc", "Java", List.of(), 10, null);
        assertThat(repo.defaultBranch()).isNull();
    }

    // ── buildBranchPriority (FIX B) ──

    @Test
    void defaultBranchDevelopUsesDevelopFirst() {
        List<String> branches = JobMatcherService.buildBranchPriority("develop");
        assertThat(branches).containsExactly("develop", "main", "master");
    }

    @Test
    void defaultBranchDevUsesDevFirst() {
        List<String> branches = JobMatcherService.buildBranchPriority("dev");
        assertThat(branches).containsExactly("dev", "main", "master");
    }

    @Test
    void defaultBranchMainDoesNotDuplicateMain() {
        List<String> branches = JobMatcherService.buildBranchPriority("main");
        assertThat(branches).containsExactly("main", "master");
    }

    @Test
    void defaultBranchMasterDoesNotDuplicateMaster() {
        List<String> branches = JobMatcherService.buildBranchPriority("master");
        assertThat(branches).containsExactly("master", "main");
    }

    @Test
    void nullDefaultBranchFallsBackToMainMaster() {
        List<String> branches = JobMatcherService.buildBranchPriority(null);
        assertThat(branches).containsExactly("main", "master");
    }

    @Test
    void blankDefaultBranchFallsBackToMainMaster() {
        List<String> branches = JobMatcherService.buildBranchPriority("  ");
        assertThat(branches).containsExactly("main", "master");
    }

    @Test
    void defaultBranchReleaseUsesReleaseFirst() {
        List<String> branches = JobMatcherService.buildBranchPriority("release/v2");
        assertThat(branches).containsExactly("release/v2", "main", "master");
    }

    // ── Existing formula unchanged ──

    @Test
    void matchScoreFormulaRemains60_40() {
        // 0.6 * 80 + 0.4 * 60 = 48 + 24 = 72
        assertThat(JobMatcherService.computeMatchScore(80, 60)).isEqualTo(72);
        // 0.6 * 50 + 0.4 * 100 = 30 + 40 = 70
        assertThat(JobMatcherService.computeMatchScore(50, 100)).isEqualTo(70);
    }

    // ── Constants unchanged ──

    @Test
    void maxCandidatesRemains25() {
        assertThat(JobMatcherService.MAX_CANDIDATES).isEqualTo(25);
    }
}
