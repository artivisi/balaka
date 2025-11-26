package com.artivisi.accountingfinance.dto.dataimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateLineImportDto(
        @NotBlank(message = "Account code is required")
        String accountCode,

        @NotNull(message = "Position is required")
        String position,

        @NotBlank(message = "Formula is required")
        String formula,

        String description
) {
    public TemplateLineImportDto {
        if (formula == null || formula.isBlank()) {
            formula = "amount";
        }
    }
}
