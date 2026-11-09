package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.TerCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service for calculating PPh 21 (Employee Income Tax) based on Indonesian regulations.
 *
 * Two calculation methods:
 *
 * 1. TER method (PMK 168/2023) — used for monthly withholding Jan-Nov:
 *    PPh 21 = gross salary × TER rate (looked up by PTKP category and gross bracket)
 *
 * 2. Pasal 17 ayat (1) huruf a UU PPh brackets (UU HPP) — used for December annual reconciliation:
 *    Annual tax calculated via progressive brackets, minus Jan-Nov TER withholdings
 *
 * Progressive Tax Rates:
 * - 0 - 60,000,000: 5%
 * - 60,000,001 - 250,000,000: 15%
 * - 250,000,001 - 500,000,000: 25%
 * - 500,000,001 - 5,000,000,000: 30%
 * - > 5,000,000,000: 35%
 */
@Service
public class Pph21CalculationService {

    // Tax bracket thresholds (annual)
    public static final BigDecimal BRACKET_1_LIMIT = new BigDecimal("60000000");
    public static final BigDecimal BRACKET_2_LIMIT = new BigDecimal("250000000");
    public static final BigDecimal BRACKET_3_LIMIT = new BigDecimal("500000000");
    public static final BigDecimal BRACKET_4_LIMIT = new BigDecimal("5000000000");

    // Tax rates
    public static final BigDecimal RATE_BRACKET_1 = new BigDecimal("5");    // 5%
    public static final BigDecimal RATE_BRACKET_2 = new BigDecimal("15");   // 15%
    public static final BigDecimal RATE_BRACKET_3 = new BigDecimal("25");   // 25%
    public static final BigDecimal RATE_BRACKET_4 = new BigDecimal("30");   // 30%
    public static final BigDecimal RATE_BRACKET_5 = new BigDecimal("35");   // 35%

    // Biaya jabatan
    public static final BigDecimal BIAYA_JABATAN_RATE = new BigDecimal("5");  // 5%
    public static final BigDecimal BIAYA_JABATAN_ANNUAL_MAX = new BigDecimal("6000000");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Calculate monthly PPh 21 using TER method (PMK 168/2023).
     * Used for Jan-Nov monthly withholding.
     *
     * @param monthlyGrossIncome Monthly gross income
     * @param ptkpStatus         PTKP status (determines TER category)
     * @return TER calculation result with rate and PPh 21 amount
     */
    public TerCalculationResult calculateTer(BigDecimal monthlyGrossIncome, PtkpStatus ptkpStatus) {
        if (ptkpStatus == null) {
            throw new IllegalArgumentException("Status PTKP wajib diisi untuk menghitung TER");
        }
        if (monthlyGrossIncome == null || monthlyGrossIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return new TerCalculationResult(BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        TerCategory category = TerCategory.fromPtkpStatus(ptkpStatus);
        BigDecimal terRate = category.lookupRate(monthlyGrossIncome);
        BigDecimal pph21 = monthlyGrossIncome.multiply(terRate)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);

        return new TerCalculationResult(monthlyGrossIncome, category, terRate, pph21);
    }

    /**
     * Calculate December PPh 21 using annual reconciliation.
     * Annual tax (progressive brackets) minus sum of Jan-Nov TER withholdings.
     *
     * @param monthlyGrossAmounts  List of monthly gross amounts for the year (Jan-Dec)
     * @param pensionContributions Employee-paid JHT and JP contributions of the year
     * @param ptkpStatus           PTKP status
     * @param janNovPph21Total     Sum of PPh 21 already withheld Jan-Nov via TER
     * @return December reconciliation result
     */
    public DecemberReconciliationResult calculateDecemberReconciliation(
            List<BigDecimal> monthlyGrossAmounts, BigDecimal pensionContributions,
            PtkpStatus ptkpStatus, BigDecimal janNovPph21Total) {
        BigDecimal annualGross = monthlyGrossAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        AnnualTax annual = calculateAnnualTax(annualGross, pensionContributions, ptkpStatus);
        BigDecimal decemberPph21 = annual.annualTax().subtract(janNovPph21Total);

        return new DecemberReconciliationResult(
                annualGross, annual.biayaJabatan(), annual.pensionContributions(), annual.annualNeto(),
                annual.ptkpAmount(), annual.pkp(), annual.annualTax(), janNovPph21Total, decemberPph21);
    }

    /**
     * Annual PPh 21 of a permanent employee (PMK 168/2023, form 1721-A1 lines 9-17): neto is gross minus
     * biaya jabatan (5%, max 6,000,000) and the employee-paid pension contributions (JHT and JP). The
     * employee's BPJS Kesehatan contribution is not a deduction. PKP is rounded down to the thousand.
     */
    public AnnualTax calculateAnnualTax(BigDecimal annualGross, BigDecimal pensionContributions,
                                        PtkpStatus ptkpStatus) {
        if (ptkpStatus == null) {
            throw new IllegalArgumentException("Status PTKP wajib diisi untuk menghitung PPh 21 tahunan");
        }
        if (annualGross == null || pensionContributions == null) {
            throw new IllegalArgumentException("Penghasilan bruto dan iuran pensiun wajib diisi");
        }
        BigDecimal biayaJabatan = annualGross.multiply(BIAYA_JABATAN_RATE)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .min(BIAYA_JABATAN_ANNUAL_MAX);
        BigDecimal annualNeto = annualGross.subtract(biayaJabatan).subtract(pensionContributions);
        BigDecimal ptkpAmount = ptkpStatus.getAnnualAmount();
        BigDecimal pkpRaw = annualNeto.subtract(ptkpAmount).max(BigDecimal.ZERO);
        BigDecimal pkp = pkpRaw.divide(new BigDecimal("1000"), 0, RoundingMode.FLOOR)
                .multiply(new BigDecimal("1000"));
        return new AnnualTax(annualGross, biayaJabatan, pensionContributions, annualNeto, ptkpAmount, pkp,
                calculateProgressiveTax(pkp));
    }

    /**
     * Calculate progressive tax based on PKP brackets.
     */
    public BigDecimal calculateProgressiveTax(BigDecimal pkp) {
        if (pkp.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal remaining = pkp;

        // Bracket 1: 0 - 60,000,000 @ 5%
        BigDecimal bracket1Amount = remaining.min(BRACKET_1_LIMIT);
        tax = tax.add(calculatePercentage(bracket1Amount, RATE_BRACKET_1));
        remaining = remaining.subtract(BRACKET_1_LIMIT);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return tax;
        }

        // Bracket 2: 60,000,001 - 250,000,000 @ 15%
        BigDecimal bracket2Size = BRACKET_2_LIMIT.subtract(BRACKET_1_LIMIT);
        BigDecimal bracket2Amount = remaining.min(bracket2Size);
        tax = tax.add(calculatePercentage(bracket2Amount, RATE_BRACKET_2));
        remaining = remaining.subtract(bracket2Size);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return tax;
        }

        // Bracket 3: 250,000,001 - 500,000,000 @ 25%
        BigDecimal bracket3Size = BRACKET_3_LIMIT.subtract(BRACKET_2_LIMIT);
        BigDecimal bracket3Amount = remaining.min(bracket3Size);
        tax = tax.add(calculatePercentage(bracket3Amount, RATE_BRACKET_3));
        remaining = remaining.subtract(bracket3Size);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return tax;
        }

        // Bracket 4: 500,000,001 - 5,000,000,000 @ 30%
        BigDecimal bracket4Size = BRACKET_4_LIMIT.subtract(BRACKET_3_LIMIT);
        BigDecimal bracket4Amount = remaining.min(bracket4Size);
        tax = tax.add(calculatePercentage(bracket4Amount, RATE_BRACKET_4));
        remaining = remaining.subtract(bracket4Size);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return tax;
        }

        // Bracket 5: > 5,000,000,000 @ 35%
        tax = tax.add(calculatePercentage(remaining, RATE_BRACKET_5));

        return tax;
    }

    private BigDecimal calculatePercentage(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage)
            .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    /**
     * Result record for TER monthly calculation.
     */
    public record TerCalculationResult(
        BigDecimal monthlyGrossIncome,
        TerCategory terCategory,
        BigDecimal terRate,
        BigDecimal monthlyPph21
    ) {}

    /**
     * Result record for the annual PPh 21 computation.
     */
    public record AnnualTax(
        BigDecimal annualGross,
        BigDecimal biayaJabatan,
        BigDecimal pensionContributions,
        BigDecimal annualNeto,
        BigDecimal ptkpAmount,
        BigDecimal pkp,
        BigDecimal annualTax
    ) {}

    /**
     * Result record for December annual reconciliation.
     */
    public record DecemberReconciliationResult(
        BigDecimal annualGross,
        BigDecimal biayaJabatan,
        BigDecimal pensionContributions,
        BigDecimal annualNeto,
        BigDecimal ptkpAmount,
        BigDecimal pkp,
        BigDecimal annualTax,
        BigDecimal janNovPph21Total,
        BigDecimal decemberPph21
    ) {}
}
