package com.artivisi.accountingfinance.enums;

/** Status of an inquiry case. CLOSED requires a recorded decision. */
public enum TaxInquiryStatus {
    OPEN("Terbuka"),
    RESPONDED("Sudah dijawab"),
    CLOSED("Selesai");

    private final String indonesianName;

    TaxInquiryStatus(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
