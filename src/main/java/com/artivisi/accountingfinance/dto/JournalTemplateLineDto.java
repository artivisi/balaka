package com.artivisi.accountingfinance.dto;

import com.artivisi.accountingfinance.enums.JournalPosition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record JournalTemplateLineDto(
        UUID id,

        @NotNull(message = "Account is required")
        UUID accountId,

        @NotNull(message = "Position is required")
        JournalPosition position,

        @NotBlank(message = "Formula is required")
        @Size(max = 255, message = "Formula must not exceed 255 characters")
        String formula,

        Integer lineOrder,

        String description
) {}
