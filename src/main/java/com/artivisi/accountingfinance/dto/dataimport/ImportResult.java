package com.artivisi.accountingfinance.dto.dataimport;

import java.util.List;

public record ImportResult(
        boolean success,
        int totalRecords,
        int successCount,
        int errorCount,
        List<ImportError> errors,
        String message
) {
    public static ImportResult success(int totalRecords, int successCount) {
        return new ImportResult(
                true,
                totalRecords,
                successCount,
                0,
                List.of(),
                "Import berhasil: " + successCount + " dari " + totalRecords + " data berhasil diimport"
        );
    }

    public static ImportResult failed(List<ImportError> errors, int totalRecords) {
        return new ImportResult(
                false,
                totalRecords,
                0,
                errors.size(),
                errors,
                "Import gagal: ditemukan " + errors.size() + " error"
        );
    }

    public static ImportResult partial(int totalRecords, int successCount, List<ImportError> errors) {
        return new ImportResult(
                true,
                totalRecords,
                successCount,
                errors.size(),
                errors,
                "Import selesai dengan warning: " + successCount + " berhasil, " + errors.size() + " dilewati"
        );
    }
}
