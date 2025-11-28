package com.artivisi.accountingfinance.enums;

public enum TaxDeadlineType {
    PPH_21_PAYMENT("Setor PPh 21", "Pembayaran PPh 21"),
    PPH_23_PAYMENT("Setor PPh 23", "Pembayaran PPh 23"),
    PPH_42_PAYMENT("Setor PPh 4(2)", "Pembayaran PPh 4(2)"),
    PPH_25_PAYMENT("Setor PPh 25", "Pembayaran PPh 25 (Angsuran)"),
    PPN_PAYMENT("Setor PPN", "Pembayaran PPN"),

    SPT_PPH_21("SPT PPh 21", "Pelaporan SPT Masa PPh 21"),
    SPT_PPH_23("SPT PPh 23", "Pelaporan SPT Masa PPh 23"),
    SPT_PPN("SPT PPN", "Pelaporan SPT Masa PPN");

    private final String displayName;
    private final String description;

    TaxDeadlineType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPayment() {
        return this.name().endsWith("_PAYMENT");
    }

    public boolean isReporting() {
        return this.name().startsWith("SPT_");
    }
}
