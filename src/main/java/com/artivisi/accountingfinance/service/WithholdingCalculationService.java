package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.config.WithholdingProperties;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.WithholdingRateBasis;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Tax math only; no persistence. Rates live in {@link WithholdingProperties}; Pasal 17 brackets live in
 * {@link Pph21CalculationService}.
 */
@Service
@RequiredArgsConstructor
public class WithholdingCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final WithholdingProperties properties;
    private final Pph21CalculationService pph21CalculationService;

    public record Withholding(BigDecimal dpp, BigDecimal rate, BigDecimal tax, WithholdingRateBasis basis) {}

    /**
     * PPh 21 bukan pegawai (PMK 168/2023): Pasal 17 ayat (1) huruf a rates on 50% of the gross of each payment,
     * 20% higher without NPWP or NIK. Each payment stands alone: no accumulation within the masa or the year
     * (the cumulative rule for recurring payees was removed by PMK 168/2023) and no PTKP.
     */
    public Withholding pph21NonEmployee(BigDecimal gross, boolean hasTaxId) {
        BigDecimal dpp = percent(gross, properties.getPph21DppPercent(), 2);
        BigDecimal base = pph21CalculationService.calculateProgressiveTax(dpp);
        BigDecimal tax = hasTaxId ? base.setScale(2, RoundingMode.HALF_UP)
                : surcharge(base, properties.getPph21NoIdSurchargePercent());
        return new Withholding(dpp, effectiveRate(tax, dpp), tax,
                hasTaxId ? WithholdingRateBasis.PPH21_PASAL_17 : WithholdingRateBasis.PPH21_PASAL_17_NO_ID);
    }

    public Withholding pph23(BigDecimal gross, boolean hasTaxId) {
        BigDecimal rate = hasTaxId ? properties.getPph23RatePercent()
                : properties.getPph23RatePercent()
                        .multiply(HUNDRED.add(properties.getPph23NoIdSurchargePercent()))
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Withholding(gross, rate.setScale(2, RoundingMode.HALF_UP), percent(gross, rate, 0).setScale(2),
                hasTaxId ? WithholdingRateBasis.PPH23_STANDARD : WithholdingRateBasis.PPH23_NO_ID);
    }

    /**
     * PPh 26 at the general rate, or at the payee's treaty rate when a DGT form is on file and valid on the
     * payment date. A treaty rate without a valid form falls to the general rate and the basis records why.
     */
    public Withholding pph26(BigDecimal gross, Vendor payee, LocalDate paymentDate) {
        BigDecimal rate = properties.getPph26RatePercent();
        WithholdingRateBasis basis = WithholdingRateBasis.PPH26_STANDARD;
        if (payee.getTreatyRate() != null) {
            if (hasValidDgt(payee, paymentDate)) {
                rate = payee.getTreatyRate();
                basis = WithholdingRateBasis.PPH26_TREATY;
            } else {
                basis = WithholdingRateBasis.PPH26_TREATY_DGT_INVALID;
            }
        }
        return new Withholding(gross, rate.setScale(2, RoundingMode.HALF_UP), percent(gross, rate, 0).setScale(2), basis);
    }

    /** Dispatch by withholding treatment. */
    public Withholding calculate(WithholdingTreatment treatment, BigDecimal gross,
                                 boolean hasTaxId, Vendor payee, LocalDate paymentDate) {
        return switch (treatment) {
            case PPH_21_BUKAN_PEGAWAI -> pph21NonEmployee(gross, hasTaxId);
            case PPH_23 -> pph23(gross, hasTaxId);
            case PPH_26 -> pph26(gross, payee, paymentDate);
        };
    }

    public boolean hasValidDgt(Vendor payee, LocalDate date) {
        return payee.getDgtFormNumber() != null && !payee.getDgtFormNumber().isBlank()
                && payee.getDgtValidUntil() != null && !payee.getDgtValidUntil().isBefore(date);
    }

    private BigDecimal surcharge(BigDecimal tax, BigDecimal surchargePercent) {
        return tax.multiply(HUNDRED.add(surchargePercent)).divide(HUNDRED, 0, RoundingMode.HALF_UP).setScale(2);
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal percent, int scale) {
        return amount.multiply(percent).divide(HUNDRED, scale, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveRate(BigDecimal tax, BigDecimal dpp) {
        return tax.multiply(HUNDRED).divide(dpp, 2, RoundingMode.HALF_UP);
    }
}
