package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PPh 21 Calculation Service")
class Pph21CalculationServiceTest {

    private Pph21CalculationService service;

    @BeforeEach
    void setUp() {
        service = new Pph21CalculationService();
    }

    @Nested
    @DisplayName("Biaya Jabatan Calculation")
    class BiayaJabatanTests {

        @Test
        @DisplayName("Should calculate 5% of gross income")
        void shouldCalculateFivePercentOfGross() {
            BigDecimal gross = new BigDecimal("5000000");
            BigDecimal biayaJabatan = service.calculateBiayaJabatan(gross);

            // 5% of 5,000,000 = 250,000
            assertThat(biayaJabatan).isEqualByComparingTo("250000");
        }

        @Test
        @DisplayName("Should cap at Rp 500,000 per month")
        void shouldCapAtMaximum() {
            BigDecimal gross = new BigDecimal("15000000");
            BigDecimal biayaJabatan = service.calculateBiayaJabatan(gross);

            // 5% of 15,000,000 = 750,000, but capped at 500,000
            assertThat(biayaJabatan).isEqualByComparingTo("500000");
        }

        @Test
        @DisplayName("Should handle salary exactly at cap threshold")
        void shouldHandleSalaryAtCapThreshold() {
            BigDecimal gross = new BigDecimal("10000000");
            BigDecimal biayaJabatan = service.calculateBiayaJabatan(gross);

            // 5% of 10,000,000 = 500,000 (exactly at cap)
            assertThat(biayaJabatan).isEqualByComparingTo("500000");
        }
    }

    @Nested
    @DisplayName("Progressive Tax Calculation")
    class ProgressiveTaxTests {

        @Test
        @DisplayName("Should apply 5% for PKP up to 60 million")
        void shouldApplyFivePercentForFirstBracket() {
            BigDecimal pkp = new BigDecimal("60000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60,000,000 × 5% = 3,000,000
            assertThat(tax).isEqualByComparingTo("3000000");
        }

        @Test
        @DisplayName("Should apply progressive rates for PKP crossing brackets")
        void shouldApplyProgressiveRates() {
            BigDecimal pkp = new BigDecimal("100000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60,000,000 × 5% = 3,000,000
            // 40,000,000 × 15% = 6,000,000
            // Total = 9,000,000
            assertThat(tax).isEqualByComparingTo("9000000");
        }

        @Test
        @DisplayName("Should apply all brackets for high PKP")
        void shouldApplyAllBracketsForHighPkp() {
            BigDecimal pkp = new BigDecimal("600000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60,000,000 × 5% = 3,000,000
            // 190,000,000 × 15% = 28,500,000
            // 250,000,000 × 25% = 62,500,000
            // 100,000,000 × 30% = 30,000,000
            // Total = 124,000,000
            assertThat(tax).isEqualByComparingTo("124000000");
        }

        @Test
        @DisplayName("Should apply 35% for PKP over 5 billion")
        void shouldApplyHighestBracket() {
            BigDecimal pkp = new BigDecimal("6000000000"); // 6 billion
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // Bracket 1: 60M × 5% = 3M
            // Bracket 2: 190M × 15% = 28.5M
            // Bracket 3: 250M × 25% = 62.5M
            // Bracket 4: 4.5B × 30% = 1.35B
            // Bracket 5: 1B × 35% = 350M
            // Total = 1,794,000,000
            assertThat(tax).isEqualByComparingTo("1794000000");
        }

        @Test
        @DisplayName("Should return zero for zero PKP")
        void shouldReturnZeroForZeroPkp() {
            BigDecimal tax = service.calculateProgressiveTax(BigDecimal.ZERO);
            assertThat(tax).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should return zero for negative PKP")
        void shouldReturnZeroForNegativePkp() {
            BigDecimal tax = service.calculateProgressiveTax(new BigDecimal("-1000000"));
            assertThat(tax).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Full PPh 21 Calculation")
    class FullCalculationTests {

        @Test
        @DisplayName("Should calculate PPh 21 for typical salary - TK/0")
        void shouldCalculateForTypicalSalaryTk0() {
            BigDecimal gross = new BigDecimal("10000000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // Gross: 10,000,000
            // Biaya Jabatan: 500,000 (5%, capped)
            // BPJS JHT: 200,000 (2%)
            // BPJS JP: 100,423 (1% of ceiling 10,042,300)
            // Monthly Neto: 10,000,000 - 500,000 - 200,000 - 100,423 = 9,199,577
            // Annual Neto: 9,199,577 × 12 = 110,394,924
            // PKP: 110,394,924 - 54,000,000 = 56,394,924
            // Annual Tax: 56,394,924 × 5% = 2,819,746
            // Monthly Tax: 2,819,746 / 12 = 234,979

            assertThat(result.monthlyGrossIncome()).isEqualByComparingTo("10000000");
            assertThat(result.biayaJabatan()).isEqualByComparingTo("500000");
            assertThat(result.ptkpAmount()).isEqualByComparingTo("54000000");
            assertThat(result.pkp()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.monthlyPph21()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should calculate zero tax for low salary")
        void shouldCalculateZeroTaxForLowSalary() {
            BigDecimal gross = new BigDecimal("4500000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // Gross: 4,500,000
            // Biaya Jabatan: 225,000 (5%)
            // BPJS: ~135,000
            // Monthly Neto: ~4,140,000
            // Annual Neto: ~49,680,000
            // PKP: 49,680,000 - 54,000,000 = negative
            // No tax

            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
            assertThat(result.annualPph21()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should calculate for different PTKP status - K/2")
        void shouldCalculateForDifferentPtkpStatus() {
            BigDecimal gross = new BigDecimal("15000000");
            var result = service.calculate(gross, PtkpStatus.K_2);

            // PTKP K/2 = 67,500,000
            assertThat(result.ptkpAmount()).isEqualByComparingTo("67500000");
            assertThat(result.pkp()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should calculate for high salary crossing tax brackets")
        void shouldCalculateForHighSalaryCrossingBrackets() {
            BigDecimal gross = new BigDecimal("50000000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // This salary should cross into the 15% bracket
            // and result in significant monthly tax
            assertThat(result.monthlyPph21()).isGreaterThan(new BigDecimal("1000000"));
        }

        @Test
        @DisplayName("Should return zero for null salary")
        void shouldReturnZeroForNullSalary() {
            var result = service.calculate(null, PtkpStatus.TK_0);

            assertThat(result.monthlyGrossIncome()).isEqualByComparingTo("0");
            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should return zero for zero salary")
        void shouldReturnZeroForZeroSalary() {
            var result = service.calculate(BigDecimal.ZERO, PtkpStatus.TK_0);

            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should default to TK/0 for null PTKP status")
        void shouldDefaultToTk0ForNullPtkp() {
            BigDecimal gross = new BigDecimal("10000000");
            var result = service.calculate(gross, null);

            assertThat(result.ptkpAmount()).isEqualByComparingTo("54000000");
        }
    }

    @Nested
    @DisplayName("No NPWP Penalty")
    class NoNpwpPenaltyTests {

        @Test
        @DisplayName("Should add 20% penalty for no NPWP")
        void shouldAddTwentyPercentPenalty() {
            BigDecimal gross = new BigDecimal("15000000");

            var resultWithNpwp = service.calculate(gross, PtkpStatus.TK_0, true);
            var resultWithoutNpwp = service.calculate(gross, PtkpStatus.TK_0, false);

            // Without NPWP should be 20% higher
            BigDecimal expectedPenalty = resultWithNpwp.annualPph21()
                .multiply(new BigDecimal("0.2"));
            BigDecimal expectedWithPenalty = resultWithNpwp.annualPph21()
                .add(expectedPenalty);

            assertThat(resultWithoutNpwp.annualPph21()).isCloseTo(
                expectedWithPenalty,
                org.assertj.core.api.Assertions.within(new BigDecimal("1"))
            );
            assertThat(resultWithoutNpwp.hasNpwp()).isFalse();
        }

        @Test
        @DisplayName("Should not add penalty when has NPWP")
        void shouldNotAddPenaltyWithNpwp() {
            BigDecimal gross = new BigDecimal("15000000");
            var result = service.calculate(gross, PtkpStatus.TK_0, true);

            assertThat(result.hasNpwp()).isTrue();
        }
    }

    @Nested
    @DisplayName("Result Record Methods")
    class ResultRecordTests {

        @Test
        @DisplayName("Should calculate effective tax rate")
        void shouldCalculateEffectiveTaxRate() {
            BigDecimal gross = new BigDecimal("20000000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            BigDecimal effectiveRate = result.effectiveTaxRate();

            // Effective rate should be between 0 and 35%
            assertThat(effectiveRate).isGreaterThan(BigDecimal.ZERO);
            assertThat(effectiveRate).isLessThan(new BigDecimal("35"));
        }

        @Test
        @DisplayName("Should calculate take home pay")
        void shouldCalculateTakeHomePay() {
            BigDecimal gross = new BigDecimal("15000000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            BigDecimal thp = result.takeHomePay();

            // THP = Gross - PPh 21 - BPJS
            BigDecimal expected = gross.subtract(result.monthlyPph21())
                .subtract(result.bpjsDeduction());
            assertThat(thp).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Should return zero effective rate for zero income")
        void shouldReturnZeroEffectiveRateForZeroIncome() {
            var result = Pph21CalculationService.Pph21CalculationResult.zero();
            assertThat(result.effectiveTaxRate()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("BPJS Deduction in PPh 21")
    class BpjsDeductionTests {

        @Test
        @DisplayName("Should deduct BPJS JHT and JP employee portions")
        void shouldDeductBpjsPortions() {
            BigDecimal gross = new BigDecimal("10000000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // JHT: 2% of 10,000,000 = 200,000
            // JP: 1% of 10,000,000 = 100,000 (below ceiling)
            // Total: 300,000
            assertThat(result.bpjsDeduction()).isEqualByComparingTo("300000");
        }

        @Test
        @DisplayName("Should apply JP ceiling for high salary")
        void shouldApplyJpCeilingForHighSalary() {
            BigDecimal gross = new BigDecimal("15000000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // JHT: 2% of 15,000,000 = 300,000
            // JP: 1% of 10,042,300 (ceiling) = 100,423
            // Total: 400,423
            assertThat(result.bpjsDeduction()).isEqualByComparingTo("400423");
        }
    }

    @Nested
    @DisplayName("PTKP Status Coverage")
    class PtkpStatusCoverageTests {

        @Test
        @DisplayName("Should calculate for all PTKP statuses")
        void shouldCalculateForAllPtkpStatuses() {
            BigDecimal gross = new BigDecimal("20000000");

            for (PtkpStatus status : PtkpStatus.values()) {
                var result = service.calculate(gross, status);
                assertThat(result.ptkpAmount()).isEqualByComparingTo(status.getAnnualAmount());
            }
        }

        @Test
        @DisplayName("K/I statuses should have higher PTKP than K statuses")
        void kiStatusesShouldHaveHigherPtkp() {
            BigDecimal gross = new BigDecimal("30000000");

            var resultK0 = service.calculate(gross, PtkpStatus.K_0);
            var resultKI0 = service.calculate(gross, PtkpStatus.K_I_0);

            // K/I/0 has higher PTKP, so lower tax
            assertThat(resultKI0.ptkpAmount()).isGreaterThan(resultK0.ptkpAmount());
            assertThat(resultKI0.pkp()).isLessThan(resultK0.pkp());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very large salary")
        void shouldHandleVeryLargeSalary() {
            BigDecimal gross = new BigDecimal("1000000000"); // 1 billion/month
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // Should not throw and should calculate tax
            assertThat(result.monthlyPph21()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle salary at PTKP threshold")
        void shouldHandleSalaryAtPtkpThreshold() {
            // Find salary that results in annual neto = PTKP TK/0
            // This requires trial calculation
            BigDecimal gross = new BigDecimal("5500000");
            var result = service.calculate(gross, PtkpStatus.TK_0);

            // Near PTKP threshold, might have very small or zero tax
            assertThat(result.monthlyGrossIncome()).isEqualByComparingTo(gross);
        }

        @Test
        @DisplayName("Should handle negative result gracefully")
        void shouldHandleNegativeResultGracefully() {
            BigDecimal gross = new BigDecimal("3000000");
            var result = service.calculate(gross, PtkpStatus.K_I_3);

            // K/I/3 has highest PTKP (126M), low salary means negative PKP
            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
            assertThat(result.pkp()).isEqualByComparingTo("0");
        }
    }
}
