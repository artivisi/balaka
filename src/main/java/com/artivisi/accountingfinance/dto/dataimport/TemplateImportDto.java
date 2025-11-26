package com.artivisi.accountingfinance.dto.dataimport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TemplateImportDto(
        @NotBlank(message = "Template name is required")
        String name,

        @NotNull(message = "Category is required")
        String category,

        @NotNull(message = "Cash flow category is required")
        String cashFlowCategory,

        String description,

        String templateType,

        List<String> tags,

        @Valid
        @Size(min = 2, message = "Template must have at least 2 lines")
        List<TemplateLineImportDto> lines
) {
    public TemplateImportDto {
        if (templateType == null) {
            templateType = "SIMPLE";
        }
    }
}
