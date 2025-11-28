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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_details")
@Getter
@Setter
@NoArgsConstructor
public class PayrollDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Payroll run wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payroll_run", nullable = false)
    private PayrollRun payrollRun;

    @NotNull(message = "Karyawan wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employee", nullable = false)
    private Employee employee;

    // Base salary for this period
    @NotNull(message = "Gaji pokok wajib diisi")
    @Column(name = "base_salary", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    // Gross salary (base + allowances)
    @Column(name = "gross_salary", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    // BPJS Kesehatan
    @Column(name = "bpjs_kes_company", precision = 15, scale = 2)
    private BigDecimal bpjsKesCompany = BigDecimal.ZERO;

    @Column(name = "bpjs_kes_employee", precision = 15, scale = 2)
    private BigDecimal bpjsKesEmployee = BigDecimal.ZERO;

    // BPJS Ketenagakerjaan - JKK (company only)
    @Column(name = "bpjs_jkk", precision = 15, scale = 2)
    private BigDecimal bpjsJkk = BigDecimal.ZERO;

    // BPJS Ketenagakerjaan - JKM (company only)
    @Column(name = "bpjs_jkm", precision = 15, scale = 2)
    private BigDecimal bpjsJkm = BigDecimal.ZERO;

    // BPJS Ketenagakerjaan - JHT
    @Column(name = "bpjs_jht_company", precision = 15, scale = 2)
    private BigDecimal bpjsJhtCompany = BigDecimal.ZERO;

    @Column(name = "bpjs_jht_employee", precision = 15, scale = 2)
    private BigDecimal bpjsJhtEmployee = BigDecimal.ZERO;

    // BPJS Ketenagakerjaan - JP
    @Column(name = "bpjs_jp_company", precision = 15, scale = 2)
    private BigDecimal bpjsJpCompany = BigDecimal.ZERO;

    @Column(name = "bpjs_jp_employee", precision = 15, scale = 2)
    private BigDecimal bpjsJpEmployee = BigDecimal.ZERO;

    // PPh 21
    @Column(name = "pph21", precision = 15, scale = 2)
    private BigDecimal pph21 = BigDecimal.ZERO;

    // Total deductions (BPJS employee + PPh 21)
    @Column(name = "total_deductions", precision = 19, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    // Net pay (gross - deductions)
    @Column(name = "net_pay", precision = 19, scale = 2)
    private BigDecimal netPay = BigDecimal.ZERO;

    // JKK Risk Class used for this calculation
    @Column(name = "jkk_risk_class")
    private Integer jkkRiskClass = 1;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
     * Get total BPJS company contribution.
     */
    public BigDecimal getTotalCompanyBpjs() {
        return bpjsKesCompany
            .add(bpjsJkk)
            .add(bpjsJkm)
            .add(bpjsJhtCompany)
            .add(bpjsJpCompany);
    }

    /**
     * Get total BPJS employee deduction.
     */
    public BigDecimal getTotalEmployeeBpjs() {
        return bpjsKesEmployee
            .add(bpjsJhtEmployee)
            .add(bpjsJpEmployee);
    }

    /**
     * Calculate totals based on components.
     */
    public void calculateTotals() {
        this.totalDeductions = getTotalEmployeeBpjs().add(pph21);
        this.netPay = grossSalary.subtract(totalDeductions);
    }

    /**
     * Get employee name for display.
     */
    public String getEmployeeName() {
        return employee != null ? employee.getName() : "";
    }

    /**
     * Get employee ID for display.
     */
    public String getEmployeeId() {
        return employee != null ? employee.getEmployeeId() : "";
    }
}
