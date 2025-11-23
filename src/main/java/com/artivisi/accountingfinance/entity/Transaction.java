package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction extends BaseEntity {

    @NotBlank(message = "Transaction number is required")
    @Size(max = 50, message = "Transaction number must not exceed 50 characters")
    @Column(name = "transaction_number", nullable = false, unique = true, length = 50)
    private String transactionNumber;

    @NotNull(message = "Transaction date is required")
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @NotNull(message = "Journal template is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_journal_template", nullable = false)
    private JournalTemplate journalTemplate;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "void_reason", length = 50)
    private VoidReason voidReason;

    @Column(name = "void_notes", columnDefinition = "TEXT")
    private String voidNotes;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Size(max = 100, message = "Voided by must not exceed 100 characters")
    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Size(max = 100, message = "Posted by must not exceed 100 characters")
    @Column(name = "posted_by", length = 100)
    private String postedBy;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TransactionAccountMapping> accountMappings = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JournalEntry> journalEntries = new ArrayList<>();

    public void addAccountMapping(TransactionAccountMapping mapping) {
        accountMappings.add(mapping);
        mapping.setTransaction(this);
    }

    public void addJournalEntry(JournalEntry entry) {
        journalEntries.add(entry);
        entry.setTransaction(this);
    }

    public boolean isDraft() {
        return status == TransactionStatus.DRAFT;
    }

    public boolean isPosted() {
        return status == TransactionStatus.POSTED;
    }

    public boolean isVoid() {
        return status == TransactionStatus.VOID;
    }
}
