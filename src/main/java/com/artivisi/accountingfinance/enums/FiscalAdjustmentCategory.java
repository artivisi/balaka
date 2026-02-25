package com.artivisi.accountingfinance.enums;

public enum FiscalAdjustmentCategory {
    PERMANENT("Permanent Difference", "Beda Tetap"),
    TEMPORARY("Temporary Difference", "Beda Waktu");

    private final String englishName;
    private final String indonesianName;

    FiscalAdjustmentCategory(String englishName, String indonesianName) {
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
