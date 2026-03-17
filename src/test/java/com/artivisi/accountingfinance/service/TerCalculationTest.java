package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.TerCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PPh 21 TER Calculation (PMK 168/2023)")
class TerCalculationTest {

    private Pph21CalculationService service;

    @BeforeEach
    void setUp() {
        service = new Pph21CalculationService();
    }

    @Nested
    @DisplayName("TER Category Mapping")
    class CategoryMappingTests {

        @Test
        @DisplayName("TK_0, TK_1, K_0 should map to Category A")
        void categoryA() {
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.TK_0)).isEqualTo(TerCategory.A);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.TK_1)).isEqualTo(TerCategory.A);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_0)).isEqualTo(TerCategory.A);
        }

        @Test
        @DisplayName("TK_2, TK_3, K_1, K_2 should map to Category B")
        void categoryB() {
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.TK_2)).isEqualTo(TerCategory.B);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.TK_3)).isEqualTo(TerCategory.B);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_1)).isEqualTo(TerCategory.B);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_2)).isEqualTo(TerCategory.B);
        }

        @Test
        @DisplayName("K_3, K/I statuses should map to Category C")
        void categoryC() {
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_3)).isEqualTo(TerCategory.C);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_I_0)).isEqualTo(TerCategory.C);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_I_1)).isEqualTo(TerCategory.C);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_I_2)).isEqualTo(TerCategory.C);
            assertThat(TerCategory.fromPtkpStatus(PtkpStatus.K_I_3)).isEqualTo(TerCategory.C);
        }
    }

    @Nested
    @DisplayName("TER Rate Lookup")
    class RateLookupTests {

        @Test
        @DisplayName("Category B, gross 11,253,000 should get 2.5% (BUG-009 reference)")
        void categoryBGross11m() {
            BigDecimal rate = TerCategory.B.lookupRate(new BigDecimal("11253000"));
            assertThat(rate).isEqualByComparingTo("2.5");
        }

        @Test
        @DisplayName("Category B, gross 5,000,000 should get 0% (below threshold)")
        void categoryBGross5m() {
            BigDecimal rate = TerCategory.B.lookupRate(new BigDecimal("5000000"));
            assertThat(rate).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Category A, gross below 5,400,000 should get 0%")
        void categoryABelowThreshold() {
            BigDecimal rate = TerCategory.A.lookupRate(new BigDecimal("5000000"));
            assertThat(rate).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Category A, gross 10,000,000 should get 2%")
        void categoryAGross10m() {
            // 10,000,000 is between 9,650,000 and 10,050,000 in Cat A
            BigDecimal rate = TerCategory.A.lookupRate(new BigDecimal("10000000"));
            assertThat(rate).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("Category C, gross below 6,600,000 should get 0%")
        void categoryCBelowThreshold() {
            BigDecimal rate = TerCategory.C.lookupRate(new BigDecimal("6000000"));
            assertThat(rate).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Very high income should get highest rate")
        void veryHighIncome() {
            BigDecimal rate = TerCategory.A.lookupRate(new BigDecimal("2000000000"));
            assertThat(rate).isEqualByComparingTo("34");
        }
    }

    @Nested
    @DisplayName("TER Monthly Calculation")
    class TerMonthlyTests {

        @Test
        @DisplayName("K_2, gross 11,253,000 should yield PPh21 = 281,325 (BUG-009)")
        void bug009ReferenceCase() {
            var result = service.calculateTer(new BigDecimal("11253000"), PtkpStatus.K_2);

            assertThat(result.terCategory()).isEqualTo(TerCategory.B);
            assertThat(result.terRate()).isEqualByComparingTo("2.5");
            assertThat(result.monthlyPph21()).isEqualByComparingTo("281325");
        }

        @Test
        @DisplayName("K_2, gross 5,000,000 should yield PPh21 = 0")
        void zeroTerRate() {
            var result = service.calculateTer(new BigDecimal("5000000"), PtkpStatus.K_2);

            assertThat(result.terCategory()).isEqualTo(TerCategory.B);
            assertThat(result.terRate()).isEqualByComparingTo("0");
            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Null gross should return zero")
        void nullGross() {
            var result = service.calculateTer(null, PtkpStatus.TK_0);
            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Zero gross should return zero")
        void zeroGross() {
            var result = service.calculateTer(BigDecimal.ZERO, PtkpStatus.TK_0);
            assertThat(result.monthlyPph21()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Null PTKP should default to TK_0")
        void nullPtkp() {
            var result = service.calculateTer(new BigDecimal("10000000"), null);
            assertThat(result.terCategory()).isEqualTo(TerCategory.A);
        }
    }

    @Nested
    @DisplayName("December Annual Reconciliation")
    class DecemberReconciliationTests {

        @Test
        @DisplayName("Full year K_2 with salary change — matches Coretax reference")
        void fullYearWithSalaryChange() {
            // Reference: spt-pph21.json endyAnnual1721A1
            // Jan-Apr: 11,253,000/month, May-Dec: 5,000,000/month
            List<BigDecimal> monthlyGross = List.of(
                    new BigDecimal("11253000"), // Jan
                    new BigDecimal("11253000"), // Feb
                    new BigDecimal("11253000"), // Mar
                    new BigDecimal("11253000"), // Apr
                    new BigDecimal("5000000"),  // May
                    new BigDecimal("5000000"),  // Jun
                    new BigDecimal("5000000"),  // Jul
                    new BigDecimal("5000000"),  // Aug
                    new BigDecimal("5000000"),  // Sep
                    new BigDecimal("5000000"),  // Oct
                    new BigDecimal("5000000"),  // Nov
                    new BigDecimal("5000000")   // Dec
            );

            // Jan-Apr TER: 281,325 × 4 = 1,125,300
            // May-Nov TER: 0 × 7 = 0
            BigDecimal janNovPph21 = new BigDecimal("1125300");

            var result = service.calculateDecemberReconciliation(monthlyGross, PtkpStatus.K_2, janNovPph21);

            // Expected from reference data:
            // annualGross: 85,012,000
            // biayaJabatan: 4,250,600
            // annualNeto: 80,761,400
            // ptkp: 67,500,000
            // pkp: 13,261,000 (rounded down to nearest 1000)
            // annualTax: 663,050 (13,261,000 × 5%)
            // decemberPph21: 663,050 - 1,125,300 = -462,250
            assertThat(result.annualGross()).isEqualByComparingTo("85012000");
            assertThat(result.biayaJabatan()).isEqualByComparingTo("4250600");
            assertThat(result.ptkpAmount()).isEqualByComparingTo("67500000");
            assertThat(result.pkp()).isEqualByComparingTo("13261000");
            assertThat(result.annualTax()).isEqualByComparingTo("663050");
            assertThat(result.decemberPph21()).isEqualByComparingTo("-462250");
        }

        @Test
        @DisplayName("Constant salary — December reconciliation should be positive or near zero")
        void constantSalary() {
            // TK_0, 10M/month all year
            List<BigDecimal> monthlyGross = java.util.Collections.nCopies(12, new BigDecimal("10000000"));

            // TER rate for Cat A, 10M = 2% → 200,000/month × 11 = 2,200,000
            BigDecimal janNovPph21 = new BigDecimal("2200000");

            var result = service.calculateDecemberReconciliation(monthlyGross, PtkpStatus.TK_0, janNovPph21);

            // annualGross: 120,000,000
            // biayaJabatan: 6,000,000 (5% = 6M, at cap)
            // annualNeto: 114,000,000
            // ptkp TK_0: 54,000,000
            // pkp: 60,000,000
            // annualTax: 3,000,000 (60M × 5%)
            // decemberPph21: 3,000,000 - 2,200,000 = 800,000
            assertThat(result.annualGross()).isEqualByComparingTo("120000000");
            assertThat(result.annualTax()).isEqualByComparingTo("3000000");
            assertThat(result.decemberPph21()).isEqualByComparingTo("800000");
        }

        @Test
        @DisplayName("Low income — no tax, December reconciliation refunds TER withholdings")
        void lowIncomeRefund() {
            // K_I_3, 6M/month — PTKP 126M, annual gross 72M, well below PTKP
            List<BigDecimal> monthlyGross = java.util.Collections.nCopies(12, new BigDecimal("6000000"));

            // Assume some small TER was withheld
            BigDecimal janNovPph21 = new BigDecimal("50000");

            var result = service.calculateDecemberReconciliation(monthlyGross, PtkpStatus.K_I_3, janNovPph21);

            assertThat(result.annualTax()).isEqualByComparingTo("0");
            assertThat(result.decemberPph21()).isEqualByComparingTo("-50000");
        }
    }
}
