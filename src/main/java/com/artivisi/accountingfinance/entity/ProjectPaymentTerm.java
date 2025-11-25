package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.PaymentTrigger;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_payment_terms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_project", "sequence"}))
@Getter
@Setter
@NoArgsConstructor
public class ProjectPaymentTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project", nullable = false)
    private Project project;

    @Min(value = 1, message = "Urutan minimal 1")
    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @NotBlank(message = "Nama termin wajib diisi")
    @Size(max = 255, message = "Nama termin maksimal 255 karakter")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Pemicu pembayaran wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "due_trigger", nullable = false, length = 20)
    private PaymentTrigger dueTrigger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_milestone")
    private ProjectMilestone milestone;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_template")
    private JournalTemplate template;

    @Column(name = "auto_post", nullable = false)
    private Boolean autoPost = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getCalculatedAmount(BigDecimal contractValue) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            return amount;
        }
        if (percentage != null && contractValue != null) {
            return contractValue.multiply(percentage).divide(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }
}
