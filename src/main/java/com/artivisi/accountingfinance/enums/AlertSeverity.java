package com.artivisi.accountingfinance.enums;

public enum AlertSeverity {
    INFO("Info", "Informasi"),
    WARNING("Warning", "Peringatan"),
    CRITICAL("Critical", "Kritis");

    private final String englishName;
    private final String indonesianName;

    AlertSeverity(String englishName, String indonesianName) {
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
