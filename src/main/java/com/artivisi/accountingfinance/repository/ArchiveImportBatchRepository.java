package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.ArchiveImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArchiveImportBatchRepository extends JpaRepository<ArchiveImportBatch, UUID> {
}
