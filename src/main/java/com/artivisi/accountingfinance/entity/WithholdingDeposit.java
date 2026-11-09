package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Setoran of withheld tax for a masa. Counts as deposited once {@link #transaction} is POSTED. */
@Entity
@Table(name = "withholding_deposits")
@Getter
@Setter
@NoArgsConstructor
public class WithholdingDeposit extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_transaction", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment", nullable = false, length = 30)
    private WithholdingTreatment treatment;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "deposit_date", nullable = false)
    private LocalDate depositDate;

    @Column(name = "billing_code", length = 50)
    private String billingCode;

    @Column(name = "ntpn", length = 50)
    private String ntpn;
}
