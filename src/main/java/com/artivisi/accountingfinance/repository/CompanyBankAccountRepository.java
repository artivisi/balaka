package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyBankAccountRepository extends JpaRepository<CompanyBankAccount, UUID> {

    List<CompanyBankAccount> findByActiveTrueOrderByBankNameAsc();

    List<CompanyBankAccount> findAllByOrderByBankNameAsc();

    Optional<CompanyBankAccount> findByIsDefaultTrueAndActiveTrue();

    Optional<CompanyBankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);
}
