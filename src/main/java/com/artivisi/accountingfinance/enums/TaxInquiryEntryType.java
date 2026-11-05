package com.artivisi.accountingfinance.enums;

/** Kind of an append-only entry in an inquiry case. */
public enum TaxInquiryEntryType {
    REPLY("Balasan dikirim"),
    MEETING("Catatan pertemuan"),
    NOTE("Catatan"),
    DEADLINE_CHANGE("Perubahan tenggat"),
    STATUS_CHANGE("Perubahan status"),
    DECISION("Keputusan"),
    DISCLOSURE("Penyerahan dokumen"),
    CORRECTION("Koreksi");

    private final String indonesianName;

    TaxInquiryEntryType(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
