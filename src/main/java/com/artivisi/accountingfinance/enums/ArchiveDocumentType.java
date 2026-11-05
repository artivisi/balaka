package com.artivisi.accountingfinance.enums;

/** Kind of a historical document in the archive (issue #51). */
public enum ArchiveDocumentType {
    FAKTUR_PAJAK("Faktur Pajak"),
    BUPOT("Bukti Potong"),
    SPT("SPT"),
    BPE("Bukti Penerimaan Elektronik"),
    BANK_STATEMENT("Rekening Koran"),
    INVOICE("Invoice"),
    RECEIPT("Kuitansi / Bukti Bayar"),
    CONTRACT("Kontrak"),
    TAX_LETTER("Surat dari Kantor Pajak"),
    OTHER("Lainnya");

    private final String indonesianName;

    ArchiveDocumentType(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
