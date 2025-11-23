package com.artivisi.accountingfinance.dto;

import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChartOfAccountDto(
        UUID id,

        @NotBlank(message = "Account code is required")
        @Size(max = 20, message = "Account code must not exceed 20 characters")
        String accountCode,

        @NotBlank(message = "Account name is required")
        @Size(max = 255, message = "Account name must not exceed 255 characters")
        String accountName,

        @NotNull(message = "Account type is required")
        AccountType accountType,

        @NotNull(message = "Normal balance is required")
        NormalBalance normalBalance,

        UUID parentId,

        Integer level,

        Boolean isHeader,

        Boolean active,

        String description
) {}
