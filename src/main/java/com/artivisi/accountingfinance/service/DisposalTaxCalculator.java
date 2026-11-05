package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.DisposalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tax effect of a disposal, shown before and after the journal is created. Gain or loss is proceeds minus book
 * value; only a sale has proceeds. The fiscal treatment quoted in the notes is from memory, not checked against
 * current regulation, and the texts say so. The figures use the single book value the register holds; a fiscal
 * book value that differs from it is not tracked.
 */
@Component
public class DisposalTaxCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal ppnRatePercent;

    public DisposalTaxCalculator(@Value("${app.asset-disposal.ppn-rate-percent}") BigDecimal ppnRatePercent) {
        this.ppnRatePercent = ppnRatePercent;
    }

    /**
     * @param gainLoss            proceeds minus book value
     * @param taxableIncomeEffect change to taxable income after any fiscal correction; null when not determinable
     * @param fiscalCorrection    positive correction on the reconciliation; null when not determinable
     * @param estimatedPpn        indicative output VAT if the company is PKP; null when the base is unknown
     */
    public record DisposalTaxEffect(DisposalType type, BigDecimal proceeds, BigDecimal bookValue, BigDecimal gainLoss,
                                    BigDecimal taxableIncomeEffect, BigDecimal fiscalCorrection,
                                    BigDecimal estimatedPpn, String note) {
    }

    public DisposalTaxEffect calculate(DisposalType type, BigDecimal proceeds, BigDecimal bookValue) {
        if (type == null) {
            throw new IllegalArgumentException("Tipe pelepasan wajib diisi");
        }
        if (bookValue == null) {
            throw new IllegalArgumentException("Nilai buku wajib diisi");
        }
        BigDecimal loss = bookValue.negate();
        return switch (type) {
            case SOLD -> sale(proceeds, bookValue);
            case GIFTED -> {
                requireNoProceeds(type, proceeds);
                yield new DisposalTaxEffect(type, BigDecimal.ZERO, bookValue, loss, BigDecimal.ZERO, bookValue, null,
                        "Hibah tidak menghasilkan penerimaan. Seluruh nilai buku menjadi rugi dan tidak dapat dikurangkan, "
                                + "sehingga koreksi fiskal positif sebesar nilai buku (Pasal 9 ayat (1) huruf g UU PPh, belum diverifikasi). "
                                + "PPN atas hibah dihitung dari harga pasar wajar yang tidak tercatat di sini "
                                + "(Pasal 16D UU PPN, belum diverifikasi).");
            }
            case WRITTEN_OFF -> {
                requireNoProceeds(type, proceeds);
                yield new DisposalTaxEffect(type, BigDecimal.ZERO, bookValue, loss, loss, BigDecimal.ZERO, null,
                        "Nilai buku dibebankan sebagai kerugian (Pasal 11 ayat (7) UU PPh, belum diverifikasi).");
            }
            case TRANSFERRED -> {
                requireNoProceeds(type, proceeds);
                yield new DisposalTaxEffect(type, BigDecimal.ZERO, bookValue, loss, null, null, null,
                        "Perlakuan fiskal pemindahan bergantung pada hubungan para pihak dan dasar pengalihan. "
                                + "Aplikasi tidak menghitungnya; perlu ditinjau konsultan pajak.");
            }
        };
    }

    private DisposalTaxEffect sale(BigDecimal proceeds, BigDecimal bookValue) {
        if (proceeds == null || proceeds.signum() <= 0) {
            throw new IllegalArgumentException("Hasil penjualan wajib diisi dan lebih dari nol");
        }
        BigDecimal gainLoss = proceeds.subtract(bookValue);
        BigDecimal ppn = proceeds.multiply(ppnRatePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new DisposalTaxEffect(DisposalType.SOLD, proceeds, bookValue, gainLoss, gainLoss, BigDecimal.ZERO, ppn,
                "Selisih hasil penjualan dan nilai buku masuk penghasilan kena pajak (Pasal 4 ayat (1) huruf d UU PPh, belum diverifikasi). "
                        + "PPN indikatif berlaku jika perusahaan PKP dan PPN masukan aset pernah dikreditkan (Pasal 16D UU PPN, belum diverifikasi); "
                        + "kendaraan bermotor bekas punya aturan DPP tersendiri. Jurnal pelepasan tidak mencatat PPN.");
    }

    private void requireNoProceeds(DisposalType type, BigDecimal proceeds) {
        if (proceeds != null && proceeds.signum() != 0) {
            throw new IllegalArgumentException("Hasil penjualan hanya untuk tipe SOLD, bukan " + type);
        }
    }
}
