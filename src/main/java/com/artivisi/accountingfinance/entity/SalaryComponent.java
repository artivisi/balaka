package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "salary_components")
@Getter
@Setter
@NoArgsConstructor
public class SalaryComponent extends TimestampedEntity {

    @NotBlank(message = "Kode komponen wajib diisi")
    @Size(max = 20, message = "Kode komponen maksimal 20 karakter")
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank(message = "Nama komponen wajib diisi")
    @Size(max = 100, message = "Nama komponen maksimal 100 karakter")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 255, message = "Deskripsi maksimal 255 karakter")
    @Column(name = "description")
    private String description;

    @NotNull(message = "Tipe komponen wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 30)
    private SalaryComponentType componentType;

    // Whether this component is calculated as percentage (of base salary)
    @Column(name = "is_percentage", nullable = false)
    private Boolean isPercentage = false;

    // Default percentage rate (e.g., 0.04 for 4%)
    @Column(name = "default_rate", precision = 10, scale = 4)
    private BigDecimal defaultRate;

    // Default fixed amount
    @Column(name = "default_amount", precision = 15, scale = 2)
    private BigDecimal defaultAmount;

    // Whether this is a system component (cannot be deleted)
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    // Display order for UI
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    // Whether this component is active
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // For tax calculation - whether this earning is taxable
    @Column(name = "is_taxable", nullable = false)
    private Boolean isTaxable = true;

    // For BPJS components - category for reporting
    @Size(max = 50, message = "Kategori BPJS maksimal 50 karakter")
    @Column(name = "bpjs_category", length = 50)
    private String bpjsCategory;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public boolean isPercentageBased() {
        return Boolean.TRUE.equals(isPercentage);
    }

    public boolean isSystemComponent() {
        return Boolean.TRUE.equals(isSystem);
    }

    public String getDisplayName() {
        return code + " - " + name;
    }
}
