package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TaxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tax_transaction_details")
@Getter
@Setter
@NoArgsConstructor
public class TaxTransactionDetail extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction", nullable = false)
    private Transaction transaction;

    // ========================================
    // e-Faktur fields (PPN)
    // ========================================

    @Size(max = 20, message = "Faktur number max 20 characters")
    @Column(name = "faktur_number", length = 20)
    private String fakturNumber;

    @Column(name = "faktur_date")
    private LocalDate fakturDate;

    @Size(max = 10, message = "Transaction code max 10 characters")
    @Column(name = "transaction_code", length = 10)
    private String transactionCode;  // 01, 02, 03, 04, 07, 08

    @Column(name = "dpp", precision = 19, scale = 2)
    private BigDecimal dpp;  // Dasar Pengenaan Pajak

    @Column(name = "ppn", precision = 19, scale = 2)
    private BigDecimal ppn;  // PPN amount

    @Column(name = "ppnbm", precision = 19, scale = 2)
    private BigDecimal ppnbm = BigDecimal.ZERO;  // PPnBM

    // ========================================
    // e-Bupot fields (PPh)
    // ========================================

    @Size(max = 30, message = "Bupot number max 30 characters")
    @Column(name = "bupot_number", length = 30)
    private String bupotNumber;

    @Size(max = 20, message = "Tax object code max 20 characters")
    @Column(name = "tax_object_code", length = 20)
    private String taxObjectCode;  // e.g., 24-104-01

    @Column(name = "gross_amount", precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    // ========================================
    // Counterparty information
    // ========================================

    @Size(max = 20, message = "NPWP max 20 characters")
    @Column(name = "counterparty_npwp", length = 20)
    private String counterpartyNpwp;

    @Size(max = 22, message = "NITKU max 22 characters")
    @Column(name = "counterparty_nitku", length = 22)
    private String counterpartyNitku;

    @Size(max = 16, message = "NIK max 16 characters")
    @Column(name = "counterparty_nik", length = 16)
    private String counterpartyNik;

    @Size(max = 10, message = "ID type max 10 characters")
    @Column(name = "counterparty_id_type", length = 10)
    private String counterpartyIdType;  // TIN or NIK

    @Size(max = 255, message = "Counterparty name max 255 characters")
    @Column(name = "counterparty_name", length = 255)
    private String counterpartyName;

    @Column(name = "counterparty_address", columnDefinition = "TEXT")
    private String counterpartyAddress;

    // ========================================
    // Tax type categorization
    // ========================================

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", length = 20)
    private TaxType taxType;

    // ========================================
    // Helper methods
    // ========================================

    public boolean isEFaktur() {
        return taxType == TaxType.PPN_KELUARAN || taxType == TaxType.PPN_MASUKAN;
    }

    public boolean isEBupot() {
        return taxType == TaxType.PPH_21 || taxType == TaxType.PPH_23 || taxType == TaxType.PPH_42;
    }

    public String getCounterpartyIdNumber() {
        if ("NIK".equals(counterpartyIdType)) {
            return counterpartyNik;
        }
        return counterpartyNpwp;
    }
}
