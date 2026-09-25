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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Campus scholarships: every scholarship student in campus/students.csv is billed the SPP of their program, granted
 * the scholarship percentage through the matching scholarship template (expense, reduces the SPP receivable) and pays
 * the remainder. Postings are dated in {@link #YEAR}, a year no other campus class uses.
 */
@DisplayName("Campus Industry - Scholarships")
@Import(CampusTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CampusScholarshipTest extends CampusTestBase {

    private static final int YEAR = 2083;

    @Test
    @Order(1)
    @DisplayName("Should seed the scholarship expense accounts under Beban Kemahasiswaan")
    void shouldSeedScholarshipAccounts() {
        loginAsAdmin();
        navigateTo("/accounts");
        waitForPageLoad();
        page.locator("#btn-expand-5").click();
        page.locator("#btn-expand-5-3").click();

        assertThat(page.locator("#account-name-5-3")).hasText("Beban Kemahasiswaan");
        assertThat(page.locator("#account-name-5-3-01")).hasText("Beban Beasiswa Prestasi");
        assertThat(page.locator("#account-name-5-3-02")).hasText("Beban Beasiswa Tidak Mampu");
        assertThat(page.locator("#account-row-5-3-01")).isVisible();
        assertThat(page.locator("#account-row-5-3-02")).isVisible();

        takeManualScreenshot("campus/scholarship-accounts");
    }

    @Test
    @Order(2)
    @DisplayName("Should define both scholarship templates as scholarship expense against the SPP receivable")
    void shouldDefineScholarshipTemplates() {
        loginAsAdmin();
        assertTemplateLines("Beasiswa Prestasi", "Pengeluaran",
                "5.3.01|Beban Beasiswa Prestasi|Debit|amount", "1.1.10|Piutang SPP Mahasiswa|Kredit|amount");
        takeManualScreenshot("campus/scholarship-templates");
        assertTemplateLines("Beasiswa Tidak Mampu", "Pengeluaran",
                "5.3.02|Beban Beasiswa Tidak Mampu|Debit|amount", "1.1.10|Piutang SPP Mahasiswa|Kredit|amount");
    }

    @TestFactory
    @Order(3)
    @DisplayName("Should grant each scholarship student their percentage of SPP and settle the remainder")
    Stream<DynamicTest> shouldGrantScholarships() {
        List<Student> recipients = students().stream().filter(s -> !s.scholarshipType().equals("NONE")).toList();
        assertEquals(List.of("2201020001:PRESTASI:50", "2301010003:TIDAK_MAMPU:75"), recipients.stream()
                .map(s -> s.nim() + ":" + s.scholarshipType() + ":" + s.scholarshipPercentage()).toList());
        return recipients.stream().map(s -> DynamicTest.dynamicTest(s.scholarshipType() + " " + s.nim() + " " + s.name(), () -> {
            long tuition = s.program().tuition();
            long scholarship = tuition * s.scholarshipPercentage() / 100;
            long remainder = tuition - scholarship;
            String template = s.scholarshipType().equals("PRESTASI") ? "Beasiswa Prestasi" : "Beasiswa Tidak Mampu";
            String expense = s.scholarshipType().equals("PRESTASI") ? "5.3.01" : "5.3.02";
            loginAsAdmin();

            UUID bill = post("Tagihan SPP Mahasiswa", LocalDate.of(YEAR, 8, 1), tuition, "Tagihan SPP " + s.name(),
                    ref("SPP", s), List.of(journal("DEBIT", "1.1.10", tuition), journal("CREDIT", "4.1.01", tuition)));
            UUID grant = post(template, LocalDate.of(YEAR, 8, 3), scholarship,
                    template + " " + s.scholarshipPercentage() + "% " + s.name(), ref("BSW", s),
                    List.of(journal("DEBIT", expense, scholarship), journal("CREDIT", "1.1.10", scholarship)));
            takeManualScreenshot("campus/scholarship-" + s.scholarshipType().toLowerCase());
            UUID payment = postToBank("Pembayaran SPP", LocalDate.of(YEAR, 8, 20), remainder,
                    "Pelunasan SPP setelah beasiswa " + s.name(), ref("PAY", s), "1.1.02",
                    List.of(journal("DEBIT", "1.1.02", remainder), journal("CREDIT", "1.1.10", remainder)));

            List<UUID> ids = List.of(bill, grant, payment);
            assertEquals(0L, balance("1.1.10", ids), "SPP receivable of " + s.nim());
            assertEquals(scholarship, balance(expense, ids));
            assertEquals(remainder, balance("1.1.02", ids));
        }));
    }

    @Test
    @Order(4)
    @DisplayName("Should report SPP revenue and each scholarship expense of the year on the income statement")
    void shouldReportScholarshipExpenses() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate(YEAR + "-01-01", YEAR + "-12-31")
                .verifyPageTitle();

        assertThat(page.locator(".revenue-row")).hasCount(1);
        assertThat(reportRow("revenue-row", "Pendapatan SPP").locator(".revenue-balance")).hasText("13.500.000");
        assertThat(page.locator(".expense-row")).hasCount(2);
        assertThat(reportRow("expense-row", "Beban Beasiswa Prestasi").locator(".expense-balance")).hasText("(3.000.000)");
        assertThat(reportRow("expense-row", "Beban Beasiswa Tidak Mampu").locator(".expense-balance")).hasText("(5.625.000)");
        assertThat(page.locator("#total-expense")).hasText("(8.625.000)");
        assertThat(page.locator("#net-income")).hasText("4.875.000");

        takeManualScreenshot("campus/report-scholarship-expenses");
    }

    private String ref(String prefix, Student s) {
        return prefix + "-" + YEAR + "-" + s.nim() + "-" + runId;
    }
}
