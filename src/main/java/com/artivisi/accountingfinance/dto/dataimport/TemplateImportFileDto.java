package com.artivisi.accountingfinance.dto.dataimport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TemplateImportFileDto(
        @NotBlank(message = "File name is required")
        String name,

        String version,

        @Valid
        @NotEmpty(message = "At least one template is required")
        List<TemplateImportDto> templates
) {}
