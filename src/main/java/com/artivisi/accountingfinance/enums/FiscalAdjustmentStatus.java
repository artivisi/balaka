package com.artivisi.accountingfinance.enums;

/**
 * Only CONFIRMED adjustments enter the fiscal reconciliation and SPT exports.
 */
public enum FiscalAdjustmentStatus {
    DRAFT("Draf"),
    CONFIRMED("Dikonfirmasi");

    private final String indonesianName;

    FiscalAdjustmentStatus(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
