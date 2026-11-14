package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.entity.UserMfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMfaRecoveryCodeRepository extends JpaRepository<UserMfaRecoveryCode, UUID> {

    List<UserMfaRecoveryCode> findByUser(User user);

    Optional<UserMfaRecoveryCode> findByUserAndCodeHashAndUsedAtIsNull(User user, String codeHash);

    /**
     * Consume a code only if nobody consumed it first. The conditional update is
     * what makes a code single use under concurrent submissions; a find-then-save
     * pair lets two requests both see the row unused.
     *
     * @return 1 when this call consumed the code, 0 when it was already used
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserMfaRecoveryCode c SET c.usedAt = :usedAt "
            + "WHERE c.id = :id AND c.usedAt IS NULL")
    int markUsedIfUnused(@Param("id") UUID id, @Param("usedAt") LocalDateTime usedAt);

    void deleteByUser(User user);
}
