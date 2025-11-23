package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_sequences", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"sequence_type", "year"})
})
@Getter
@Setter
@NoArgsConstructor
public class TransactionSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Sequence type is required")
    @Size(max = 50, message = "Sequence type must not exceed 50 characters")
    @Column(name = "sequence_type", nullable = false, length = 50)
    private String sequenceType;

    @NotBlank(message = "Prefix is required")
    @Size(max = 20, message = "Prefix must not exceed 20 characters")
    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    @Min(value = 2000, message = "Year must be at least 2000")
    @Column(name = "year", nullable = false)
    private Integer year;

    @Min(value = 0, message = "Last number cannot be negative")
    @Column(name = "last_number", nullable = false)
    private Integer lastNumber = 0;

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

    public String getNextNumber() {
        this.lastNumber++;
        return String.format("%s-%d-%04d", prefix, year, lastNumber);
    }
}
