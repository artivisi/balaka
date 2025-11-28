package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for calculating PPh 21 (Employee Income Tax) based on Indonesian regulations.
 *
 * PPh 21 Progressive Tax Rates (PP 58/2023, effective 2024):
 * - 0 - 60,000,000: 5%
 * - 60,000,001 - 250,000,000: 15%
 * - 250,000,001 - 500,000,000: 25%
 * - 500,000,001 - 5,000,000,000: 30%
 * - > 5,000,000,000: 35%
 *
 * Calculation steps:
 * 1. Gross income (gaji bruto)
 * 2. Subtract biaya jabatan (5%, max Rp 500,000/month)
 * 3. Subtract BPJS deductions (JHT + JP employee portion)
 * 4. Calculate annual neto
 * 5. Subtract PTKP
 * 6. Apply progressive tax rates
 * 7. Divide by 12 for monthly PPh 21
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
    public static final BigDecimal BIAYA_JABATAN_MONTHLY_MAX = new BigDecimal("500000");
    public static final BigDecimal BIAYA_JABATAN_ANNUAL_MAX = new BigDecimal("6000000");

    // BPJS deductible rates (employee portion only)
    public static final BigDecimal BPJS_JHT_EMPLOYEE_RATE = new BigDecimal("2");  // 2%
    public static final BigDecimal BPJS_JP_EMPLOYEE_RATE = new BigDecimal("1");   // 1%
    public static final BigDecimal BPJS_JP_CEILING = new BigDecimal("10042300");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    /**
     * Calculate monthly PPh 21 for an employee.
     *
     * @param monthlyGrossIncome Monthly gross income (gaji bruto)
     * @param ptkpStatus         PTKP status for tax deduction
     * @param hasNpwp            Whether employee has NPWP (no NPWP = 20% higher tax)
     * @return Pph21CalculationResult with all calculation details
     */
    public Pph21CalculationResult calculate(BigDecimal monthlyGrossIncome, PtkpStatus ptkpStatus, boolean hasNpwp) {
        if (monthlyGrossIncome == null || monthlyGrossIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return Pph21CalculationResult.zero();
        }

        if (ptkpStatus == null) {
            ptkpStatus = PtkpStatus.TK_0;
        }

        // Step 1: Calculate biaya jabatan (5%, max 500k/month)
        BigDecimal biayaJabatan = calculateBiayaJabatan(monthlyGrossIncome);

        // Step 2: Calculate BPJS deductions
        BigDecimal bpjsJhtEmployee = calculatePercentage(monthlyGrossIncome, BPJS_JHT_EMPLOYEE_RATE);
        BigDecimal jpBase = monthlyGrossIncome.min(BPJS_JP_CEILING);
        BigDecimal bpjsJpEmployee = calculatePercentage(jpBase, BPJS_JP_EMPLOYEE_RATE);
        BigDecimal totalBpjsDeduction = bpjsJhtEmployee.add(bpjsJpEmployee);

        // Step 3: Calculate monthly neto
        BigDecimal monthlyNeto = monthlyGrossIncome
            .subtract(biayaJabatan)
            .subtract(totalBpjsDeduction);

        if (monthlyNeto.compareTo(BigDecimal.ZERO) < 0) {
            monthlyNeto = BigDecimal.ZERO;
        }

        // Step 4: Calculate annual neto
        BigDecimal annualNeto = monthlyNeto.multiply(TWELVE);

        // Step 5: Calculate PKP (Penghasilan Kena Pajak)
        BigDecimal ptkpAmount = ptkpStatus.getAnnualAmount();
        BigDecimal pkp = annualNeto.subtract(ptkpAmount);

        if (pkp.compareTo(BigDecimal.ZERO) <= 0) {
            // No tax if PKP is zero or negative
            return new Pph21CalculationResult(
                monthlyGrossIncome,
                biayaJabatan,
                totalBpjsDeduction,
                monthlyNeto,
                annualNeto,
                ptkpAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                hasNpwp
            );
        }

        // Step 6: Apply progressive tax rates
        BigDecimal annualPph21 = calculateProgressiveTax(pkp);

        // Step 7: Apply 20% higher rate if no NPWP
        if (!hasNpwp) {
            BigDecimal penalty = annualPph21.multiply(new BigDecimal("20")).divide(HUNDRED, 0, RoundingMode.HALF_UP);
            annualPph21 = annualPph21.add(penalty);
        }

        // Step 8: Calculate monthly PPh 21
        BigDecimal monthlyPph21 = annualPph21.divide(TWELVE, 0, RoundingMode.HALF_UP);

        return new Pph21CalculationResult(
            monthlyGrossIncome,
            biayaJabatan,
            totalBpjsDeduction,
            monthlyNeto,
            annualNeto,
            ptkpAmount,
            pkp,
            annualPph21,
            monthlyPph21,
            hasNpwp
        );
    }

    /**
     * Calculate with default hasNpwp = true.
     */
    public Pph21CalculationResult calculate(BigDecimal monthlyGrossIncome, PtkpStatus ptkpStatus) {
        return calculate(monthlyGrossIncome, ptkpStatus, true);
    }

    /**
     * Calculate biaya jabatan (occupational expense).
     * 5% of gross income, max Rp 500,000/month.
     */
    public BigDecimal calculateBiayaJabatan(BigDecimal monthlyGrossIncome) {
        BigDecimal biayaJabatan = calculatePercentage(monthlyGrossIncome, BIAYA_JABATAN_RATE);
        return biayaJabatan.min(BIAYA_JABATAN_MONTHLY_MAX);
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
     * Result record containing all PPh 21 calculation details.
     */
    public record Pph21CalculationResult(
        BigDecimal monthlyGrossIncome,
        BigDecimal biayaJabatan,
        BigDecimal bpjsDeduction,
        BigDecimal monthlyNeto,
        BigDecimal annualNeto,
        BigDecimal ptkpAmount,
        BigDecimal pkp,
        BigDecimal annualPph21,
        BigDecimal monthlyPph21,
        boolean hasNpwp
    ) {
        public static Pph21CalculationResult zero() {
            return new Pph21CalculationResult(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true
            );
        }

        /**
         * Get the effective tax rate (annual PPh 21 / annual gross).
         */
        public BigDecimal effectiveTaxRate() {
            if (monthlyGrossIncome.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal annualGross = monthlyGrossIncome.multiply(new BigDecimal("12"));
            return annualPph21.multiply(new BigDecimal("100"))
                .divide(annualGross, 2, RoundingMode.HALF_UP);
        }

        /**
         * Get take home pay (gross - PPh 21 - BPJS deduction).
         */
        public BigDecimal takeHomePay() {
            return monthlyGrossIncome.subtract(monthlyPph21).subtract(bpjsDeduction);
        }
    }
}
