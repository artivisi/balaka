package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_templates")
@Getter
@Setter
@NoArgsConstructor
public class JournalTemplate extends BaseEntity {

    @NotBlank(message = "Template name is required")
    @Size(max = 255, message = "Template name must not exceed 255 characters")
    @Column(name = "template_name", nullable = false, length = 255)
    private String templateName;

    @NotNull(message = "Category is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private TemplateCategory category;

    @NotNull(message = "Cash flow category is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "cash_flow_category", nullable = false, length = 20)
    private CashFlowCategory cashFlowCategory;

    @NotNull(message = "Template type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 20)
    private TemplateType templateType = TemplateType.SIMPLE;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_favorite", nullable = false)
    private Boolean isFavorite = false;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Min(value = 1, message = "Version must be at least 1")
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Min(value = 0, message = "Usage count cannot be negative")
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "journalTemplate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineOrder ASC")
    private List<JournalTemplateLine> lines = new ArrayList<>();

    public void addLine(JournalTemplateLine line) {
        lines.add(line);
        line.setJournalTemplate(this);
    }

    public void removeLine(JournalTemplateLine line) {
        lines.remove(line);
        line.setJournalTemplate(null);
    }
}
