package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.PasswordResetToken;
import com.artivisi.accountingfinance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Invalidates every token of the user that is still usable. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.invalidatedAt = :now "
            + "WHERE t.user = :user AND t.usedAt IS NULL AND t.invalidatedAt IS NULL")
    int invalidateOpenTokens(@Param("user") User user, @Param("now") LocalDateTime now);
}
