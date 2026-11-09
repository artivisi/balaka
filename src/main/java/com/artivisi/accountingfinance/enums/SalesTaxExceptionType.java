package com.artivisi.accountingfinance.enums;

/** Breaks in the chain from invoice to faktur, receipt and bupot. */
public enum SalesTaxExceptionType {
    PAID_NO_FAKTUR("Dibayar tanpa faktur"),
    FAKTUR_NO_REVENUE("Faktur tanpa pendapatan terposting"),
    DUPLICATE_FAKTUR("Lebih dari satu faktur pada satu invoice"),
    MISSING_BUPOT("Bukti potong belum ada");

    private final String indonesianName;

    SalesTaxExceptionType(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
