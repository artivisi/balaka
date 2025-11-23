package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyConfigRepository extends JpaRepository<CompanyConfig, UUID> {

    default Optional<CompanyConfig> findFirst() {
        return findAll().stream().findFirst();
    }
}
