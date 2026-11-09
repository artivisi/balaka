package com.artivisi.accountingfinance.enums;

/** Reconciling lines from booked revenue to the DPP of SPT Masa PPN. Each amount is added to revenue. */
public enum SalesTaxReconLine {
    PPN_IN_REVENUE("PPN ikut terbukukan sebagai pendapatan"),
    PPH23_NETTED_FROM_REVENUE("PPh 23 mengurangi pendapatan"),
    REVENUE_NO_FAKTUR("Pendapatan tanpa faktur"),
    FAKTUR_IN_OTHER_MASA("Pendapatan masa ini, faktur di masa lain"),
    REVENUE_IN_OTHER_MASA("Faktur masa ini, pendapatan di masa lain"),
    FAKTUR_NO_REVENUE("Faktur tanpa pendapatan terposting"),
    DPP_NILAI_LAIN("Selisih DPP Nilai Lain (kode 04)"),
    OTHER_DIFFERENCE("Selisih lain antara pendapatan dan DPP");

    private final String indonesianName;

    SalesTaxReconLine(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
