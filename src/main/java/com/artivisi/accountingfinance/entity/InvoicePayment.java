package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.PaymentMethod;
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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "invoice_payments")
@Getter
@Setter
@NoArgsConstructor
public class InvoicePayment extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_invoice", nullable = false)
    @JsonIgnore
    @NotNull
    private Invoice invoice;

    @NotNull(message = "Tanggal pembayaran wajib diisi")
    @Column(name = "payment_date", nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paymentDate;

    @NotNull(message = "Jumlah pembayaran wajib diisi")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Metode pembayaran wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Size(max = 100)
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

}
