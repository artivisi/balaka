package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_deadline_completions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"id_tax_deadline", "year", "month"})
})
@Getter
@Setter
@NoArgsConstructor
public class TaxDeadlineCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Tax deadline wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax_deadline", nullable = false)
    private TaxDeadline taxDeadline;

    @NotNull(message = "Tahun wajib diisi")
    @Min(value = 2000, message = "Tahun minimal 2000")
    @Max(value = 2100, message = "Tahun maksimal 2100")
    @Column(name = "year", nullable = false)
    private Integer year;

    @NotNull(message = "Bulan wajib diisi")
    @Min(value = 1, message = "Bulan minimal 1")
    @Max(value = 12, message = "Bulan maksimal 12")
    @Column(name = "month", nullable = false)
    private Integer month;

    @NotNull(message = "Tanggal selesai wajib diisi")
    @Column(name = "completed_date", nullable = false)
    private LocalDate completedDate;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

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

    public String getPeriodName() {
        return String.format("%04d-%02d", year, month);
    }

    public String getPeriodDisplayName() {
        String[] monthNames = {"", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
        return monthNames[month] + " " + year;
    }

    public boolean isOnTime() {
        LocalDate dueDate = taxDeadline.getDueDateForPeriod(year, month);
        return !completedDate.isAfter(dueDate);
    }
}
