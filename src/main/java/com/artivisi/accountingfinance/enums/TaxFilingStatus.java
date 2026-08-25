package com.artivisi.accountingfinance.enums;

/**
 * Reporting state of a tax period. Set explicitly by the caller — the register
 * records what happened at DJP, it does not infer it from deadlines.
 */
public enum TaxFilingStatus {
    NOT_FILED("Belum Lapor"),
    FILED("Sudah Lapor"),
    LATE("Terlambat"),
    CORRECTED("Dibetulkan");

    private final String indonesianName;

    TaxFilingStatus(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
