package com.artivisi.accountingfinance.enums;

public enum TaxType {
    PPN_KELUARAN("PPN Keluaran", "Output VAT (Sales)"),
    PPN_MASUKAN("PPN Masukan", "Input VAT (Purchases)"),
    PPH_21("PPh 21", "Employment Income Tax"),
    PPH_23("PPh 23", "Service/Rental Withholding Tax"),
    PPH_26("PPh 26", "Foreign Payee Withholding Tax"),
    PPH_42("PPh 4(2)", "Final Income Tax"),
    PPH_25("PPh 25", "Monthly Installment Tax"),
    PPH_29("PPh 29", "Annual Tax Payment");

    private final String indonesianName;
    private final String englishName;

    TaxType(String indonesianName, String englishName) {
        this.indonesianName = indonesianName;
        this.englishName = englishName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    public String getEnglishName() {
        return englishName;
    }
}
