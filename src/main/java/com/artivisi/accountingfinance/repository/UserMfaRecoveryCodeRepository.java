package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.entity.UserMfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMfaRecoveryCodeRepository extends JpaRepository<UserMfaRecoveryCode, UUID> {

    List<UserMfaRecoveryCode> findByUser(User user);

    Optional<UserMfaRecoveryCode> findByUserAndCodeHashAndUsedAtIsNull(User user, String codeHash);

    void deleteByUser(User user);
}
