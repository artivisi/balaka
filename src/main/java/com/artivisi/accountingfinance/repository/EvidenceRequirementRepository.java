package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.EvidenceRequirement;
import com.artivisi.accountingfinance.enums.EvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvidenceRequirementRepository extends JpaRepository<EvidenceRequirement, UUID> {

    @Query("SELECT r FROM EvidenceRequirement r LEFT JOIN FETCH r.account LEFT JOIN FETCH r.journalTemplate t "
            + "LEFT JOIN FETCH t.originalTemplate ORDER BY r.evidenceType, r.createdAt")
    List<EvidenceRequirement> findAllWithTargets();

    boolean existsByAccountIdAndEvidenceType(UUID accountId, EvidenceType evidenceType);

    boolean existsByJournalTemplateIdAndEvidenceType(UUID templateId, EvidenceType evidenceType);
}
