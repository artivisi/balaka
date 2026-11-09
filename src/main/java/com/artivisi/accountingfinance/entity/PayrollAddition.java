package com.artivisi.accountingfinance.entity;

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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bonus or THR input for one employee on one run. Calculation sums these into
 * {@link PayrollDetail#getBonus()} and {@link PayrollDetail#getThr()}; the rows survive recalculation,
 * which replaces the details.
 */
@Entity
@Table(name = "payroll_additions")
@Getter
@Setter
@NoArgsConstructor
public class PayrollAddition extends TimestampedEntity {

    @NotNull(message = "Payroll run wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payroll_run", nullable = false)
    private PayrollRun payrollRun;

    @NotNull(message = "Karyawan wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employee", nullable = false)
    private Employee employee;

    @NotNull(message = "Jenis wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "addition_type", nullable = false, length = 10)
    private PayrollAdditionType additionType;

    @NotNull(message = "Jumlah wajib diisi")
    @DecimalMin(value = "0.01", message = "Jumlah harus lebih dari nol")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Size(max = 255, message = "Keterangan maksimal 255 karakter")
    @Column(name = "description", length = 255)
    private String description;
}
