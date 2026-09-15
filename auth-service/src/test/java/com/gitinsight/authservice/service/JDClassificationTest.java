package com.gitinsight.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JD skill classification (required / preferred / mandatory) and
 * mandatory-weighted skill matching — the recruiter "Run Job" accuracy fix.
 *
 * <p>These tests verify the whole JD is processed (a "Mandatory requirements"
 * section near the END is honored) and that mandatory skills count double in
 * the skill-match percentage.
 */
class JDClassificationTest {

    private final JobMatcherService svc = new JobMatcherService(
            org.springframework.web.client.RestClient.create());

    /** The Java Full Stack JD shape: mandatory requirements listed near the END. */
    private static final String JAVA_FULL_STACK_JD = """
            Java Full Stack Developer

            We are looking for a Java Full Stack Developer with experience in Java,
            Spring Boot, React.js, REST APIs, SQL, Git, and Docker.

            Preferred skills:
            - Microservices
            - GitHub
            - Problem-solving
            - AI/ML knowledge

            Responsibilities:
            - Build scalable backend services...
            - Develop React applications...
            - Design REST APIs...
            - Work with PostgreSQL...
            - Implement CI/CD...
            - Deploy applications using Docker...

            Mandatory requirements near the END:
            - Kubernetes
            - AWS
            - Kafka
            - Redis
            """;

    @Test
    void mandatorySkillsNearTheEndAreClassified() {
        Map<String, JobMatcherService.SkillCategory> cls =
                svc.extractSkillClassification(JAVA_FULL_STACK_JD);

        assertThat(cls.get("Kubernetes")).isEqualTo(JobMatcherService.SkillCategory.MANDATORY);
        assertThat(cls.get("AWS")).isEqualTo(JobMatcherService.SkillCategory.MANDATORY);
        assertThat(cls.get("Kafka")).isEqualTo(JobMatcherService.SkillCategory.MANDATORY);
        assertThat(cls.get("Redis")).isEqualTo(JobMatcherService.SkillCategory.MANDATORY);
    }

    @Test
    void preferredSectionIsClassifiedAsPreferred() {
        Map<String, JobMatcherService.SkillCategory> cls =
                svc.extractSkillClassification(JAVA_FULL_STACK_JD);

        assertThat(cls.get("Microservices")).isEqualTo(JobMatcherService.SkillCategory.PREFERRED);
        assertThat(cls.get("GitHub")).isEqualTo(JobMatcherService.SkillCategory.PREFERRED);
        assertThat(cls.get("Machine Learning")).isEqualTo(JobMatcherService.SkillCategory.PREFERRED);
    }

    @Test
    void bodyAndResponsibilitySkillsAreRequired() {
        Map<String, JobMatcherService.SkillCategory> cls =
                svc.extractSkillClassification(JAVA_FULL_STACK_JD);

        assertThat(cls.get("Java")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        assertThat(cls.get("Spring Boot")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        assertThat(cls.get("React")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        assertThat(cls.get("REST API")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        assertThat(cls.get("SQL")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        assertThat(cls.get("Docker")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        // Responsibilities section resets to REQUIRED after Preferred.
        assertThat(cls.get("PostgreSQL")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
        assertThat(cls.get("CI/CD")).isEqualTo(JobMatcherService.SkillCategory.REQUIRED);
    }

    @Test
    void mandatorySectionDetectionIsCaseAndMarkdownTolerant() {
        String jd = """
                some intro
                ## MUST HAVE:
                - docker
                * nice to have:
                - kubernetes
                Mandatory Skills:
                - redis
                """;
        Map<String, JobMatcherService.SkillCategory> cls = svc.extractSkillClassification(jd);

        assertThat(cls.get("Docker")).isEqualTo(JobMatcherService.SkillCategory.MANDATORY);
        assertThat(cls.get("Kubernetes")).isEqualTo(JobMatcherService.SkillCategory.PREFERRED);
        assertThat(cls.get("Redis")).isEqualTo(JobMatcherService.SkillCategory.MANDATORY);
    }

    @Test
    void skillNamedInTwoSectionsKeepsFirstClassification() {
        String jd = """
                Preferred skills:
                - Docker

                Mandatory requirements:
                - Docker
                """;
        Map<String, JobMatcherService.SkillCategory> cls = svc.extractSkillClassification(jd);
        // First mention wins: PREFERRED (before the Mandatory header).
        assertThat(cls.get("Docker")).isEqualTo(JobMatcherService.SkillCategory.PREFERRED);
    }

    @Test
    void weightedPercentCountsMandatorySkillsDouble() {
        List<String> required = List.of("Java", "Docker", "Redis");
        // Mandatory: Redis. Candidate matches Java + Docker, misses Redis.
        int pct = JobMatcherService.computeWeightedSkillMatchPercent(
                required, List.of("Java", "Docker"), Set.of("Redis"));

        // Weights: Java 1 + Docker 1 + Redis 2 = 4; matched = 2 → 50%.
        assertThat(pct).isEqualTo(50);
    }

    @Test
    void missingMandatorySkillCannotHideBehindPreferredMatches() {
        // 3 of 4 matched but the missing one is mandatory → 3/5 weight = 60%,
        // versus 75% under the old unweighted formula.
        List<String> required = List.of("Java", "Spring Boot", "React", "Redis");
        int pct = JobMatcherService.computeWeightedSkillMatchPercent(
                required, List.of("Java", "Spring Boot", "React"), Set.of("Redis"));

        assertThat(pct).isEqualTo(60);
    }

    @Test
    void noMandatorySkillsFallsBackToPlainRatio() {
        List<String> required = List.of("Java", "Docker");
        int pct = JobMatcherService.computeWeightedSkillMatchPercent(
                required, List.of("Java"), Set.of());
        assertThat(pct).isEqualTo(50);
    }

    @Test
    void longJdMandatorySkillsBeyond3500CharsAreStillClassified() {
        // Regression for the async 3500-char truncation bug: padding + mandatory
        // section must survive because the FULL text is now processed.
        String filler = "Team culture paragraph. ".repeat(400); // ~10k chars of noise
        String jd = filler + """

                Mandatory requirements:
                - Kubernetes
                - Redis
                """;

        Set<String> mandatory = svc.extractMandatorySkills(jd);
        assertThat(mandatory).contains("Kubernetes", "Redis");
    }

    @Test
    void extractMandatoryAndPreferredSetsMatchClassification() {
        Set<String> mandatory = svc.extractMandatorySkills(JAVA_FULL_STACK_JD);
        Set<String> preferred = svc.extractPreferredSkills(JAVA_FULL_STACK_JD);

        assertThat(mandatory).containsExactlyInAnyOrder("Kubernetes", "AWS", "Kafka", "Redis");
        assertThat(preferred).contains("Microservices", "GitHub", "Machine Learning");
    }
}
