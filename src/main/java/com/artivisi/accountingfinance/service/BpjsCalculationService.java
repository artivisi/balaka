package com.artivisi.accountingfinance.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
 * - JP: 2% company + 1% employee (ceiling Rp 10,042,300 for 2025)
 */
@Service
public class BpjsCalculationService {

    // BPJS Kesehatan ceiling (updated annually)
    public static final BigDecimal BPJS_KESEHATAN_CEILING = new BigDecimal("12000000");

    // BPJS JP ceiling (updated annually, 2025 value)
    public static final BigDecimal BPJS_JP_CEILING = new BigDecimal("10042300");

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
     * @return BpjsCalculationResult containing all contributions
     */
    public BpjsCalculationResult calculate(BigDecimal baseSalary, Integer jkkRiskClass) {
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

        // JP - apply ceiling
        BigDecimal jpBase = baseSalary.min(BPJS_JP_CEILING);
        BigDecimal jpCompany = calculatePercentage(jpBase, BPJS_JP_COMPANY_RATE);
        BigDecimal jpEmployee = calculatePercentage(jpBase, BPJS_JP_EMPLOYEE_RATE);

        return new BpjsCalculationResult(
            kesehatanCompany, kesehatanEmployee,
            jkk, jkm,
            jhtCompany, jhtEmployee,
            jpCompany, jpEmployee
        );
    }

    /**
     * Calculate BPJS with default JKK risk class 1 (IT services, very low risk).
     */
    public BpjsCalculationResult calculate(BigDecimal baseSalary) {
        return calculate(baseSalary, 1);
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
