package com.artivisi.accountingfinance.enums;

public enum TransactionStatus {
    DRAFT("Draft", "Draft", "yellow"),
    POSTED("Posted", "Posted", "green"),
    VOID("Void", "Void", "red");

    private final String englishName;
    private final String indonesianName;
    private final String colorCode;

    TransactionStatus(String englishName, String indonesianName, String colorCode) {
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
