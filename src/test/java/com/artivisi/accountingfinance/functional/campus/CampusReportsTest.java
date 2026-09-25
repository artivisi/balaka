package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Campus financial reports. July 2024 comes from the seeded campus/financial-*.csv (no campus test class posts on
 * or before 2024-07-31), so its reports are asserted as exact amounts:
 * revenue SPP 540,000,000 + uang pangkal 180,000,000; expenses dosen 150,000,000, karyawan 60,000,000,
 * listrik 25,000,000, beasiswa prestasi 18,000,000; net 467,000,000; Bank BCA 1,467,000,000 after the
 * 1,000,000,000 yayasan capital.
 *
 * Student receivables are posted by this class into {@link #YEAR} (no other campus class uses it) and asserted as the
 * balance sheet change from the end of the previous year.
 */
@DisplayName("Campus Industry - Financial Reports")
@Import(CampusTestDataInitializer.class)
class CampusReportsTest extends CampusTestBase {

    private static final int YEAR = 2084;

    @Test
    @DisplayName("Should show July 2024 SPP and enrollment revenue, and the resulting surplus, on the income statement")
    void shouldShowTuitionRevenue() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate("2024-07-01", "2024-07-31")
                .verifyPageTitle()
                .verifyReportTitle();

        assertThat(page.locator(".revenue-row")).hasCount(2);
        assertThat(reportRow("revenue-row", "Pendapatan SPP").locator(".revenue-balance")).hasText("540.000.000");
        assertThat(reportRow("revenue-row", "Pendapatan Uang Pangkal").locator(".revenue-balance")).hasText("180.000.000");
        assertThat(page.locator("#total-revenue")).hasText("720.000.000");
        assertThat(page.locator("#total-expense")).hasText("(253.000.000)");
        assertThat(page.locator("#net-income")).hasText("467.000.000");

        takeManualScreenshot("campus/report-revenue");
    }

    @Test
    @DisplayName("Should show July 2024 lecturer, staff, utility and scholarship expenses on the income statement")
    void shouldShowExpensesIncludingScholarships() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate("2024-07-01", "2024-07-31");

        assertThat(page.locator(".expense-row")).hasCount(4);
        assertThat(reportRow("expense-row", "Beban Gaji Dosen Tetap").locator(".expense-balance")).hasText("(150.000.000)");
        assertThat(reportRow("expense-row", "Beban Gaji Karyawan").locator(".expense-balance")).hasText("(60.000.000)");
        assertThat(reportRow("expense-row", "Beban Listrik").locator(".expense-balance")).hasText("(25.000.000)");
        assertThat(reportRow("expense-row", "Beban Beasiswa Prestasi").locator(".expense-balance")).hasText("(18.000.000)");

        takeManualScreenshot("campus/report-income-statement");
    }

    @Test
    @DisplayName("Should show the July 2024 bank balance, yayasan capital and surplus on the balance sheet")
    void shouldShowBalanceSheet() {
        loginAsAdmin();
        new BalanceSheetPage(page, baseUrl()).navigate("2024-07-31")
                .verifyPageTitle()
                .verifyReportTitle()
                .verifyBalanced();

        assertThat(page.locator(".asset-row")).hasCount(1);
        assertThat(reportRow("asset-row", "Bank BCA").locator(".asset-balance")).hasText("1.467.000.000");
        assertThat(page.locator("#total-assets")).hasText("1.467.000.000");
        assertThat(page.locator(".liability-row")).hasCount(0);
        assertThat(reportRow("equity-row", "Modal Yayasan").locator(".equity-balance")).hasText("1.000.000.000");
        assertThat(page.locator("#current-year-earnings")).hasText("467.000.000");
        assertThat(page.locator("#total-equity")).hasText("1.467.000.000");

        takeManualScreenshot("campus/report-balance-sheet");
    }

    @Test
    @DisplayName("Should split July 2024 cash into operating inflows/outflows per template and yayasan financing")
    void shouldShowCashFlow() {
        loginAsAdmin();
        new CashFlowPage(page, baseUrl()).navigate("2024-07-01", "2024-07-31")
                .verifyPageTitle();

        assertThat(cashFlowItem("Pembayaran SPP")).hasText("540.000.000");
        assertThat(cashFlowItem("Pembayaran Uang Pangkal")).hasText("180.000.000");
        assertThat(cashFlowItem("Gaji Dosen Tetap")).hasText("(150.000.000)");
        assertThat(cashFlowItem("Gaji Karyawan")).hasText("(60.000.000)");
        assertThat(cashFlowItem("Bayar Listrik Kampus")).hasText("(25.000.000)");
        assertThat(cashFlowItem("Beasiswa Prestasi")).hasText("(18.000.000)");
        assertThat(page.locator("#operating-total")).hasText("467.000.000");
        assertThat(cashFlowItem("Setoran Modal Yayasan")).hasText("1.000.000.000");
        assertThat(page.locator("#financing-total")).hasText("1.000.000.000");
        assertThat(page.locator("#investing-total")).hasText("0");
        assertThat(page.locator("#net-cash-change")).hasText("1.467.000.000");
        assertThat(page.locator("#beginning-balance")).hasText("0");
        assertThat(page.locator("#ending-balance")).hasText("Rp 1.467.000.000");

        takeManualScreenshot("campus/report-cash-flow");
    }

    @Test
    @DisplayName("Should carry an unpaid SPP balance as Piutang SPP Mahasiswa on the balance sheet")
    void shouldShowStudentReceivables() {
        Student s = student("2401020002");
        long tuition = s.program().tuition();
        assertEquals(6_000_000L, tuition);
        String before = (YEAR - 1) + "-12-31";
        String end = YEAR + "-12-31";
        loginAsAdmin();

        BalanceSheetPage balanceSheet = new BalanceSheetPage(page, baseUrl());
        balanceSheet.navigate(before).verifyBalanced();
        long receivableBefore = rowAmount("asset-row", "Piutang SPP Mahasiswa", ".asset-balance");
        long bankBefore = rowAmount("asset-row", "Bank BCA", ".asset-balance");
        long assetsBefore = amount(page.locator("#total-assets"));
        long equityBefore = amount(page.locator("#total-equity"));

        post("Tagihan SPP Mahasiswa", LocalDate.of(YEAR, 8, 1), tuition, "Tagihan SPP " + s.name(),
                "SPP-" + YEAR + "-" + s.nim() + "-" + runId,
                List.of(journal("DEBIT", "1.1.10", tuition), journal("CREDIT", "4.1.01", tuition)));
        postToBank("Pembayaran SPP", LocalDate.of(YEAR, 8, 15), 2_000_000L, "Cicilan SPP " + s.name(),
                "PAY-" + YEAR + "-" + s.nim() + "-" + runId, "1.1.02",
                List.of(journal("DEBIT", "1.1.02", 2_000_000L), journal("CREDIT", "1.1.10", 2_000_000L)));

        balanceSheet.navigate(end).verifyBalanced();
        assertEquals(4_000_000L, rowAmount("asset-row", "Piutang SPP Mahasiswa", ".asset-balance") - receivableBefore);
        assertEquals(2_000_000L, rowAmount("asset-row", "Bank BCA", ".asset-balance") - bankBefore);
        assertEquals(6_000_000L, amount(page.locator("#total-assets")) - assetsBefore);
        assertEquals(6_000_000L, amount(page.locator("#total-equity")) - equityBefore);

        takeManualScreenshot("campus/report-receivables");
    }

    private long rowAmount(String rowClass, String accountName, String balanceClass) {
        Locator row = reportRow(rowClass, accountName);
        return row.count() == 0 ? 0L : amount(row.locator(balanceClass));
    }

    private Locator cashFlowItem(String templateName) {
        return page.locator("span.text-gray-700:text-is('" + templateName + "') + span");
    }
}
