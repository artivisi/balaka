package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PPh 21 Calculation Service")
class Pph21CalculationServiceTest {

    private Pph21CalculationService service;

    @BeforeEach
    void setUp() {
        service = new Pph21CalculationService();
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
    @DisplayName("Progressive Tax Bracket Boundaries")
    class BracketBoundaryTests {

        @Test
        @DisplayName("Should calculate tax exactly at bracket 2 limit (250M)")
        void shouldCalculateTaxAtBracket2Limit() {
            BigDecimal pkp = new BigDecimal("250000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60M * 5% = 3M
            // 190M * 15% = 28.5M
            // Total = 31.5M
            assertThat(tax).isEqualByComparingTo("31500000");
        }

        @Test
        @DisplayName("Should calculate tax exactly at bracket 3 limit (500M)")
        void shouldCalculateTaxAtBracket3Limit() {
            BigDecimal pkp = new BigDecimal("500000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60M * 5% = 3M
            // 190M * 15% = 28.5M
            // 250M * 25% = 62.5M
            // Total = 94M
            assertThat(tax).isEqualByComparingTo("94000000");
        }

        @Test
        @DisplayName("Should calculate tax exactly at bracket 4 limit (5B)")
        void shouldCalculateTaxAtBracket4Limit() {
            BigDecimal pkp = new BigDecimal("5000000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60M * 5% = 3M
            // 190M * 15% = 28.5M
            // 250M * 25% = 62.5M
            // 4.5B * 30% = 1.35B
            // Total = 1,444,000,000
            assertThat(tax).isEqualByComparingTo("1444000000");
        }

        @Test
        @DisplayName("Should calculate tax for 1 rupiah over bracket 1")
        void shouldCalculateTaxOneOverBracket1() {
            BigDecimal pkp = new BigDecimal("60000001");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 60M * 5% = 3M
            // 1 * 15% = 0 (rounded)
            assertThat(tax).isGreaterThanOrEqualTo(new BigDecimal("3000000"));
        }

        @Test
        @DisplayName("Should calculate tax for small PKP amount")
        void shouldCalculateTaxForSmallPkp() {
            BigDecimal pkp = new BigDecimal("1000000");
            BigDecimal tax = service.calculateProgressiveTax(pkp);

            // 1M * 5% = 50,000
            assertThat(tax).isEqualByComparingTo("50000");
        }
    }

    @Nested
    @DisplayName("Annual PPh 21 (1721-A1)")
    class AnnualTaxTests {

        @Test
        @DisplayName("Should deduct biaya jabatan and employee JHT + JP before PTKP")
        void shouldDeductPensionContributions() {
            // 15,000,000 x 12; JHT 2% = 300,000 and JP 1% of 10,547,400 = 105,474 per month
            BigDecimal gross = new BigDecimal("180000000");
            BigDecimal pension = new BigDecimal("4865688");

            var annual = service.calculateAnnualTax(gross, pension, PtkpStatus.TK_0);

            assertThat(annual.biayaJabatan()).isEqualByComparingTo("6000000");      // 5% capped at 6,000,000
            assertThat(annual.annualNeto()).isEqualByComparingTo("169134312");       // 180,000,000 - 6,000,000 - 4,865,688
            assertThat(annual.pkp()).isEqualByComparingTo("115134000");              // - 54,000,000, floored to 1,000
            assertThat(annual.annualTax()).isEqualByComparingTo("11270100");         // 3,000,000 + 15% x 55,134,000
        }

        @Test
        @DisplayName("December PPh 21 should be annual tax minus Jan-Nov withholding")
        void decemberShouldSettleAnnualTax() {
            List<BigDecimal> months = java.util.Collections.nCopies(12, new BigDecimal("15000000"));

            var december = service.calculateDecemberReconciliation(
                    months, new BigDecimal("4865688"), PtkpStatus.TK_0, new BigDecimal("9900000"));

            assertThat(december.pensionContributions()).isEqualByComparingTo("4865688");
            assertThat(december.annualTax()).isEqualByComparingTo("11270100");
            assertThat(december.decemberPph21()).isEqualByComparingTo("1370100");
        }

        @Test
        @DisplayName("Should reject a missing PTKP status or pension amount")
        void shouldRejectMissingInputs() {
            assertThatThrownBy(() -> service.calculateAnnualTax(new BigDecimal("1000000"), BigDecimal.ZERO, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.calculateAnnualTax(new BigDecimal("1000000"), null, PtkpStatus.TK_0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
