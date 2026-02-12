package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByTokenHash(String tokenHash);

    @Query("SELECT t FROM DeviceToken t WHERE t.user = :user AND t.revokedAt IS NULL ORDER BY t.createdAt DESC")
    List<DeviceToken> findActiveByUser(User user);

    @Query("SELECT t FROM DeviceToken t WHERE t.user.id = :userId AND t.revokedAt IS NULL ORDER BY t.createdAt DESC")
    List<DeviceToken> findActiveByUserId(UUID userId);

    @Query("SELECT COUNT(t) FROM DeviceToken t WHERE t.user = :user AND t.revokedAt IS NULL")
    long countActiveByUser(User user);

    @Query("SELECT t FROM DeviceToken t JOIN FETCH t.user WHERE t.revokedAt IS NULL AND t.expiresAt > CURRENT_TIMESTAMP")
    List<DeviceToken> findAllActiveWithUser();
}
