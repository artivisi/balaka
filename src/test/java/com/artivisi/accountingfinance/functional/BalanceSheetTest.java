package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Balance Sheet Report (Section 3)")
class BalanceSheetTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private BalanceSheetPage balanceSheetPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        balanceSheetPage = new BalanceSheetPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("3.1 Navigation")
    class NavigationTests {

        @Test
        @DisplayName("Should display balance sheet page title")
        void shouldDisplayBalanceSheetPageTitle() {
            balanceSheetPage.navigate();

            balanceSheetPage.assertPageTitleVisible();
            balanceSheetPage.assertPageTitleText("Neraca");
        }

        @Test
        @DisplayName("Should display report title 'LAPORAN POSISI KEUANGAN'")
        void shouldDisplayReportTitle() {
            balanceSheetPage.navigate();

            balanceSheetPage.assertReportTitleVisible();
            balanceSheetPage.assertReportTitleText("LAPORAN POSISI KEUANGAN");
        }
    }

    @Nested
    @DisplayName("3.2 Filter Controls")
    class FilterControlsTests {

        @Test
        @DisplayName("Should display date selector")
        void shouldDisplayDateSelector() {
            balanceSheetPage.navigate();

            balanceSheetPage.assertAsOfDateVisible();
        }

        @Test
        @DisplayName("Should display generate button")
        void shouldDisplayGenerateButton() {
            balanceSheetPage.navigate();

            balanceSheetPage.assertGenerateButtonVisible();
        }

        @Test
        @DisplayName("Should display print button")
        void shouldDisplayPrintButton() {
            balanceSheetPage.navigate();

            balanceSheetPage.assertPrintButtonVisible();
        }
    }

    @Nested
    @DisplayName("3.3 Balance Sheet Structure")
    class BalanceSheetStructureTests {

        @Test
        @DisplayName("Should display asset items section")
        void shouldDisplayAssetItemsSection() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertAssetItemsVisible();
        }

        @Test
        @DisplayName("Should display liability items section")
        void shouldDisplayLiabilityItemsSection() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertLiabilityItemsVisible();
        }

        @Test
        @DisplayName("Should display equity items section")
        void shouldDisplayEquityItemsSection() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertEquityItemsVisible();
        }

        @Test
        @DisplayName("Should display total assets")
        void shouldDisplayTotalAssets() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertTotalAssetsVisible();
        }

        @Test
        @DisplayName("Should display total liabilities")
        void shouldDisplayTotalLiabilities() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertTotalLiabilitiesVisible();
        }

        @Test
        @DisplayName("Should display total equity")
        void shouldDisplayTotalEquity() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertTotalEquityVisible();
        }

        @Test
        @DisplayName("Should display balance status")
        void shouldDisplayBalanceStatus() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertBalanceStatusVisible();
        }
    }

    @Nested
    @DisplayName("3.4 Balance Sheet Calculation")
    class BalanceSheetCalculationTests {

        @Test
        @DisplayName("Should show asset accounts with balances")
        void shouldShowAssetAccountsWithBalances() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Should have asset rows from test data
            int assetCount = balanceSheetPage.getAssetRowCount();
            assertThat(assetCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should show Cash account")
        void shouldShowCashAccount() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertAccountNameExists("Kas");
        }

        @Test
        @DisplayName("Should show Bank BCA account")
        void shouldShowBankBCAAccount() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertAccountNameExists("Bank BCA");
        }

        @Test
        @DisplayName("Should show Peralatan Komputer account")
        void shouldShowPeralatanKomputerAccount() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertAccountNameExists("Peralatan Komputer");
        }

        @Test
        @DisplayName("Should show liability accounts")
        void shouldShowLiabilityAccounts() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Should have at least Hutang Usaha
            int liabilityCount = balanceSheetPage.getLiabilityRowCount();
            assertThat(liabilityCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should show Hutang Usaha account")
        void shouldShowHutangUsahaAccount() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertAccountNameExists("Hutang Usaha");
        }

        @Test
        @DisplayName("Should show equity accounts")
        void shouldShowEquityAccounts() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Should have at least Modal Disetor
            int equityCount = balanceSheetPage.getEquityRowCount();
            assertThat(equityCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should show Modal Disetor account")
        void shouldShowModalDisetorAccount() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            balanceSheetPage.assertAccountNameExists("Modal Disetor");
        }

        @Test
        @DisplayName("Should show current year earnings")
        void shouldShowCurrentYearEarnings() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Current year earnings (includes V905 profitability test data)
            // V901: 33M (revenue 52M - expense 19M)
            // V905: +37M (revenue 55M - expense 18M)
            // Total: 70M
            balanceSheetPage.assertCurrentYearEarningsVisible();
            String earnings = balanceSheetPage.getCurrentYearEarningsText();
            assertThat(earnings).isEqualTo("70.000.000");
        }

        @Test
        @DisplayName("Should show expected total assets")
        void shouldShowExpectedTotalAssets() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Total Assets (includes V905 profitability test data)
            // V901: Cash 134M + BCA 40M + Peralatan 30M - Akum Peny 1M = 203M
            // V905: Cash -16M + BCA +53M = +37M
            // Total: 240M (Cash 118M + BCA 93M + Peralatan 30M - Akum Peny 1M)
            String totalAssets = balanceSheetPage.getTotalAssetsText();
            assertThat(totalAssets).isEqualTo("240.000.000");
        }

        @Test
        @DisplayName("Should show expected total liabilities")
        void shouldShowExpectedTotalLiabilities() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Total Liabilities = Hutang Usaha 10M (unchanged by V905)
            String totalLiabilities = balanceSheetPage.getTotalLiabilitiesText();
            assertThat(totalLiabilities).isEqualTo("10.000.000");
        }

        @Test
        @DisplayName("Should show expected total equity")
        void shouldShowExpectedTotalEquity() {
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Total Equity (includes V905 profitability test data)
            // V901: Modal 150M + Prior Year Retained 10M + Current Year NI 33M = 193M
            // V905: Current Year NI +37M
            // Total: 230M (Modal 150M + Prior 10M + Current 70M)
            String totalEquity = balanceSheetPage.getTotalEquityText();
            assertThat(totalEquity).isEqualTo("230.000.000");
        }
    }

    @Nested
    @DisplayName("3.5 Date Filter Functionality")
    class DateFilterTests {

        @Test
        @DisplayName("Should update report date in header when date changes")
        void shouldUpdateReportDateInHeader() {
            balanceSheetPage.navigateWithDate("2024-03-31");

            balanceSheetPage.assertReportDateContains("31 Maret 2024");
        }

        @Test
        @DisplayName("Should exclude VOID entries from calculation")
        void shouldExcludeVoidEntriesFromCalculation() {
            // VOID entry JRN-2024-0008 should not affect totals
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Total Assets should be 240M (VOID excluded, includes V905 data)
            String totalAssets = balanceSheetPage.getTotalAssetsText();
            assertThat(totalAssets).isEqualTo("240.000.000");
        }

        @Test
        @DisplayName("Should exclude DRAFT entries from calculation")
        void shouldExcludeDraftEntriesFromCalculation() {
            // DRAFT entry JRN-2024-0012 should not affect totals
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Total Assets should be 240M (DRAFT excluded, includes V905 data)
            String totalAssets = balanceSheetPage.getTotalAssetsText();
            assertThat(totalAssets).isEqualTo("240.000.000");
        }

        @Test
        @DisplayName("Should exclude future entries when date is filtered")
        void shouldExcludeFutureEntriesWhenDateFiltered() {
            // JRN-2024-0013 and JRN-2024-0014 are July 2024, should be excluded from June report
            balanceSheetPage.navigateWithDate("2024-06-30");

            // Total Assets should be 240M (future entries excluded, includes V905 data)
            String totalAssets = balanceSheetPage.getTotalAssetsText();
            assertThat(totalAssets).isEqualTo("240.000.000");
        }
    }
}
