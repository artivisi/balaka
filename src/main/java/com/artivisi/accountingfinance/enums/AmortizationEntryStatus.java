package com.artivisi.accountingfinance.enums;

public enum AmortizationEntryStatus {
    PENDING("Pending", "Menunggu", "yellow"),
    POSTED("Posted", "Terposting", "green"),
    SKIPPED("Skipped", "Dilewati", "gray");

    private final String englishName;
    private final String indonesianName;
    private final String colorCode;

    AmortizationEntryStatus(String englishName, String indonesianName, String colorCode) {
        this.englishName = englishName;
        this.indonesianName = indonesianName;
        this.colorCode = colorCode;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    public String getColorCode() {
        return colorCode;
    }
}
