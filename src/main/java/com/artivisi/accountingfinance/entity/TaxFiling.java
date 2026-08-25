package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One tax return per (type, period, pembetulan). Holds the DJP-side facts that
 * have no journal entry to live on: when the SPT was filed, under which BPE,
 * and how the resulting kurang bayar was billed and paid.
 */
@Entity
@Table(name = "tax_filings")
@Getter
@Setter
@NoArgsConstructor
public class TaxFiling extends TimestampedEntity {

    @NotNull(message = "Jenis pajak wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 20)
    private TaxFilingType taxType;

    @NotNull(message = "Tahun pajak wajib diisi")
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** Null for the annual PPh Badan return; 1-12 for masa returns. */
    @Column(name = "period_month")
    private Integer periodMonth;

    @NotNull(message = "Nomor pembetulan wajib diisi")
    @PositiveOrZero(message = "Nomor pembetulan tidak boleh negatif")
    @Column(name = "pembetulan_no", nullable = false)
    private Integer pembetulanNo = 0;

    @NotNull(message = "Status pelaporan wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaxFilingStatus status;

    @Column(name = "filed_date")
    private LocalDate filedDate;

    @Column(name = "bpe_number", length = 100)
    private String bpeNumber;

    @Column(name = "bpe_date")
    private LocalDate bpeDate;

    @PositiveOrZero(message = "Kurang bayar tidak boleh negatif")
    @Column(name = "kurang_bayar", precision = 15, scale = 2)
    private BigDecimal kurangBayar;

    @PositiveOrZero(message = "Lebih bayar tidak boleh negatif")
    @Column(name = "lebih_bayar", precision = 15, scale = 2)
    private BigDecimal lebihBayar;

    @Column(name = "nihil", nullable = false)
    private boolean nihil = false;

    @Column(name = "billing_code", length = 50)
    private String billingCode;

    @Column(name = "ntpn", length = 50)
    private String ntpn;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    /**
     * The booked setoran. Nullable: a nihil masa has nothing to pay, and an
     * FP-03 (BUMN pemungut) masa is report-only — the BUMN remits the PPN.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payment_transaction")
    private Transaction paymentTransaction;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** "2026-04" for a masa return, "2026" for the annual PPh Badan return. */
    public String getPeriod() {
        return periodMonth == null
                ? String.valueOf(periodYear)
                : String.format("%04d-%02d", periodYear, periodMonth);
    }

    /** First day covered by this filing. */
    public LocalDate getPeriodStart() {
        return periodMonth == null
                ? LocalDate.of(periodYear, 1, 1)
                : LocalDate.of(periodYear, periodMonth, 1);
    }

    /** Last day covered by this filing. */
    public LocalDate getPeriodEnd() {
        LocalDate start = getPeriodStart();
        return periodMonth == null
                ? LocalDate.of(periodYear, 12, 31)
                : start.withDayOfMonth(start.lengthOfMonth());
    }
}
