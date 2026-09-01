package com.gitinsight.authservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Targeted tests for the four confirmed evidence-pattern gaps:
 * 1. Microservices — @EnableDiscoveryClient, @FeignClient, @EnableEurekaClient, @LoadBalanced
 * 2. Docker — filename-aware (Dockerfile), docker-compose, strong Dockerfile instructions
 * 3. SQL — PostgreSQL, MySQL, SELECT, INSERT, @Query, nativeQuery
 * 4. Java — import java., @SpringBootApplication, SpringApplication.run
 *
 * Uses sourcePatternMatches() directly since these are source-evidence patterns,
 * not the SKILL_ALIASES regex patterns used by matches().
 */
@DisplayName("Skill Evidence Gap Tests")
class SkillEvidenceGapTest {

    // ══════════════════════════════════════════════════════════════════
    //  FIX 1 — MICROSERVICES
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Microservices Evidence (sourcePatternMatches)")
    class MicroservicesEvidence {

        @Test
        void enableDiscoveryClientDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@EnableDiscoveryClient\n@SpringBootApplication", "Microservices")).isTrue();
        }

        @Test
        void enableEurekaClientDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@EnableEurekaClient\n@SpringBootApplication", "Microservices")).isTrue();
        }

        @Test
        void feignClientDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@FeignClient(\"order-service\")", "Microservices")).isTrue();
        }

        @Test
        void loadBalancedDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@LoadBalanced\n@Bean\nRestTemplate restTemplate()", "Microservices")).isTrue();
        }

        @Test
        void springCloudStandaloneDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Spring Cloud Config Server", "Microservices")).isTrue();
        }

        @Test
        void apiGatewayDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Spring Cloud Gateway configuration for api-gateway service", "Microservices")).isTrue();
        }

        @Test
        void genericServiceNotMicroservices() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "public class OrderService { }", "Microservices")).isFalse();
        }

        @Test
        void singleRestControllerNotMicroservices() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@RestController\npublic class FooController {}", "Microservices")).isFalse();
        }

        @Test
        void genericDockerNotMicroservices() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "FROM node:18\nRUN npm install", "Microservices")).isFalse();
        }

        @Test
        void eurekaInConfigYamlDetected() {
            String config = "spring:\n  application:\n    name: order-service\neureka:\n  client:\n    service-url:\n      defaultZone: http://eureka:8761/eureka/";
            assertThat(JobMatcherService.sourcePatternMatches(config, "Microservices")).isTrue();
        }

        @Test
        void eurekaInPomXmlDetected() {
            String pom = "<artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>";
            assertThat(JobMatcherService.sourcePatternMatches(pom, "Microservices")).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  FIX 2 — DOCKER
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Docker Evidence (sourcePatternMatches + filename)")
    class DockerEvidence {

        @Test
        void dockerKeywordInContentDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Docker containerization deployment", "Docker")).isTrue();
        }

        @Test
        void dockerComposeKeywordDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "docker compose up --build", "Docker")).isTrue();
        }

        @Test
        void dockerfileKeywordInContentDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Dockerfile:\nFROM eclipse-temurin:21-jre", "Docker")).isTrue();
        }

        @Test
        void dockerImageKeywordDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "image: eclipse-temurin:21-jre", "Docker")).isTrue();
        }

        @Test
        void dockerFileByNameDetected() {
            // Filename "Dockerfile" is strong Docker evidence
            assertThat(JobMatcherService.sourcePatternMatches(
                    "FROM eclipse-temurin:21-jre\nEXPOSE 8080", "Dockerfile", "Docker")).isTrue();
        }

        @Test
        void dockerFileByNameAtSubpathDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "FROM node:18\nCOPY . .", "backend/Dockerfile", "Docker")).isTrue();
        }

        @Test
        void dockerComposeYmlByNameDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "services:\n  app:\n    build: .", "docker-compose.yml", "Docker")).isTrue();
        }

        @Test
        void dockerComposeYamlByNameDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "services:\n  app:\n    image: myapp", "docker-compose.yaml", "Docker")).isTrue();
        }

        @Test
        void dockerfileInstructionsWithKeywordDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Dockerfile:\nFROM maven:3.9\nCOPY . .\nRUN mvn package\nEXPOSE 8080\nENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]",
                    "Docker")).isTrue();
        }

        @Test
        void ordinaryJavaFileWithFromNotDocker() {
            // A Java file containing "FROM" must NOT be classified as Docker
            assertThat(JobMatcherService.sourcePatternMatches(
                    "FROM model import *\nSELECT * FROM users", "UserRepository.java", "Docker")).isFalse();
        }

        @Test
        void ordinaryJavaFileWithFromNotDockerContentOnly() {
            // Without filename, "FROM" alone doesn't trigger Docker
            assertThat(JobMatcherService.sourcePatternMatches(
                    "FROM model import *\nSELECT * FROM users", "Docker")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  FIX 3 — SQL
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SQL Evidence (sourcePatternMatches)")
    class SqlEvidence {

        @Test
        void postgresqlDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "PostgreSQL database configuration", "SQL")).isTrue();
        }

        @Test
        void postgresDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "postgres connection pool setup", "SQL")).isTrue();
        }

        @Test
        void mysqlDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "MySQL database with user management", "SQL")).isTrue();
        }

        @Test
        void selectQueryDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "SELECT * FROM users WHERE id = ?", "SQL")).isTrue();
        }

        @Test
        void insertIntoDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "INSERT INTO orders (amount) VALUES (100)", "SQL")).isTrue();
        }

        @Test
        void updateDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "UPDATE users SET name = ? WHERE id = ?", "SQL")).isTrue();
        }

        @Test
        void deleteFromDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "DELETE FROM sessions WHERE expired_at < NOW()", "SQL")).isTrue();
        }

        @Test
        void createTableDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "CREATE TABLE users (id SERIAL PRIMARY KEY)", "SQL")).isTrue();
        }

        @Test
        void queryAnnotationDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@Query(\"SELECT u FROM User u WHERE u.email = :email\")", "SQL")).isTrue();
        }

        @Test
        void nativeQueryDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@NativeQuery(\"SELECT * FROM users\")", "SQL")).isTrue();
        }

        @Test
        void jpaRepositoryDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "interface UserRepository extends JpaRepository", "SQL")).isTrue();
        }

        @Test
        void jdbcDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "jdbc:postgresql://localhost:5432/mydb", "SQL")).isTrue();
        }

        @Test
        void entityAnnotationDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@Entity\n@Table(name = \"users\")", "SQL")).isTrue();
        }

        @Test
        void springDataJpaDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Spring Data JPA repository pattern", "SQL")).isTrue();
        }

        @Test
        void datasourceDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "spring.datasource.url=jdbc:postgresql://localhost:5432/db", "SQL")).isTrue();
        }

        @Test
        void flywayMigrationDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "flyway migration V1__create_users.sql", "SQL")).isTrue();
        }

        @Test
        void mariadbDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "MariaDB connection configuration", "SQL")).isTrue();
        }

        @Test
        void oracleDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Oracle database setup", "SQL")).isTrue();
        }

        @Test
        void genericClassNotSql() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "public class UserService {}", "SQL")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  FIX 4 — JAVA SOURCE EVIDENCE
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Java Source Evidence (sourcePatternMatches)")
    class JavaSourceEvidence {

        @Test
        void importJavaDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "import java.util.List;", "Java")).isTrue();
        }

        @Test
        void importJavaUtilDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "import java.util.Map;\nimport java.util.stream.Collectors;", "Java")).isTrue();
        }

        @Test
        void importJavaxDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "import javax.persistence.Entity;", "Java")).isTrue();
        }

        @Test
        void importJakartaDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "import jakarta.persistence.EntityManager;", "Java")).isTrue();
        }

        @Test
        void springBootApplicationDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@SpringBootApplication\npublic class MyApp {}", "Java")).isTrue();
        }

        @Test
        void springApplicationRunDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "SpringApplication.run(MyApp.class, args);", "Java")).isTrue();
        }

        @Test
        void javaUtilDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "java.util.List<String> items = new ArrayList<>();", "Java")).isTrue();
        }

        @Test
        void javaLangDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "java.lang.String result;", "Java")).isTrue();
        }

        @Test
        void javaIoDetected() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "java.io.BufferedReader reader;", "Java")).isTrue();
        }

        @Test
        void genericPythonClassNotJava() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "class MyClass:\n    def __init__(self):", "Java")).isFalse();
        }

        @Test
        void pythonImportNotJava() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "import os\nimport sys", "Java")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  EXISTING PATTERNS UNCHANGED
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Existing source patterns unchanged")
    class ExistingPatternsUnchanged {

        @Test
        void restApiDetectionUnchanged() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@RestController\n@RequestMapping(\"/api\")", "REST API")).isTrue();
        }

        @Test
        void springBootDetectionUnchanged() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "@SpringBootApplication", "Spring Boot")).isTrue();
        }

        @Test
        void reactDetectionUnchanged() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "import React from 'react';", "React")).isTrue();
        }

        @Test
        void gitDetectionUnchanged() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "git commit -m \"fix\"", "Git")).isTrue();
        }

        @Test
        void dockerDetectionViaKeywordUnchanged() {
            assertThat(JobMatcherService.sourcePatternMatches(
                    "Docker compose deployment", "Docker")).isTrue();
        }

        @Test
        void scoringFormulaUnchanged() {
            assertThat(JobMatcherService.computeMatchScore(80, 60)).isEqualTo(72);
            assertThat(JobMatcherService.computeMatchScore(100, 100)).isEqualTo(100);
            assertThat(JobMatcherService.computeMatchScore(0, 100)).isEqualTo(40);
        }
    }
}
