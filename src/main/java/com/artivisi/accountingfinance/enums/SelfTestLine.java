package com.artivisi.accountingfinance.enums;

/** Lines of the tax-office self-test (issue #45), in report order. perMasa lines have one row per month. */
public enum SelfTestLine {
    REVENUE_VS_PPN_DPP("Pendapatan vs DPP PPN", true,
            "Pendapatan (kredit akun pendapatan, jurnal posted, tanpa jurnal penutup)",
            "DPP faktur keluaran bernomor pada transaksi posted, menurut tanggal faktur"),
    SALARY_VS_PPH21_DPP("Beban gaji vs DPP PPh 21", true,
            "Beban gaji (akun gaji dari template payroll, debit dikurangi kredit)",
            "Penghasilan bruto PPh 21 payroll posted (gaji, bonus, THR, natura)"),
    SERVICE_VS_PPH23_DPP("Beban jasa/sewa vs DPP PPh 23", false,
            "Beban pada akun berperan Jasa/sewa objek PPh 23",
            "Bruto bukti potong PPh 23 yang dipotong perusahaan"),
    RENT_VS_PPH42_DPP("Beban sewa tanah/bangunan vs DPP PPh 4(2)", false,
            "Beban pada akun berperan Sewa tanah/bangunan",
            "Bruto bukti potong PPh 4(2) yang dipotong perusahaan"),
    FIXED_ASSET_ADDITIONS("Penambahan aset tetap vs register", false,
            "Debit akun harga perolehan aset tetap",
            "Harga perolehan aset yang dibeli tahun ini menurut register"),
    FIXED_ASSET_DISPOSALS("Pengurangan aset tetap vs pelepasan", false,
            "Kredit akun harga perolehan aset tetap",
            "Harga perolehan aset yang dilepas tahun ini menurut register"),
    CAPITAL_MOVEMENT("Mutasi modal", false,
            "Mutasi akun ekuitas (kredit dikurangi debit), tanpa jurnal penutup",
            null),
    NON_OPERATING_ASSET_MOVEMENT("Mutasi aset non-operasional", false,
            "Mutasi akun berperan Aset non-operasional (debit dikurangi kredit)",
            null),
    UNITEMISED_OTHER_EXPENSE("Beban lain-lain tanpa rincian", false,
            "Beban pada akun berperan Beban lain-lain",
            "Bagian yang berbukti atau di bawah ambang");

    private final String title;
    private final boolean perMasa;
    private final String bookBasis;
    /** Null: the return has no counterpart figure; the whole movement needs an explanation. */
    private final String taxBasis;

    SelfTestLine(String title, boolean perMasa, String bookBasis, String taxBasis) {
        this.title = title;
        this.perMasa = perMasa;
        this.bookBasis = bookBasis;
        this.taxBasis = taxBasis;
    }

    public String getTitle() {
        return title;
    }

    public boolean isPerMasa() {
        return perMasa;
    }

    public String getBookBasis() {
        return bookBasis;
    }

    public String getTaxBasis() {
        return taxBasis;
    }

    public boolean hasTaxFigure() {
        return taxBasis != null;
    }
}
