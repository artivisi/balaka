package com.artivisi.accountingfinance.enums;

public enum RecurringStatus {
    ACTIVE("Active", "Aktif", "green"),
    PAUSED("Paused", "Dijeda", "yellow"),
    COMPLETED("Completed", "Selesai", "gray");

    private final String englishName;
    private final String indonesianName;
    private final String colorCode;

    RecurringStatus(String englishName, String indonesianName, String colorCode) {
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
