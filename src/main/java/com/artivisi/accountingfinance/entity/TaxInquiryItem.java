package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.SelfTestLine;
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

import java.time.LocalDateTime;
import java.util.UUID;

/** One item the tax office asks about. Append-only: the database refuses UPDATE and DELETE. */
@Entity
@Table(name = "tax_inquiry_items")
@Immutable
@Getter
@Setter
public class TaxInquiryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_case", nullable = false)
    private TaxInquiryCase inquiryCase;

    @Column(name = "item_no", nullable = false)
    private Integer itemNo;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** The self-test line the item questions, with periodMonth (0 = whole year). Both null when not linked. */
    @Enumerated(EnumType.STRING)
    @Column(name = "self_test_line", length = 40)
    private SelfTestLine selfTestLine;

    @Column(name = "period_month")
    private Integer periodMonth;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
