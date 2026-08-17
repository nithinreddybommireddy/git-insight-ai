package com.gitinsight.githubservice.repository;

import com.gitinsight.githubservice.entity.ScoreHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreHistoryRepository extends JpaRepository<ScoreHistory, Long> {

    List<ScoreHistory> findByUsernameOrderByCreatedAtDesc(String username);

    List<ScoreHistory> findByUsernameOrderByCreatedAtAsc(String username);

    Optional<ScoreHistory> findTopByUsernameOrderByCreatedAtDesc(String username);

    Page<ScoreHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ScoreHistory> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    long countByUsername(String username);

    // ── Owner-scoped queries (USER sees only the snapshots they saved) ──

    List<ScoreHistory> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<ScoreHistory> findByOwnerIdAndUsernameOrderByCreatedAtAsc(Long ownerId, String username);

    Optional<ScoreHistory> findTopByOwnerIdAndUsernameOrderByCreatedAtDesc(Long ownerId, String username);

    long countByOwnerId(Long ownerId);

    // ── SQL aggregation (stats never load the full table) ──

    @Query("select count(s) from ScoreHistory s")
    long countAll();

    @Query("select count(distinct s.username) from ScoreHistory s")
    long countDistinctUsernameAll();

    @Query("select avg(s.overallScore) from ScoreHistory s")
    Double averageScoreAll();

    @Query("select count(distinct s.username) from ScoreHistory s where s.ownerId = :ownerId")
    long countDistinctUsernameByOwnerId(@Param("ownerId") Long ownerId);

    @Query("select avg(s.overallScore) from ScoreHistory s where s.ownerId = :ownerId")
    Double averageScoreByOwnerId(@Param("ownerId") Long ownerId);
}
