package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TaxDocumentType;
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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Coretax paperwork attached to a {@link TaxFiling}. The file itself is stored
 * in {@link Document}; this record carries the tax metadata and the document
 * chain (a teguran references the STP it chases, an STP references the SPT).
 */
@Entity
@Table(name = "tax_documents")
@Getter
@Setter
@NoArgsConstructor
public class TaxDocument extends TimestampedEntity {

    @NotNull(message = "Pelaporan pajak wajib diisi")
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tax_filing", nullable = false)
    private TaxFiling taxFiling;

    @NotNull(message = "Berkas wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_document", nullable = false)
    private Document document;

    @NotNull(message = "Jenis dokumen wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private TaxDocumentType docType;

    @Size(max = 100, message = "Nomor dokumen maksimal 100 karakter")
    @Column(name = "doc_number", length = 100)
    private String docNumber;

    @Column(name = "doc_date")
    private LocalDate docDate;

    /** Payment or response deadline, e.g. 30 days from an STP or SP2DK. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @PositiveOrZero(message = "Nilai dokumen tidak boleh negatif")
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    /** Number of the document this one answers or chases, e.g. teguran -> STP. */
    @Size(max = 100, message = "Nomor referensi maksimal 100 karakter")
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
