package com.artivisi.accountingfinance.enums;

/** Kind of tax office inquiry handled as a case. */
public enum TaxInquiryType {
    SP2DK("SP2DK"),
    PEMERIKSAAN("Pemeriksaan"),
    OTHER("Lainnya");

    private final String indonesianName;

    TaxInquiryType(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
