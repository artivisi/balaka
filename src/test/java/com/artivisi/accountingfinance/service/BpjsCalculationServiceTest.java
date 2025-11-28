package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.service.BpjsCalculationService.BpjsCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BPJS Calculation Service")
class BpjsCalculationServiceTest {

    private BpjsCalculationService service;

    @BeforeEach
    void setUp() {
        service = new BpjsCalculationService();
    }

    @Nested
    @DisplayName("BPJS Kesehatan Calculation")
    class BpjsKesehatanCalculation {

        @Test
        @DisplayName("Should calculate 4% company and 1% employee for salary below ceiling")
        void shouldCalculateForSalaryBelowCeiling() {
            BigDecimal salary = new BigDecimal("10000000"); // 10 million

            BpjsCalculationResult result = service.calculate(salary);

            // 4% of 10,000,000 = 400,000
            assertThat(result.kesehatanCompany()).isEqualByComparingTo("400000");
            // 1% of 10,000,000 = 100,000
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("Should apply ceiling of Rp 12,000,000 for high salaries")
        void shouldApplyCeilingForHighSalaries() {
            BigDecimal salary = new BigDecimal("20000000"); // 20 million

            BpjsCalculationResult result = service.calculate(salary);

            // Ceiling is 12,000,000
            // 4% of 12,000,000 = 480,000
            assertThat(result.kesehatanCompany()).isEqualByComparingTo("480000");
            // 1% of 12,000,000 = 120,000
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("120000");
        }

        @Test
        @DisplayName("Should calculate for salary exactly at ceiling")
        void shouldCalculateForSalaryAtCeiling() {
            BigDecimal salary = new BigDecimal("12000000"); // exactly at ceiling

            BpjsCalculationResult result = service.calculate(salary);

            assertThat(result.kesehatanCompany()).isEqualByComparingTo("480000");
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("120000");
        }
    }

    @Nested
    @DisplayName("BPJS JKK Calculation")
    class BpjsJkkCalculation {

        @Test
        @DisplayName("Should use risk class 1 rate (0.24%) by default")
        void shouldUseRiskClass1ByDefault() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary);

            // 0.24% of 10,000,000 = 24,000
            assertThat(result.jkk()).isEqualByComparingTo("24000");
        }

        @Test
        @DisplayName("Should calculate JKK for risk class 2 (0.54%)")
        void shouldCalculateForRiskClass2() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary, 2);

            // 0.54% of 10,000,000 = 54,000
            assertThat(result.jkk()).isEqualByComparingTo("54000");
        }

        @Test
        @DisplayName("Should calculate JKK for risk class 3 (0.89%)")
        void shouldCalculateForRiskClass3() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary, 3);

            // 0.89% of 10,000,000 = 89,000
            assertThat(result.jkk()).isEqualByComparingTo("89000");
        }

        @Test
        @DisplayName("Should calculate JKK for risk class 4 (1.27%)")
        void shouldCalculateForRiskClass4() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary, 4);

            // 1.27% of 10,000,000 = 127,000
            assertThat(result.jkk()).isEqualByComparingTo("127000");
        }

        @Test
        @DisplayName("Should calculate JKK for risk class 5 (1.74%)")
        void shouldCalculateForRiskClass5() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary, 5);

            // 1.74% of 10,000,000 = 174,000
            assertThat(result.jkk()).isEqualByComparingTo("174000");
        }

        @Test
        @DisplayName("Should default to class 1 for invalid risk class")
        void shouldDefaultToClass1ForInvalidRiskClass() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary, 99);

            assertThat(result.jkk()).isEqualByComparingTo("24000");
        }
    }

    @Nested
    @DisplayName("BPJS JKM Calculation")
    class BpjsJkmCalculation {

        @Test
        @DisplayName("Should calculate 0.3% for JKM (company only)")
        void shouldCalculateJkm() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary);

            // 0.3% of 10,000,000 = 30,000
            assertThat(result.jkm()).isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("BPJS JHT Calculation")
    class BpjsJhtCalculation {

        @Test
        @DisplayName("Should calculate 3.7% company and 2% employee")
        void shouldCalculateJht() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary);

            // 3.7% of 10,000,000 = 370,000
            assertThat(result.jhtCompany()).isEqualByComparingTo("370000");
            // 2% of 10,000,000 = 200,000
            assertThat(result.jhtEmployee()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("Should not apply ceiling for JHT")
        void shouldNotApplyCeilingForJht() {
            BigDecimal salary = new BigDecimal("50000000"); // 50 million

            BpjsCalculationResult result = service.calculate(salary);

            // 3.7% of 50,000,000 = 1,850,000
            assertThat(result.jhtCompany()).isEqualByComparingTo("1850000");
            // 2% of 50,000,000 = 1,000,000
            assertThat(result.jhtEmployee()).isEqualByComparingTo("1000000");
        }
    }

    @Nested
    @DisplayName("BPJS JP Calculation")
    class BpjsJpCalculation {

        @Test
        @DisplayName("Should calculate 2% company and 1% employee for salary below ceiling")
        void shouldCalculateForSalaryBelowCeiling() {
            BigDecimal salary = new BigDecimal("8000000");

            BpjsCalculationResult result = service.calculate(salary);

            // 2% of 8,000,000 = 160,000
            assertThat(result.jpCompany()).isEqualByComparingTo("160000");
            // 1% of 8,000,000 = 80,000
            assertThat(result.jpEmployee()).isEqualByComparingTo("80000");
        }

        @Test
        @DisplayName("Should apply ceiling of Rp 10,042,300 for high salaries")
        void shouldApplyCeilingForHighSalaries() {
            BigDecimal salary = new BigDecimal("20000000"); // 20 million

            BpjsCalculationResult result = service.calculate(salary);

            // Ceiling is 10,042,300
            // 2% of 10,042,300 = 200,846
            assertThat(result.jpCompany()).isEqualByComparingTo("200846");
            // 1% of 10,042,300 = 100,423
            assertThat(result.jpEmployee()).isEqualByComparingTo("100423");
        }
    }

    @Nested
    @DisplayName("Total Calculations")
    class TotalCalculations {

        @Test
        @DisplayName("Should calculate total company contribution")
        void shouldCalculateTotalCompany() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary);

            // Kesehatan: 400,000 + JKK: 24,000 + JKM: 30,000 + JHT: 370,000 + JP: 200,846 = 1,024,846
            BigDecimal expectedTotal = result.kesehatanCompany()
                .add(result.jkk())
                .add(result.jkm())
                .add(result.jhtCompany())
                .add(result.jpCompany());

            assertThat(result.totalCompany()).isEqualByComparingTo(expectedTotal);
        }

        @Test
        @DisplayName("Should calculate total employee contribution")
        void shouldCalculateTotalEmployee() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary);

            // Kesehatan: 100,000 + JHT: 200,000 + JP: 100,423 = 400,423
            BigDecimal expectedTotal = result.kesehatanEmployee()
                .add(result.jhtEmployee())
                .add(result.jpEmployee());

            assertThat(result.totalEmployee()).isEqualByComparingTo(expectedTotal);
        }

        @Test
        @DisplayName("Should calculate grand total")
        void shouldCalculateGrandTotal() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary);

            assertThat(result.grandTotal())
                .isEqualByComparingTo(result.totalCompany().add(result.totalEmployee()));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should return zero for null salary")
        void shouldReturnZeroForNullSalary() {
            BpjsCalculationResult result = service.calculate(null);

            assertThat(result.kesehatanCompany()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalCompany()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalEmployee()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should return zero for zero salary")
        void shouldReturnZeroForZeroSalary() {
            BpjsCalculationResult result = service.calculate(BigDecimal.ZERO);

            assertThat(result.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should return zero for negative salary")
        void shouldReturnZeroForNegativeSalary() {
            BpjsCalculationResult result = service.calculate(new BigDecimal("-5000000"));

            assertThat(result.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle small salary correctly")
        void shouldHandleSmallSalary() {
            BigDecimal salary = new BigDecimal("1000000"); // 1 million (minimum wage range)

            BpjsCalculationResult result = service.calculate(salary);

            // 4% of 1,000,000 = 40,000
            assertThat(result.kesehatanCompany()).isEqualByComparingTo("40000");
            // 1% of 1,000,000 = 10,000
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("Should default to risk class 1 for null risk class")
        void shouldDefaultToRiskClass1ForNullRiskClass() {
            BigDecimal salary = new BigDecimal("10000000");

            BpjsCalculationResult result = service.calculate(salary, null);

            // Should use class 1 rate (0.24%)
            assertThat(result.jkk()).isEqualByComparingTo("24000");
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Should calculate for typical IT professional salary (Rp 15,000,000)")
        void shouldCalculateForTypicalItProfessional() {
            BigDecimal salary = new BigDecimal("15000000");

            BpjsCalculationResult result = service.calculate(salary, 1);

            // Kesehatan capped at 12M: 4% = 480,000, 1% = 120,000
            assertThat(result.kesehatanCompany()).isEqualByComparingTo("480000");
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("120000");

            // JKK (0.24%): 36,000
            assertThat(result.jkk()).isEqualByComparingTo("36000");

            // JKM (0.3%): 45,000
            assertThat(result.jkm()).isEqualByComparingTo("45000");

            // JHT: 3.7% = 555,000, 2% = 300,000
            assertThat(result.jhtCompany()).isEqualByComparingTo("555000");
            assertThat(result.jhtEmployee()).isEqualByComparingTo("300000");

            // JP capped at 10,042,300: 2% = 200,846, 1% = 100,423
            assertThat(result.jpCompany()).isEqualByComparingTo("200846");
            assertThat(result.jpEmployee()).isEqualByComparingTo("100423");
        }

        @Test
        @DisplayName("Should calculate for minimum wage salary (Rp 4,900,000 - Jakarta 2024)")
        void shouldCalculateForMinimumWage() {
            BigDecimal salary = new BigDecimal("4900000");

            BpjsCalculationResult result = service.calculate(salary);

            // All below ceilings
            assertThat(result.kesehatanCompany()).isEqualByComparingTo("196000"); // 4%
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("49000");  // 1%
            assertThat(result.jkk()).isEqualByComparingTo("11760");               // 0.24%
            assertThat(result.jkm()).isEqualByComparingTo("14700");               // 0.3%
            assertThat(result.jhtCompany()).isEqualByComparingTo("181300");       // 3.7%
            assertThat(result.jhtEmployee()).isEqualByComparingTo("98000");       // 2%
            assertThat(result.jpCompany()).isEqualByComparingTo("98000");         // 2%
            assertThat(result.jpEmployee()).isEqualByComparingTo("49000");        // 1%
        }

        @Test
        @DisplayName("Should calculate for executive salary (Rp 50,000,000)")
        void shouldCalculateForExecutive() {
            BigDecimal salary = new BigDecimal("50000000");

            BpjsCalculationResult result = service.calculate(salary, 1);

            // Kesehatan capped at 12M
            assertThat(result.kesehatanCompany()).isEqualByComparingTo("480000");
            assertThat(result.kesehatanEmployee()).isEqualByComparingTo("120000");

            // JKK/JKM not capped
            assertThat(result.jkk()).isEqualByComparingTo("120000");    // 0.24% of 50M
            assertThat(result.jkm()).isEqualByComparingTo("150000");    // 0.3% of 50M

            // JHT not capped
            assertThat(result.jhtCompany()).isEqualByComparingTo("1850000");  // 3.7% of 50M
            assertThat(result.jhtEmployee()).isEqualByComparingTo("1000000"); // 2% of 50M

            // JP capped at 10,042,300
            assertThat(result.jpCompany()).isEqualByComparingTo("200846");
            assertThat(result.jpEmployee()).isEqualByComparingTo("100423");
        }
    }
}
