package com.gitinsight.authservice.repository;

import com.gitinsight.authservice.entity.SavedCandidate;
import com.gitinsight.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedCandidateRepository extends JpaRepository<SavedCandidate, Long> {

    List<SavedCandidate> findByRecruiterOrderByCreatedAtDesc(User recruiter);

    Optional<SavedCandidate> findByRecruiterAndCandidateUsername(User recruiter, String candidateUsername);

    boolean existsByRecruiterAndCandidateUsername(User recruiter, String candidateUsername);

    List<SavedCandidate> findByRecruiterAndBookmarkedTrueOrderByCreatedAtDesc(User recruiter);

    long countByRecruiter(User recruiter);
}
