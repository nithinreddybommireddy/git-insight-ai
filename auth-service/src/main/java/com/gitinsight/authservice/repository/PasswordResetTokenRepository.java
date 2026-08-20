package com.gitinsight.authservice.repository;

import com.gitinsight.authservice.entity.PasswordResetToken;
import com.gitinsight.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    void deleteByUsedFalse();
}
