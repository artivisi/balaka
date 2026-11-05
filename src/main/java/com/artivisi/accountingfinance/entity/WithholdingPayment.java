package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.WithholdingRateBasis;
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

/**
 * A payment to a payee with tax withheld. Its state is the state of {@link #transaction}: DRAFT until the
 * user posts it, VOID when voided. grossAmount is the withholding base; settledAmount is what the payment
 * debits, which differs from gross only for a bill payment that includes PPN.
 */
@Entity
@Table(name = "withholding_payments")
@Getter
@Setter
@NoArgsConstructor
public class WithholdingPayment extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_vendor", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_transaction", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax_detail")
    private TaxTransactionDetail taxDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bill")
    private Bill bill;

    /** Set when the transaction is posted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bill_payment")
    private BillPayment billPayment;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment", nullable = false, length = 30)
    private WithholdingTreatment treatment;

    @Column(name = "tax_object_code", nullable = false, length = 20)
    private String taxObjectCode;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "dpp_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal dppAmount;

    /** Effective rate on dppAmount, rounded for display and export. taxAmount is the exact figure. */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    /** Snapshot: did the payee have NPWP or NIK when this payment was computed. */
    @Column(name = "has_tax_id", nullable = false)
    private boolean hasTaxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_basis", nullable = false, length = 30)
    private WithholdingRateBasis rateBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
}
