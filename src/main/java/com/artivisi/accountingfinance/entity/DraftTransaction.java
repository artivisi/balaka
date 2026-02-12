package com.artivisi.accountingfinance.entity;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "draft_transactions")
@Getter
@Setter
@NoArgsConstructor
public class DraftTransaction {

    public enum Source {
        TELEGRAM, MANUAL, EMAIL, API
    }

    public enum Status {
        PENDING, APPROVED, REJECTED, AUTO_APPROVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Source is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private Source source;

    @Size(max = 255)
    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    // Extracted data
    @Size(max = 255)
    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Size(max = 10)
    @Column(name = "currency", length = 10)
    private String currency = "IDR";

    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    @Size(max = 50)
    @Column(name = "receipt_type", length = 50)
    private String receiptType;

    // API fields
    @Size(max = 50)
    @Column(name = "api_source", length = 50)
    private String apiSource; // "claude-code", "gemini-cli", "curl", etc.

    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata; // items, category, extra data

    // Confidence scores (0.0 - 1.0)
    @Column(name = "merchant_confidence", precision = 3, scale = 2)
    private BigDecimal merchantConfidence;

    @Column(name = "date_confidence", precision = 3, scale = 2)
    private BigDecimal dateConfidence;

    @Column(name = "amount_confidence", precision = 3, scale = 2)
    private BigDecimal amountConfidence;

    @Column(name = "overall_confidence", precision = 3, scale = 2)
    private BigDecimal overallConfidence;

    // Suggested mapping
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_suggested_template")
    private JournalTemplate suggestedTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_merchant_mapping")
    private MerchantMapping merchantMapping;

    // Attached document
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document")
    private Document document;

    // Workflow status
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Size(max = 500)
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Created transaction (after approval)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    // Audit
    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Size(max = 100)
    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isApproved() {
        return status == Status.APPROVED || status == Status.AUTO_APPROVED;
    }

    public void approve(String approvedBy) {
        this.status = Status.APPROVED;
        this.processedBy = approvedBy;
        this.processedAt = LocalDateTime.now();
    }

    public void autoApprove() {
        this.status = Status.AUTO_APPROVED;
        this.processedBy = "SYSTEM";
        this.processedAt = LocalDateTime.now();
    }

    public void reject(String rejectedBy, String reason) {
        this.status = Status.REJECTED;
        this.processedBy = rejectedBy;
        this.processedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public String getAmountFormatted() {
        if (amount == null) {
            return "-";
        }
        return String.format("%s %,.0f", currency != null ? currency : "IDR", amount);
    }

    public String getConfidencePercentage() {
        if (overallConfidence == null) {
            return "-";
        }
        return String.format("%.0f%%", overallConfidence.multiply(BigDecimal.valueOf(100)));
    }
}
