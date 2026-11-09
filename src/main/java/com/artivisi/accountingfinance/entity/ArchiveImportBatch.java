package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/** One accepted bulk import of archive documents (issue #51). A rejected import leaves no row. */
@Entity
@Table(name = "archive_import_batches")
@Immutable
@Getter
@Setter
public class ArchiveImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "document_count", nullable = false)
    private Integer documentCount;

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;
}
