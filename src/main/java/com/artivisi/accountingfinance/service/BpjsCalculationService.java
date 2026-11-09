package com.artivisi.accountingfinance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Service for calculating BPJS contributions based on Indonesian regulations.
 *
 * BPJS Kesehatan:
 * - Company: 4% of salary (capped at ceiling)
 * - Employee: 1% of salary (capped at ceiling)
 * - Ceiling: Rp 12,000,000 per month
 *
 * BPJS Ketenagakerjaan:
 * - JKK: 0.24% - 1.74% of salary (company only, varies by risk class)
 * - JKM: 0.3% of salary (company only)
 * - JHT: 3.7% company + 2% employee (no ceiling)
 * - JP: 2% company + 1% employee, wage ceiling per {@link #jpWageCeiling(YearMonth)}
 */
@Service
@Slf4j
public class BpjsCalculationService {

    // BPJS Kesehatan ceiling (updated annually)
    public static final BigDecimal BPJS_KESEHATAN_CEILING = new BigDecimal("12000000");

    /**
     * JP wage ceiling (batas upah Jaminan Pensiun), PP 45/2015 Pasal 29: adjusted every March by the
     * previous year's GDP growth and announced by BPJS Ketenagakerjaan. Key: first payroll period in force.
     * <ul>
     *   <li>2024-03: Rp 10,042,300 (letter B/1387/022024)</li>
     *   <li>2025-03: Rp 10,547,400 (letter B/91/022025)</li>
     *   <li>2026-03: Rp 11,086,300 (letter B/1226/022026)</li>
     * </ul>
     * An announced ceiling stays in force until BPJS Ketenagakerjaan announces the next one, so a period
     * after the latest entry uses it. Twelve or more periods after the latest entry the next announcement
     * is overdue in this table and a warning is logged on every calculation until it is added.
     */
    /** Jaminan Pensiun started 1 July 2015 (PP 45/2015 Pasal 47); earlier periods carry no JP contribution. */
    public static final YearMonth JP_PROGRAM_START = YearMonth.of(2015, 7);

    private static final NavigableMap<YearMonth, BigDecimal> JP_WAGE_CEILINGS = new TreeMap<>(Map.of(
        YearMonth.of(2024, 3), new BigDecimal("10042300"),
        YearMonth.of(2025, 3), new BigDecimal("10547400"),
        YearMonth.of(2026, 3), new BigDecimal("11086300")
    ));

    // BPJS Kesehatan rates
    public static final BigDecimal BPJS_KESEHATAN_COMPANY_RATE = new BigDecimal("4");
    public static final BigDecimal BPJS_KESEHATAN_EMPLOYEE_RATE = new BigDecimal("1");

    // BPJS Ketenagakerjaan rates
    public static final BigDecimal BPJS_JKK_RATE_CLASS_1 = new BigDecimal("0.24");  // Very low risk
    public static final BigDecimal BPJS_JKK_RATE_CLASS_2 = new BigDecimal("0.54");  // Low risk
    public static final BigDecimal BPJS_JKK_RATE_CLASS_3 = new BigDecimal("0.89");  // Medium risk
    public static final BigDecimal BPJS_JKK_RATE_CLASS_4 = new BigDecimal("1.27");  // High risk
    public static final BigDecimal BPJS_JKK_RATE_CLASS_5 = new BigDecimal("1.74");  // Very high risk

    public static final BigDecimal BPJS_JKM_RATE = new BigDecimal("0.3");

    public static final BigDecimal BPJS_JHT_COMPANY_RATE = new BigDecimal("3.7");
    public static final BigDecimal BPJS_JHT_EMPLOYEE_RATE = new BigDecimal("2");

    public static final BigDecimal BPJS_JP_COMPANY_RATE = new BigDecimal("2");
    public static final BigDecimal BPJS_JP_EMPLOYEE_RATE = new BigDecimal("1");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Calculate all BPJS contributions for an employee.
     *
     * @param baseSalary The employee's base salary for BPJS calculation
     * @param jkkRiskClass JKK risk class (1-5), defaults to 1 if null
     * @param period Payroll period, selects the JP wage ceiling
     * @return BpjsCalculationResult containing all contributions
     */
    public BpjsCalculationResult calculate(BigDecimal baseSalary, Integer jkkRiskClass, YearMonth period) {
        if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BpjsCalculationResult.zero();
        }

        int riskClass = (jkkRiskClass != null && jkkRiskClass >= 1 && jkkRiskClass <= 5)
            ? jkkRiskClass : 1;

        // BPJS Kesehatan - apply ceiling
        BigDecimal kesehatanBase = baseSalary.min(BPJS_KESEHATAN_CEILING);
        BigDecimal kesehatanCompany = calculatePercentage(kesehatanBase, BPJS_KESEHATAN_COMPANY_RATE);
        BigDecimal kesehatanEmployee = calculatePercentage(kesehatanBase, BPJS_KESEHATAN_EMPLOYEE_RATE);

        // JKK - company only, no ceiling
        BigDecimal jkkRate = getJkkRate(riskClass);
        BigDecimal jkk = calculatePercentage(baseSalary, jkkRate);

        // JKM - company only, no ceiling
        BigDecimal jkm = calculatePercentage(baseSalary, BPJS_JKM_RATE);

        // JHT - no ceiling
        BigDecimal jhtCompany = calculatePercentage(baseSalary, BPJS_JHT_COMPANY_RATE);
        BigDecimal jhtEmployee = calculatePercentage(baseSalary, BPJS_JHT_EMPLOYEE_RATE);

        // JP - apply ceiling; none before the program started
        BigDecimal jpCompany = BigDecimal.ZERO;
        BigDecimal jpEmployee = BigDecimal.ZERO;
        if (period == null) {
            throw new IllegalArgumentException("Periode payroll wajib diisi untuk batas upah JP");
        }
        if (!period.isBefore(JP_PROGRAM_START)) {
            BigDecimal jpBase = baseSalary.min(jpWageCeiling(period));
            jpCompany = calculatePercentage(jpBase, BPJS_JP_COMPANY_RATE);
            jpEmployee = calculatePercentage(jpBase, BPJS_JP_EMPLOYEE_RATE);
        }

        return new BpjsCalculationResult(
            kesehatanCompany, kesehatanEmployee,
            jkk, jkm,
            jhtCompany, jhtEmployee,
            jpCompany, jpEmployee
        );
    }

    /**
     * JP wage ceiling in force for a payroll period: the latest announcement effective on or before it.
     *
     * @throws IllegalStateException when the period precedes the first announcement in the table
     */
    public static BigDecimal jpWageCeiling(YearMonth period) {
        if (period == null) {
            throw new IllegalArgumentException("Periode payroll wajib diisi untuk batas upah JP");
        }
        Map.Entry<YearMonth, BigDecimal> entry = JP_WAGE_CEILINGS.floorEntry(period);
        if (entry == null) {
            throw new IllegalStateException("Batas upah Jaminan Pensiun untuk periode " + period
                + " belum dimasukkan. Tabel dimulai " + JP_WAGE_CEILINGS.firstKey()
                + "; tambahkan pengumuman BPJS Ketenagakerjaan tahun tersebut di BpjsCalculationService.");
        }
        if (!period.isBefore(entry.getKey().plusMonths(12))) {
            log.warn("Batas upah Jaminan Pensiun periode {} memakai pengumuman {} (Rp {}). Pengumuman berikutnya "
                + "belum dimasukkan ke BpjsCalculationService.", period, entry.getKey(), entry.getValue());
        }
        return entry.getValue();
    }

    /** First period of the JP wage ceiling announcement in force for a payroll period. */
    public static YearMonth jpWageCeilingEffectiveFrom(YearMonth period) {
        jpWageCeiling(period);
        return JP_WAGE_CEILINGS.floorKey(period);
    }

    private BigDecimal getJkkRate(int riskClass) {
        return switch (riskClass) {
            case 1 -> BPJS_JKK_RATE_CLASS_1;
            case 2 -> BPJS_JKK_RATE_CLASS_2;
            case 3 -> BPJS_JKK_RATE_CLASS_3;
            case 4 -> BPJS_JKK_RATE_CLASS_4;
            case 5 -> BPJS_JKK_RATE_CLASS_5;
            default -> BPJS_JKK_RATE_CLASS_1;
        };
    }

    private BigDecimal calculatePercentage(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage)
            .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    /**
     * Result record containing all BPJS contribution amounts.
     */
    public record BpjsCalculationResult(
        BigDecimal kesehatanCompany,
        BigDecimal kesehatanEmployee,
        BigDecimal jkk,
        BigDecimal jkm,
        BigDecimal jhtCompany,
        BigDecimal jhtEmployee,
        BigDecimal jpCompany,
        BigDecimal jpEmployee
    ) {
        public static BpjsCalculationResult zero() {
            return new BpjsCalculationResult(
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO
            );
        }

        /**
         * Total company contribution (all BPJS programs).
         */
        public BigDecimal totalCompany() {
            return kesehatanCompany.add(jkk).add(jkm).add(jhtCompany).add(jpCompany);
        }

        /**
         * Total employee contribution (deducted from salary).
         */
        public BigDecimal totalEmployee() {
            return kesehatanEmployee.add(jhtEmployee).add(jpEmployee);
        }

        /**
         * Grand total of all BPJS contributions.
         */
        public BigDecimal grandTotal() {
            return totalCompany().add(totalEmployee());
        }
    }
}
