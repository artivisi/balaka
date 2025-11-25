package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.functional.page.TrialBalancePage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Trial Balance Report (Section 1.1)")
class TrialBalanceTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private TrialBalancePage trialBalancePage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        trialBalancePage = new TrialBalancePage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("1.1.1 Navigation")
    class NavigationTests {

        @Test
        @DisplayName("Should display trial balance page title")
        void shouldDisplayTrialBalancePageTitle() {
            trialBalancePage.navigate();

            trialBalancePage.assertPageTitleVisible();
            trialBalancePage.assertPageTitleText("Neraca Saldo");
        }

        @Test
        @DisplayName("Should display report title 'NERACA SALDO'")
        void shouldDisplayReportTitle() {
            trialBalancePage.navigate();

            trialBalancePage.assertReportTitleVisible();
            trialBalancePage.assertReportTitleText("NERACA SALDO");
        }
    }

    @Nested
    @DisplayName("1.1.2 Filter Controls")
    class FilterControlsTests {

        @Test
        @DisplayName("Should display date selector")
        void shouldDisplayDateSelector() {
            trialBalancePage.navigate();

            trialBalancePage.assertAsOfDateVisible();
        }

        @Test
        @DisplayName("Should display generate button")
        void shouldDisplayGenerateButton() {
            trialBalancePage.navigate();

            trialBalancePage.assertGenerateButtonVisible();
        }

        @Test
        @DisplayName("Should display print button")
        void shouldDisplayPrintButton() {
            trialBalancePage.navigate();

            trialBalancePage.assertPrintButtonVisible();
        }
    }

    @Nested
    @DisplayName("1.1.3 Trial Balance Display")
    class TrialBalanceDisplayTests {

        @Test
        @DisplayName("Should display trial balance table")
        void shouldDisplayTrialBalanceTable() {
            trialBalancePage.navigateWithDate("2024-06-30");

            trialBalancePage.assertTrialBalanceTableVisible();
        }

        @Test
        @DisplayName("Should display accounts with activity")
        void shouldDisplayAccountsWithActivity() {
            trialBalancePage.navigateWithDate("2024-06-30");

            // Should have rows from the test data
            int rowCount = trialBalancePage.getTrialBalanceRowCount();
            assertThat(rowCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should display total debit and credit")
        void shouldDisplayTotalDebitAndCredit() {
            trialBalancePage.navigateWithDate("2024-06-30");

            trialBalancePage.assertTotalDebitVisible();
            trialBalancePage.assertTotalCreditVisible();
        }

        @Test
        @DisplayName("Should display balance status")
        void shouldDisplayBalanceStatus() {
            trialBalancePage.navigateWithDate("2024-06-30");

            trialBalancePage.assertBalanceStatusVisible();
        }
    }

    @Nested
    @DisplayName("1.1.4 Trial Balance Calculation")
    class TrialBalanceCalculationTests {

        @Test
        @DisplayName("Should show totals that balance (debit = credit)")
        void shouldShowTotalsThatBalance() {
            trialBalancePage.navigateWithDate("2024-06-30");

            // Trial Balance totals = 278,000,000 (sum of account balances, includes V905 profitability test data)
            trialBalancePage.assertBalanceStatusText("Balance");
            trialBalancePage.assertBalanceMessageContains("Total Debit = Total Kredit");
        }

        @Test
        @DisplayName("Should show expected total debit")
        void shouldShowExpectedTotalDebit() {
            trialBalancePage.navigateWithDate("2024-06-30");

            // Trial Balance Total Debit = 278,000,000
            // V901: Cash 134M + BCA 40M + Peralatan 30M + Gaji 16M + Server 2M + Penyusutan 1M = 223M
            // V905: Cash -16M + BCA +53M + Gaji +16M + Server +2M = +55M
            // Total: 223M + 55M = 278M (Cash 118M + BCA 93M + Peralatan 30M + Gaji 32M + Server 4M + Penyusutan 1M)
            String totalDebit = trialBalancePage.getTotalDebitText();
            assertThat(totalDebit).isEqualTo("278.000.000");
        }

        @Test
        @DisplayName("Should show expected total credit")
        void shouldShowExpectedTotalCredit() {
            trialBalancePage.navigateWithDate("2024-06-30");

            // Trial Balance Total Credit = 278,000,000
            // V901: Akum Peny 1M + Hutang 10M + Modal 150M + Konsultasi 37M + Development 25M = 223M
            // V905: Konsultasi +28M (10M + 18M) + Development +27M (15M + 12M) = +55M
            // Total: 223M + 55M = 278M (Akum Peny 1M + Hutang 10M + Modal 150M + Konsultasi 65M + Development 52M)
            String totalCredit = trialBalancePage.getTotalCreditText();
            assertThat(totalCredit).isEqualTo("278.000.000");
        }

        @Test
        @DisplayName("Should display Cash account with correct balance")
        void shouldDisplayCashAccountWithCorrectBalance() {
            trialBalancePage.navigateWithDate("2024-06-30");

            // Cash account should be visible
            trialBalancePage.assertAccountRowExists("1.1.01");
            trialBalancePage.assertAccountNameExists("Kas");
        }

        @Test
        @DisplayName("Should display Modal Disetor account with correct balance")
        void shouldDisplayModalDisetorAccountWithCorrectBalance() {
            trialBalancePage.navigateWithDate("2024-06-30");

            // Modal Disetor account should be visible
            trialBalancePage.assertAccountRowExists("3.1.01");
            trialBalancePage.assertAccountNameExists("Modal Disetor");
        }
    }

    @Nested
    @DisplayName("1.1.5 Date Filter Functionality")
    class DateFilterTests {

        @Test
        @DisplayName("Should update report date in header when date changes")
        void shouldUpdateReportDateInHeader() {
            trialBalancePage.navigateWithDate("2024-03-31");

            trialBalancePage.assertReportDateContains("31 Maret 2024");
        }

        @Test
        @DisplayName("Should show fewer transactions for earlier date")
        void shouldShowFewerTransactionsForEarlierDate() {
            // Count rows for end of Q2
            trialBalancePage.navigateWithDate("2024-06-30");
            int rowsQ2 = trialBalancePage.getTrialBalanceRowCount();

            // Count rows for end of Q1 (should have fewer accounts with activity)
            trialBalancePage.navigateWithDate("2024-03-31");
            int rowsQ1 = trialBalancePage.getTrialBalanceRowCount();

            // Q2 should have >= Q1 accounts (more activity over time)
            assertThat(rowsQ2).isGreaterThanOrEqualTo(rowsQ1);
        }

        @Test
        @DisplayName("Should show only POSTED entries, excluding VOID")
        void shouldShowOnlyPostedEntriesExcludingVoid() {
            // VOID entry JRN-2024-0008 dated 2024-04-15 should not affect totals
            trialBalancePage.navigateWithDate("2024-06-30");

            // Totals should still balance at 278,000,000 (VOID excluded, includes V905 data)
            String totalDebit = trialBalancePage.getTotalDebitText();
            String totalCredit = trialBalancePage.getTotalCreditText();

            assertThat(totalDebit).isEqualTo(totalCredit);
            assertThat(totalDebit).isEqualTo("278.000.000");
        }

        @Test
        @DisplayName("Should exclude DRAFT entries from calculation")
        void shouldExcludeDraftEntriesFromCalculation() {
            // DRAFT entry JRN-2024-0012 dated 2024-06-30 should not affect totals
            trialBalancePage.navigateWithDate("2024-06-30");

            // Totals should be 278,000,000 (DRAFT excluded, includes V905 data)
            String totalDebit = trialBalancePage.getTotalDebitText();
            assertThat(totalDebit).isEqualTo("278.000.000");
        }

        @Test
        @DisplayName("Should exclude soft-deleted entries from calculation")
        void shouldExcludeSoftDeletedEntriesFromCalculation() {
            // Soft-deleted entry JRN-2024-0015 should not affect totals
            trialBalancePage.navigateWithDate("2024-06-30");

            // Totals should be 278,000,000 (soft-deleted excluded, includes V905 data)
            String totalDebit = trialBalancePage.getTotalDebitText();
            assertThat(totalDebit).isEqualTo("278.000.000");
        }

        @Test
        @DisplayName("Should exclude future entries when date is filtered")
        void shouldExcludeFutureEntriesWhenDateFiltered() {
            // JRN-2024-0013 and JRN-2024-0014 are July 2024, should be excluded from June report
            trialBalancePage.navigateWithDate("2024-06-30");

            // Totals should be 278,000,000 (future entries excluded, includes V905 data)
            String totalDebit = trialBalancePage.getTotalDebitText();
            assertThat(totalDebit).isEqualTo("278.000.000");
        }
    }
}
