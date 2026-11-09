package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Benefit-in-kind (natura/kenikmatan) given to one employee through a posted transaction.
 * A row with a payroll detail has entered that employee's PPh 21 base.
 */
@Entity
@Table(name = "benefit_in_kind")
@Getter
@Setter
@NoArgsConstructor
public class BenefitInKind extends TimestampedEntity {

    @NotNull(message = "Transaksi wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction", nullable = false)
    private Transaction transaction;

    @NotNull(message = "Penerima wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employee", nullable = false)
    private Employee employee;

    @NotNull(message = "Nilai natura wajib diisi")
    @Positive(message = "Nilai natura harus positif")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Keterangan natura wajib diisi")
    @Size(max = 255, message = "Keterangan natura maksimal 255 karakter")
    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payroll_detail")
    private PayrollDetail payrollDetail;
}
