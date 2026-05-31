package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Kode proyek wajib diisi")
    @Size(max = 50, message = "Kode proyek maksimal 50 karakter")
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Nama proyek wajib diisi")
    @Size(max = 255, message = "Nama proyek maksimal 255 karakter")
    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client")
    private Client client;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "contract_value", precision = 19, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "budget_amount", precision = 19, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ProjectMilestone> milestones = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ProjectPaymentTerm> paymentTerms = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "project")
    private List<Invoice> invoices = new ArrayList<>();

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

    public boolean isActive() {
        return status == ProjectStatus.ACTIVE;
    }

    public boolean isCompleted() {
        return status == ProjectStatus.COMPLETED;
    }

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    public int getProgressPercent() {
        if (milestones.isEmpty()) {
            return 0;
        }
        int totalWeight = milestones.stream()
                .mapToInt(ProjectMilestone::getWeightPercent)
                .sum();
        if (totalWeight == 0) {
            return 0;
        }
        int completedWeight = milestones.stream()
                .filter(ProjectMilestone::isCompleted)
                .mapToInt(ProjectMilestone::getWeightPercent)
                .sum();
        return (completedWeight * 100) / totalWeight;
    }

    /**
     * Combobox label for the bound client. Used by the project form's
     * clientPicker so data-initial-label renders the existing selection.
     */
    public String getClientLabel() {
        if (client == null) return "";
        String code = client.getCode() == null ? "" : client.getCode();
        String name = client.getName() == null ? "" : client.getName();
        if (code.isEmpty() && name.isEmpty()) return "";
        if (code.isEmpty()) return name;
        if (name.isEmpty()) return code;
        return code + " - " + name;
    }
}
