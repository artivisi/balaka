package com.artivisi.accountingfinance.dto;

import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record JournalTemplateDto(
        UUID id,

        @NotBlank(message = "Template name is required")
        @Size(max = 255, message = "Template name must not exceed 255 characters")
        String templateName,

        @NotNull(message = "Category is required")
        TemplateCategory category,

        @NotNull(message = "Cash flow category is required")
        CashFlowCategory cashFlowCategory,

        @NotNull(message = "Template type is required")
        TemplateType templateType,

        String description,

        Boolean isFavorite,

        Boolean active,

        @Valid
        @Size(min = 2, message = "Template must have at least 2 lines")
        List<JournalTemplateLineDto> lines
) {}
