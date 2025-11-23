package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_balances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"id_account", "period_year", "period_month"})
})
@Getter
@Setter
@NoArgsConstructor
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull(message = "Account is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_account", nullable = false)
    private ChartOfAccount account;

    @Min(value = 2000, message = "Period year must be at least 2000")
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Min(value = 1, message = "Period month must be between 1 and 12")
    @Max(value = 12, message = "Period month must be between 1 and 12")
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @NotNull(message = "Opening balance is required")
    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @NotNull(message = "Debit total is required")
    @Column(name = "debit_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal debitTotal = BigDecimal.ZERO;

    @NotNull(message = "Credit total is required")
    @Column(name = "credit_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditTotal = BigDecimal.ZERO;

    @NotNull(message = "Closing balance is required")
    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Min(value = 0, message = "Entry count cannot be negative")
    @Column(name = "entry_count", nullable = false)
    private Integer entryCount = 0;

    @Column(name = "last_calculated_at", nullable = false)
    private LocalDateTime lastCalculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastCalculatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
