package com.artivisi.accountingfinance.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
@NoArgsConstructor
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Periode gaji wajib diisi")
    @Size(max = 7, message = "Periode gaji format YYYY-MM")
    @Column(name = "payroll_period", nullable = false, length = 7)
    private String payrollPeriod; // Format: "2025-01"

    @NotNull(message = "Tanggal mulai periode wajib diisi")
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull(message = "Tanggal akhir periode wajib diisi")
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @NotNull(message = "Status wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayrollStatus status = PayrollStatus.DRAFT;

    // Summary totals
    @Column(name = "total_gross", precision = 19, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", precision = 19, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net_pay", precision = 19, scale = 2)
    private BigDecimal totalNetPay = BigDecimal.ZERO;

    @Column(name = "total_company_bpjs", precision = 19, scale = 2)
    private BigDecimal totalCompanyBpjs = BigDecimal.ZERO;

    @Column(name = "total_pph21", precision = 19, scale = 2)
    private BigDecimal totalPph21 = BigDecimal.ZERO;

    @Column(name = "employee_count")
    private Integer employeeCount = 0;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Reference to journal entry created when posted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_journal_entry")
    private JournalEntry journalEntry;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Size(max = 500, message = "Alasan pembatalan maksimal 500 karakter")
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollDetail> details = new ArrayList<>();

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

    /**
     * Set period from YearMonth.
     */
    public void setPeriod(YearMonth yearMonth) {
        this.payrollPeriod = yearMonth.toString();
        this.periodStart = yearMonth.atDay(1);
        this.periodEnd = yearMonth.atEndOfMonth();
    }

    /**
     * Get period as YearMonth.
     */
    public YearMonth getPeriod() {
        return YearMonth.parse(payrollPeriod);
    }

    /**
     * Get display name for period.
     */
    public String getPeriodDisplayName() {
        YearMonth ym = getPeriod();
        return ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("id"))
            + " " + ym.getYear();
    }

    /**
     * Add a payroll detail.
     */
    public void addDetail(PayrollDetail detail) {
        details.add(detail);
        detail.setPayrollRun(this);
    }

    /**
     * Remove a payroll detail.
     */
    public void removeDetail(PayrollDetail detail) {
        details.remove(detail);
        detail.setPayrollRun(null);
    }

    /**
     * Calculate totals from details.
     */
    public void calculateTotals() {
        this.totalGross = details.stream()
            .map(PayrollDetail::getGrossSalary)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalDeductions = details.stream()
            .map(PayrollDetail::getTotalDeductions)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalNetPay = details.stream()
            .map(PayrollDetail::getNetPay)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalCompanyBpjs = details.stream()
            .map(PayrollDetail::getTotalCompanyBpjs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalPph21 = details.stream()
            .map(PayrollDetail::getPph21)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.employeeCount = details.size();
    }

    public boolean isDraft() {
        return status == PayrollStatus.DRAFT;
    }

    public boolean isCalculated() {
        return status == PayrollStatus.CALCULATED;
    }

    public boolean isApproved() {
        return status == PayrollStatus.APPROVED;
    }

    public boolean isPosted() {
        return status == PayrollStatus.POSTED;
    }

    public boolean isCancelled() {
        return status == PayrollStatus.CANCELLED;
    }

    public boolean canEdit() {
        return isDraft() || isCalculated();
    }

    public boolean canPost() {
        return isApproved();
    }

    public boolean canCancel() {
        return !isPosted() && !isCancelled();
    }
}
