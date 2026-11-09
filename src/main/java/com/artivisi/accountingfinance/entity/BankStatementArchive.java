package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Monthly bank statement file for one company bank account. Replacing a file keeps the previous row as SUPERSEDED.
 * No row or file is removed before {@code retentionUntil}.
 */
@Entity
@Table(name = "bank_statement_archives")
@Getter
@Setter
@NoArgsConstructor
public class BankStatementArchive extends TimestampedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bank_account", nullable = false)
    private CompanyBankAccount bankAccount;

    /** First day of the statement month. */
    @NotNull
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document", nullable = false)
    private Document document;

    @NotNull
    @Column(name = "retention_until", nullable = false)
    private LocalDate retentionUntil;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private StatementArchiveStatus status;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @NotBlank
    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    public YearMonth getYearMonth() {
        return YearMonth.from(periodMonth);
    }

    public boolean isRetainedOn(LocalDate date) {
        return !date.isAfter(retentionUntil);
    }
}
