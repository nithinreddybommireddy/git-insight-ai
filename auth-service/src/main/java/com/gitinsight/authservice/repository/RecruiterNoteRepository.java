package com.gitinsight.authservice.repository;

import com.gitinsight.authservice.entity.RecruiterNote;
import com.gitinsight.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruiterNoteRepository extends JpaRepository<RecruiterNote, Long> {

    List<RecruiterNote> findByRecruiterAndCandidateUsernameOrderByCreatedAtDesc(User recruiter, String candidateUsername);

    List<RecruiterNote> findByRecruiterOrderByCreatedAtDesc(User recruiter);

    long countByRecruiter(User recruiter);

    void deleteByRecruiterAndCandidateUsername(User recruiter, String candidateUsername);
}
