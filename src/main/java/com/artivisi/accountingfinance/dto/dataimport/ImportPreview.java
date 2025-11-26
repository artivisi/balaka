package com.artivisi.accountingfinance.dto.dataimport;

import java.util.List;

public record ImportPreview(
        String importType,
        String fileName,
        String fileVersion,
        int recordCount,
        int newRecordCount,
        int existingRecordCount,
        List<String> sampleRecords,
        List<ImportError> validationErrors,
        boolean hasErrors
) {
    public static ImportPreview forCOA(
            String fileName,
            String fileVersion,
            int recordCount,
            int newRecordCount,
            int existingRecordCount,
            List<String> sampleRecords,
            List<ImportError> validationErrors
    ) {
        return new ImportPreview(
                "COA",
                fileName,
                fileVersion,
                recordCount,
                newRecordCount,
                existingRecordCount,
                sampleRecords,
                validationErrors,
                !validationErrors.isEmpty()
        );
    }

    public static ImportPreview forTemplate(
            String fileName,
            String fileVersion,
            int recordCount,
            int newRecordCount,
            int existingRecordCount,
            List<String> sampleRecords,
            List<ImportError> validationErrors
    ) {
        return new ImportPreview(
                "TEMPLATE",
                fileName,
                fileVersion,
                recordCount,
                newRecordCount,
                existingRecordCount,
                sampleRecords,
                validationErrors,
                !validationErrors.isEmpty()
        );
    }
}
