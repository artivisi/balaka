package com.artivisi.accountingfinance.enums;

public enum TemplateCategory {
    INCOME("Income", "Pendapatan", "green"),
    EXPENSE("Expense", "Pengeluaran", "red"),
    PAYMENT("Payment", "Pembayaran", "blue"),
    RECEIPT("Receipt", "Penerimaan", "cyan"),
    TRANSFER("Transfer", "Transfer", "purple");

    private final String englishName;
    private final String indonesianName;
    private final String colorCode;

    TemplateCategory(String englishName, String indonesianName, String colorCode) {
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
