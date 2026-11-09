package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TaxInquiryStatus;
import com.artivisi.accountingfinance.enums.TaxInquiryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One tax office inquiry (SP2DK, pemeriksaan) handled as a case (issue #51). The letter fields are fixed at
 * creation. status, deadline, decision and closedOn change only through {@code TaxInquiryService}, which appends
 * the matching entry first, so the entries are the full history.
 */
@Entity
@Table(name = "tax_inquiry_cases")
@Getter
@Setter
public class TaxInquiryCase extends TimestampedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 20, updatable = false)
    private TaxInquiryType inquiryType;

    @Column(name = "tax_year", nullable = false, updatable = false)
    private Integer taxYear;

    @Column(name = "letter_number", nullable = false, length = 100, updatable = false)
    private String letterNumber;

    @Column(name = "letter_date", nullable = false, updatable = false)
    private LocalDate letterDate;

    @Column(name = "received_date", nullable = false, updatable = false)
    private LocalDate receivedDate;

    @Column(name = "tax_office", nullable = false, length = 200, updatable = false)
    private String taxOffice;

    @Column(name = "subject", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String subject;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaxInquiryStatus status;

    @Column(name = "decision", columnDefinition = "TEXT")
    private String decision;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_letter_document", nullable = false, updatable = false)
    private Document letterDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax_filing", updatable = false)
    private TaxFiling taxFiling;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax_document", updatable = false)
    private TaxDocument taxDocument;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    public String getTitle() {
        return inquiryType.getIndonesianName() + " " + letterNumber;
    }
}
