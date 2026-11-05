package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TaxInquiryEntryType;
import com.artivisi.accountingfinance.enums.TaxInquiryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One event in an inquiry case: reply sent, meeting note, note, deadline or status change, decision, disclosure or
 * correction of an earlier entry. Append-only: the database refuses UPDATE and DELETE.
 */
@Entity
@Table(name = "tax_inquiry_entries")
@Immutable
@Getter
@Setter
public class TaxInquiryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_case", nullable = false)
    private TaxInquiryCase inquiryCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private TaxInquiryEntryType entryType;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item")
    private TaxInquiryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_corrects")
    private TaxInquiryEntry corrects;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document")
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private TaxInquiryStatus newStatus;

    @Column(name = "new_deadline")
    private LocalDate newDeadline;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
