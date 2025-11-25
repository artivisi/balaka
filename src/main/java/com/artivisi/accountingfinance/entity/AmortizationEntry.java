package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.AmortizationEntryStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "amortization_entries")
@Getter
@Setter
@NoArgsConstructor
public class AmortizationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull(message = "Schedule is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_schedule", nullable = false)
    private AmortizationSchedule schedule;

    @NotNull(message = "Period number is required")
    @Min(value = 1, message = "Period number must be at least 1")
    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @NotNull(message = "Period start is required")
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "id_journal_entry")
    private UUID journalEntryId;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AmortizationEntryStatus status = AmortizationEntryStatus.PENDING;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == AmortizationEntryStatus.PENDING;
    }

    public boolean isPosted() {
        return status == AmortizationEntryStatus.POSTED;
    }

    public boolean isSkipped() {
        return status == AmortizationEntryStatus.SKIPPED;
    }

    public String getPeriodLabel() {
        return periodStart.getMonth().name() + " " + periodStart.getYear();
    }
}
