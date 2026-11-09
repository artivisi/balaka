package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_entries")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry extends BaseEntity {

    @Size(max = 50, message = "Journal number must not exceed 50 characters")
    @Column(name = "journal_number", unique = true, length = 50)
    private String journalNumber;

    @NotNull(message = "Line order is required")
    @Positive(message = "Line order must be positive")
    @Column(name = "line_order", nullable = false)
    private Integer lineOrder;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Size(max = 500, message = "Void reason must not exceed 500 characters")
    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @JsonIgnore
    @NotNull(message = "Transaction is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction", nullable = false)
    private Transaction transaction;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project")
    private Project project;

    @JsonIgnore
    @NotNull(message = "Account is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_account", nullable = false)
    private ChartOfAccount account;

    @NotNull(message = "Debit amount is required")
    @DecimalMin(value = "0.00", message = "Debit amount cannot be negative")
    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @NotNull(message = "Credit amount is required")
    @DecimalMin(value = "0.00", message = "Credit amount cannot be negative")
    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "is_reversal", nullable = false)
    private Boolean isReversal = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reversed_entry")
    private JournalEntry reversedEntry;

    public boolean isDebitEntry() {
        return debitAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isCreditEntry() {
        return creditAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getAmount() {
        return isDebitEntry() ? debitAmount : creditAmount;
    }

    public boolean isDraft() {
        return transaction.isDraft();
    }

    public boolean isPosted() {
        return transaction.isPosted();
    }

    public boolean isVoid() {
        return transaction.isVoid();
    }

    // ========== Delegated Getters (from Transaction) ==========

    public LocalDate getJournalDate() {
        return transaction.getTransactionDate();
    }

    public String getDescription() {
        if (Boolean.TRUE.equals(isReversal)) {
            return "Reversal: " + transaction.getDescription();
        }
        return transaction.getDescription();
    }

    public String getReferenceNumber() {
        return transaction.getReferenceNumber();
    }

    public TransactionStatus getStatus() {
        return transaction.getStatus();
    }
}
