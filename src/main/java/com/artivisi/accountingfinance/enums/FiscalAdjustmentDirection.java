package com.artivisi.accountingfinance.enums;

public enum FiscalAdjustmentDirection {
    POSITIVE("Positive Adjustment", "Koreksi Positif"),
    NEGATIVE("Negative Adjustment", "Koreksi Negatif");

    private final String englishName;
    private final String indonesianName;

    FiscalAdjustmentDirection(String englishName, String indonesianName) {
        this.englishName = englishName;
        this.indonesianName = indonesianName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
