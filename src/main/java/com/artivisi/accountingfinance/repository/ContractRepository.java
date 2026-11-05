package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    boolean existsByContractNumber(String contractNumber);

    long countByDocumentId(UUID documentId);

    boolean existsByContractNumberAndIdNot(String contractNumber, UUID id);

    Optional<Contract> findByContractNumber(String contractNumber);

    @Query("SELECT c FROM Contract c LEFT JOIN FETCH c.client LEFT JOIN FETCH c.vendor LEFT JOIN FETCH c.document "
            + "WHERE (:clientId IS NULL OR c.client.id = :clientId) AND (:vendorId IS NULL OR c.vendor.id = :vendorId) "
            + "ORDER BY c.startDate DESC, c.contractNumber")
    List<Contract> search(@Param("clientId") UUID clientId, @Param("vendorId") UUID vendorId);

    @Query("SELECT c FROM Contract c LEFT JOIN FETCH c.client LEFT JOIN FETCH c.vendor LEFT JOIN FETCH c.document "
            + "WHERE c.id = :id")
    Optional<Contract> findWithParties(@Param("id") UUID id);
}
