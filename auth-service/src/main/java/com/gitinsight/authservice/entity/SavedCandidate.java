package com.gitinsight.authservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_candidates", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"recruiter_id", "candidate_username"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(name = "candidate_username", nullable = false)
    private String candidateUsername;

    @Column(name = "candidate_name")
    private String candidateName;

    @Column(name = "candidate_avatar_url", length = 512)
    private String candidateAvatarUrl;

    @Column(name = "candidate_github_id")
    private Long candidateGithubId;

    @Column(name = "candidate_score")
    private Integer candidateScore;

    @Column(name = "candidate_level")
    private String candidateLevel;

    @Column(name = "candidate_languages", length = 512)
    private String candidateLanguages;

    @Column(nullable = false)
    private boolean bookmarked = false;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
