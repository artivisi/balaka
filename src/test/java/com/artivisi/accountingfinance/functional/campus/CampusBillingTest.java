package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Campus student billing: each billing template (SPP, uang pangkal, praktikum, wisuda) is checked on its template
 * page and posted through the transaction form once per billed student from campus/students.csv, at the fee of the
 * student's program in campus/programs.csv. Postings are dated in {@link #YEAR}, a year no other campus class uses.
 */
@DisplayName("Campus Industry - Student Billing")
@Import(CampusTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CampusBillingTest extends CampusTestBase {

    private static final int YEAR = 2081;

    private final List<UUID> bills = new ArrayList<>();

    @Test
    @Order(1)
    @DisplayName("Should seed the student receivable and billing revenue accounts in the chart of accounts")
    void shouldSeedBillingAccounts() {
        loginAsAdmin();
        navigateTo("/accounts");
        waitForPageLoad();
        page.locator("#btn-expand-1").click();
        page.locator("#btn-expand-1-1").click();
        page.locator("#btn-expand-4").click();
        page.locator("#btn-expand-4-1").click();

        assertAccount("1.1.10", "Piutang SPP Mahasiswa");
        assertAccount("1.1.11", "Piutang Uang Pangkal");
        assertAccount("1.1.12", "Piutang Biaya Praktikum");
        assertAccount("1.1.13", "Piutang Wisuda");
        assertAccount("4.1.01", "Pendapatan SPP");
        assertAccount("4.1.02", "Pendapatan Uang Pangkal");
        assertAccount("4.1.03", "Pendapatan Biaya Praktikum");
        assertAccount("4.1.04", "Pendapatan Wisuda");

        takeManualScreenshot("campus/accounts-list");
    }

    @Test
    @Order(2)
    @DisplayName("Should define each billing template as debit student receivable, credit billing revenue")
    void shouldDefineBillingTemplates() {
        loginAsAdmin();
        assertTemplateLines("Tagihan SPP Mahasiswa", "Pendapatan",
                "1.1.10|Piutang SPP Mahasiswa|Debit|amount", "4.1.01|Pendapatan SPP|Kredit|amount");
        takeManualScreenshot("campus/billing-templates");
        assertTemplateLines("Tagihan Uang Pangkal", "Pendapatan",
                "1.1.11|Piutang Uang Pangkal|Debit|amount", "4.1.02|Pendapatan Uang Pangkal|Kredit|amount");
        assertTemplateLines("Tagihan Biaya Praktikum", "Pendapatan",
                "1.1.12|Piutang Biaya Praktikum|Debit|amount", "4.1.03|Pendapatan Biaya Praktikum|Kredit|amount");
        assertTemplateLines("Tagihan Wisuda", "Pendapatan",
                "1.1.13|Piutang Wisuda|Debit|amount", "4.1.04|Pendapatan Wisuda|Kredit|amount");
    }

    @TestFactory
    @Order(3)
    @DisplayName("Should bill each student SPP, lab fee, new-student enrollment fee and final-year graduation fee of their program")
    Stream<DynamicTest> shouldBillStudents() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Student s : students()) {
            Program p = s.program();
            tests.add(bill(s, "Tagihan SPP Mahasiswa", "SPP", LocalDate.of(YEAR, 8, 1), p.tuition(), "1.1.10", "4.1.01"));
            tests.add(bill(s, "Tagihan Biaya Praktikum", "PRAK", LocalDate.of(YEAR, 8, 5), p.labFee(), "1.1.12", "4.1.03"));
            if (s.semester() == 1) {
                tests.add(bill(s, "Tagihan Uang Pangkal", "UP", LocalDate.of(YEAR, 7, 15), p.enrollmentFee(), "1.1.11", "4.1.02"));
            }
            if (s.semester() >= 5) {
                tests.add(bill(s, "Tagihan Wisuda", "WIS", LocalDate.of(YEAR, 9, 1), p.graduationFee(), "1.1.13", "4.1.04"));
            }
        }
        assertEquals(16, tests.size(), "6 SPP + 6 lab + 2 enrollment (semester 1) + 2 graduation (semester 5) bills");
        return tests.stream();
    }

    @Test
    @Order(4)
    @DisplayName("Should raise each student receivable by exactly the billed fees")
    void shouldRaiseReceivables() {
        assertEquals(16, bills.size(), "bills posted in order 3");
        assertEquals(42_000_000L, balance("1.1.10", bills));
        assertEquals(9_000_000L, balance("1.1.11", bills));
        assertEquals(5_500_000L, balance("1.1.12", bills));
        assertEquals(4_500_000L, balance("1.1.13", bills));
        assertEquals(-42_000_000L, balance("4.1.01", bills));
        assertEquals(-9_000_000L, balance("4.1.02", bills));
        assertEquals(-5_500_000L, balance("4.1.03", bills));
        assertEquals(-4_500_000L, balance("4.1.04", bills));
    }

    @Test
    @Order(5)
    @DisplayName("Should report the year's billing revenue per fee type on the income statement")
    void shouldReportBillingRevenue() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate(YEAR + "-01-01", YEAR + "-12-31")
                .verifyPageTitle();

        assertThat(page.locator(".revenue-row")).hasCount(4);
        assertThat(reportRow("revenue-row", "Pendapatan SPP").locator(".revenue-balance")).hasText("42.000.000");
        assertThat(reportRow("revenue-row", "Pendapatan Uang Pangkal").locator(".revenue-balance")).hasText("9.000.000");
        assertThat(reportRow("revenue-row", "Pendapatan Biaya Praktikum").locator(".revenue-balance")).hasText("5.500.000");
        assertThat(reportRow("revenue-row", "Pendapatan Wisuda").locator(".revenue-balance")).hasText("4.500.000");
        assertThat(page.locator("#total-revenue")).hasText("61.000.000");
        assertThat(page.locator(".expense-row")).hasCount(0);
        assertThat(page.locator("#net-income")).hasText("61.000.000");

        takeManualScreenshot("campus/billing-income-statement");
    }

    private DynamicTest bill(Student s, String template, String prefix, LocalDate date, long amount,
                             String receivable, String revenue) {
        return DynamicTest.dynamicTest(template + " " + s.nim() + " " + s.name(), () -> {
            loginAsAdmin();
            bills.add(post(template, date, amount, template + " " + s.name() + " (" + s.nim() + ") " + s.program().name(),
                    prefix + "-" + YEAR + "-" + s.nim() + "-" + runId,
                    List.of(journal("DEBIT", receivable, amount), journal("CREDIT", revenue, amount))));
        });
    }

    private void assertAccount(String code, String name) {
        String id = code.replace('.', '-');
        assertThat(page.locator("#account-code-" + id)).hasText(code);
        assertThat(page.locator("#account-name-" + id)).hasText(name);
    }
}
