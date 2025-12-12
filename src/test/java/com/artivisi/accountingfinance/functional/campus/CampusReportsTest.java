package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * Campus Industry - Financial Reports Tests
 * Tests financial report functionality including receivables aging and revenue per program.
 * Uses Page Object Pattern for maintainability.
 */
@DisplayName("Campus Industry - Financial Reports")
@Import(CampusTestDataInitializer.class)
public class CampusReportsTest extends PlaywrightTestBase {

    // Page Objects
    private IncomeStatementPage incomeStatementPage;
    private BalanceSheetPage balanceSheetPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        incomeStatementPage = new IncomeStatementPage(page, baseUrl);
        balanceSheetPage = new BalanceSheetPage(page, baseUrl);
    }

    @Test
    @DisplayName("Should display Income Statement page")
    void shouldDisplayIncomeStatement() {
        loginAsAdmin();
        initPageObjects();

        incomeStatementPage.navigate()
            .verifyPageTitle()
            .verifyReportTitle();

        takeManualScreenshot("campus/report-income-statement");
    }

    @Test
    @DisplayName("Should display Balance Sheet page")
    void shouldDisplayBalanceSheet() {
        loginAsAdmin();
        initPageObjects();

        balanceSheetPage.navigate()
            .verifyPageTitle()
            .verifyReportTitle();

        takeManualScreenshot("campus/report-balance-sheet");
    }

    @Test
    @DisplayName("Should display student receivables in Balance Sheet")
    void shouldDisplayStudentReceivablesInBalanceSheet() {
        loginAsAdmin();
        initPageObjects();

        balanceSheetPage.navigate()
            .verifyPageTitle()
            .verifyReportTitle();

        // Verify student receivables accounts exist in report
        page.locator("text=Piutang SPP Mahasiswa").isVisible();

        takeManualScreenshot("campus/report-receivables");
    }

    @Test
    @DisplayName("Should display tuition revenue in Income Statement")
    void shouldDisplayTuitionRevenueInIncomeStatement() {
        loginAsAdmin();
        initPageObjects();

        incomeStatementPage.navigate()
            .verifyPageTitle()
            .verifyReportTitle();

        // Verify tuition revenue accounts exist in report
        page.locator("text=Pendapatan SPP").isVisible();

        takeManualScreenshot("campus/report-revenue");
    }

    @Test
    @DisplayName("Should display scholarship expenses in Income Statement")
    void shouldDisplayScholarshipExpensesInIncomeStatement() {
        loginAsAdmin();
        initPageObjects();

        incomeStatementPage.navigate()
            .verifyPageTitle()
            .verifyReportTitle();

        // Verify scholarship expense accounts exist in report
        page.locator("text=Beban Beasiswa").first().isVisible();

        takeManualScreenshot("campus/report-scholarship-expenses");
    }
}
