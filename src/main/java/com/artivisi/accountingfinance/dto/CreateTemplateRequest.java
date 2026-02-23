package com.artivisi.accountingfinance.dto;

import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateTemplateRequest(
        @NotBlank(message = "Template name is required")
        @Size(max = 255, message = "Template name must not exceed 255 characters")
        String templateName,

        @NotNull(message = "Category is required")
        TemplateCategory category,

        @NotNull(message = "Cash flow category is required")
        CashFlowCategory cashFlowCategory,

        TemplateType templateType,

        String description,

        String semanticDescription,

        String[] keywords,

        String[] exampleMerchants,

        BigDecimal typicalAmountMin,

        BigDecimal typicalAmountMax,

        String[] merchantPatterns,

        @NotNull(message = "Lines are required")
        @Size(min = 2, message = "Template must have at least 2 lines")
        @Valid
        List<TemplateLine> lines
) {
    public record TemplateLine(
            @NotNull(message = "Line order is required")
            @Min(value = 1, message = "Line order must be at least 1")
            Integer lineOrder,

            @NotNull(message = "Position is required")
            JournalPosition position,

            @NotBlank(message = "Formula is required")
            @Size(max = 255, message = "Formula must not exceed 255 characters")
            String formula,

            UUID accountId,

            @Size(max = 100, message = "Account hint must not exceed 100 characters")
            String accountHint,

            String description
    ) {}
}
