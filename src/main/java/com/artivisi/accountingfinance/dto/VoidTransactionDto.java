package com.artivisi.accountingfinance.dto;

import com.artivisi.accountingfinance.enums.VoidReason;
import jakarta.validation.constraints.NotNull;

public record VoidTransactionDto(
        @NotNull(message = "Void reason is required")
        VoidReason reason,

        String notes
) {}
