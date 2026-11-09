package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contract register entry with exactly one counterparty, client or vendor. Invoices and transactions reference it
 * through their own {@code id_contract}. It counts as CONTRACT evidence only once its file is attached.
 */
@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class Contract extends TimestampedEntity {

    @NotBlank(message = "Nomor kontrak wajib diisi")
    @Size(max = 100, message = "Nomor kontrak maksimal 100 karakter")
    @Column(name = "contract_number", nullable = false, unique = true, length = 100)
    private String contractNumber;

    @NotBlank(message = "Judul kontrak wajib diisi")
    @Size(max = 255, message = "Judul kontrak maksimal 255 karakter")
    @Column(name = "title", nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vendor")
    private Vendor vendor;

    @NotNull(message = "Tanggal mulai wajib diisi")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "contract_value", precision = 19, scale = 2)
    private BigDecimal contractValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document")
    private Document document;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @NotBlank
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    public String getCounterpartyName() {
        return client != null ? client.getName() : vendor.getName();
    }

    public String getCounterpartyKind() {
        return client != null ? "Klien" : "Vendor";
    }

    public boolean hasFile() {
        return document != null;
    }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }
}
