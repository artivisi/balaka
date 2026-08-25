package com.artivisi.accountingfinance.enums;

/**
 * Tax return types tracked in the filing register (issue #35). Distinct from
 * {@link TaxType}, which classifies tax on an individual transaction — these
 * identify the SPT that covers a whole tax period.
 */
public enum TaxFilingType {
    PPN("SPT Masa PPN", true),
    PPH21("SPT Masa PPh 21", true),
    PPH23_UNIFIKASI("SPT Masa PPh Unifikasi", true),
    PPH_BADAN("SPT Tahunan PPh Badan", false);

    private final String indonesianName;
    private final boolean monthly;

    TaxFilingType(String indonesianName, boolean monthly) {
        this.indonesianName = indonesianName;
        this.monthly = monthly;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    /** True when the period is a masa (YYYY-MM); false for the annual PPh Badan return (YYYY). */
    public boolean isMonthly() {
        return monthly;
    }
}
