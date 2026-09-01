package com.gitinsight.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick test to verify skill extraction from the Java Full Stack Developer JD.
 */
class JDExtractionTest {

    @Test
    void extractSkillsFromJavaFullStackDeveloperJD() {
        String jd = """
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
                - Collaborate with engineering teams...
                - Write unit and integration tests...
                - Deploy applications using Docker...

                Mandatory requirements near the END:
                - Kubernetes
                - AWS
                - Kafka
                - Redis
                """;

        JobMatcherService svc = new JobMatcherService(
                org.springframework.web.client.RestClient.create());
        List<String> required = svc.extractRequiredSkills(jd);

        System.out.println("=== Extracted Skills ===");
        required.forEach(s -> System.out.println("  ✅ " + s));
        System.out.println("Total: " + required.size());

        // Verify ALL mentioned skills are extracted (including end-of-JD mandatory)
        assertThat(required).contains(
                "Java",
                "Spring Boot",
                "React",
                "REST API",
                "SQL",
                "Git",
                "Docker",
                "Microservices",
                "Machine Learning",  // AI/ML knowledge
                "Kubernetes",
                "AWS",
                "Kafka",
                "Redis"
        );

        // The JD mentions 19 distinct skills including CI/CD, GitHub, Full Stack, Backend,
        // PostgreSQL, Artificial Intelligence, Machine Learning etc.
        assertThat(required).hasSize(19);
        // Verify no obviously wrong skills (like Problem-solving which has no alias)
        assertThat(required).doesNotContain("Problem-solving");
    }
}
