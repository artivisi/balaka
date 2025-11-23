package com.artivisi.accountingfinance.enums;

public enum CashFlowCategory {
    OPERATING("Operating Activities", "Aktivitas Operasional"),
    INVESTING("Investing Activities", "Aktivitas Investasi"),
    FINANCING("Financing Activities", "Aktivitas Pendanaan");

    private final String englishName;
    private final String indonesianName;

    CashFlowCategory(String englishName, String indonesianName) {
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
