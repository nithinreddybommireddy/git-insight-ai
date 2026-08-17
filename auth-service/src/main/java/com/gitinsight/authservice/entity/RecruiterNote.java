package com.gitinsight.authservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recruiter_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER: recruiter is serialized with every note response; with
    // open-in-view disabled a lazy proxy can't be initialized after flush.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(nullable = false)
    private String candidateUsername;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(length = 200)
    private String title;

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
