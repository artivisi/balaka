package com.artivisi.accountingfinance.enums;

/**
 * Coretax paperwork that attaches to a tax period rather than to a transaction.
 */
public enum TaxDocumentType {
    SPT("Surat Pemberitahuan"),
    BPE("Bukti Penerimaan Elektronik"),
    STP("Surat Tagihan Pajak"),
    TEGURAN("Surat Teguran"),
    SP2DK("Surat Permintaan Penjelasan atas Data dan/atau Keterangan"),
    SKPLB("Surat Ketetapan Pajak Lebih Bayar"),
    SKPKPP("Surat Keputusan Pengembalian Kelebihan Pembayaran Pajak"),
    KODE_BILLING("Kode Billing"),
    BPN("Bukti Penerimaan Negara"),
    SURAT("Surat Lainnya");

    private final String indonesianName;

    TaxDocumentType(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
