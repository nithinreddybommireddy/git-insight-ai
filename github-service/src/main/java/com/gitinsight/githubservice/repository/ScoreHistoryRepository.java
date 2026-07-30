package com.gitinsight.githubservice.repository;

import com.gitinsight.githubservice.entity.ScoreHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreHistoryRepository extends JpaRepository<ScoreHistory, Long> {

    List<ScoreHistory> findByUsernameOrderByCreatedAtDesc(String username);

    List<ScoreHistory> findByUsernameOrderByCreatedAtAsc(String username);

    Optional<ScoreHistory> findTopByUsernameOrderByCreatedAtDesc(String username);

    List<ScoreHistory> findAllByOrderByCreatedAtDesc();

    long countByUsername(String username);
}
