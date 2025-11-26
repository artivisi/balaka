package com.artivisi.accountingfinance.dto.dataimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record COAImportDto(
        @NotBlank(message = "Account code is required")
        String code,

        @NotBlank(message = "Account name is required")
        String name,

        @NotNull(message = "Account type is required")
        String type,

        @NotNull(message = "Normal balance is required")
        String normalBalance,

        String parentCode,

        Boolean isHeader,

        Boolean isPermanent,

        String description
) {
    public COAImportDto {
        if (isHeader == null) {
            isHeader = false;
        }
        if (isPermanent == null) {
            isPermanent = true;
        }
    }
}
