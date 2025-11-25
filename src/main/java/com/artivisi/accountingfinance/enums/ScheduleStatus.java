package com.artivisi.accountingfinance.enums;

public enum ScheduleStatus {
    ACTIVE("Active", "Aktif", "green"),
    COMPLETED("Completed", "Selesai", "blue"),
    CANCELLED("Cancelled", "Dibatalkan", "red");

    private final String englishName;
    private final String indonesianName;
    private final String colorCode;

    ScheduleStatus(String englishName, String indonesianName, String colorCode) {
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
