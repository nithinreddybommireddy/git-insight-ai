package com.gitinsight.githubservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "score_history", indexes = {
    @Index(name = "idx_score_username", columnList = "username"),
    @Index(name = "idx_score_username_created", columnList = "username, created_at"),
    @Index(name = "idx_score_owner", columnList = "owner_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /**
     * The auth-service user who saved this snapshot (null for rows recorded
     * before ownership was introduced). USER-scoped history hides null-owner
     * rows; RECRUITER/ADMIN see everything.
     */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(nullable = false)
    private int overallScore;

    @Column(length = 50)
    private String level;

    private int contributionRecency;
    private int commitFrequency;
    private int repositoryHealth;
    private int repositoryQuality;
    private int contributionConsistency;
    private int languageDiversity;
    private int collaboration;
    private int openSourceImpact;
    private int popularity;
    private int maintenance;

    private int totalStars;
    private int totalForks;
    private int totalRepositories;
    private int languageCount;

    @Column(length = 500)
    private String languages;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
