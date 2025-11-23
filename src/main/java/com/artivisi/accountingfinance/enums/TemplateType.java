package com.artivisi.accountingfinance.enums;

public enum TemplateType {
    SIMPLE("Simple", "Sederhana"),
    DETAILED("Detailed", "Terperinci");

    private final String englishName;
    private final String indonesianName;

    TemplateType(String englishName, String indonesianName) {
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
