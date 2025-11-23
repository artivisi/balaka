package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.ChartOfAccountsPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Chart of Accounts - Seed Data Verification")
class ChartOfAccountSeedDataTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private ChartOfAccountsPage accountsPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        accountsPage = new ChartOfAccountsPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Test
    @DisplayName("Should display page title and add button")
    void shouldDisplayPageTitleAndAddButton() {
        accountsPage.navigate();

        accountsPage.assertPageTitleVisible();
        accountsPage.assertPageTitleText("Bagan Akun");
        accountsPage.assertAddButtonVisible();
    }

    @Test
    @DisplayName("Should display accounts table")
    void shouldDisplayAccountsTable() {
        accountsPage.navigate();

        accountsPage.assertAccountsTableVisible();
    }

    @Test
    @DisplayName("Should display all root account rows from seed data")
    void shouldDisplayAllRootAccountRows() {
        accountsPage.navigate();

        accountsPage.assertAllRootAccountsVisible();
    }

    @Test
    @DisplayName("Should display all root accounts with correct names")
    void shouldDisplayAllRootAccountsWithNames() {
        accountsPage.navigate();

        accountsPage.assertRootAccountsWithNames();
    }

    @Test
    @DisplayName("Should display all root accounts with correct type badges")
    void shouldDisplayAllRootAccountsWithTypes() {
        accountsPage.navigate();

        accountsPage.assertRootAccountsWithTypes();
    }

    @Test
    @DisplayName("Should display ASET account with expand button")
    void shouldDisplayAsetWithExpandButton() {
        accountsPage.navigate();

        accountsPage.assertAccountRowVisible("1");
        accountsPage.assertAccountNameVisible("1", "ASET");
        accountsPage.assertAccountTypeVisible("1", "Aset");
        accountsPage.assertExpandButtonVisible("1");
    }

    @Test
    @DisplayName("Should display LIABILITAS account with expand button")
    void shouldDisplayLiabilitasWithExpandButton() {
        accountsPage.navigate();

        accountsPage.assertAccountRowVisible("2");
        accountsPage.assertAccountNameVisible("2", "LIABILITAS");
        accountsPage.assertAccountTypeVisible("2", "Liabilitas");
        accountsPage.assertExpandButtonVisible("2");
    }

    @Test
    @DisplayName("Should display EKUITAS account with expand button")
    void shouldDisplayEkuitasWithExpandButton() {
        accountsPage.navigate();

        accountsPage.assertAccountRowVisible("3");
        accountsPage.assertAccountNameVisible("3", "EKUITAS");
        accountsPage.assertAccountTypeVisible("3", "Ekuitas");
        accountsPage.assertExpandButtonVisible("3");
    }

    @Test
    @DisplayName("Should display PENDAPATAN account with expand button")
    void shouldDisplayPendapatanWithExpandButton() {
        accountsPage.navigate();

        accountsPage.assertAccountRowVisible("4");
        accountsPage.assertAccountNameVisible("4", "PENDAPATAN");
        accountsPage.assertAccountTypeVisible("4", "Pendapatan");
        accountsPage.assertExpandButtonVisible("4");
    }

    @Test
    @DisplayName("Should display BEBAN account with expand button")
    void shouldDisplayBebanWithExpandButton() {
        accountsPage.navigate();

        accountsPage.assertAccountRowVisible("5");
        accountsPage.assertAccountNameVisible("5", "BEBAN");
        accountsPage.assertAccountTypeVisible("5", "Beban");
        accountsPage.assertExpandButtonVisible("5");
    }

    @Test
    @DisplayName("Should expand ASET and show child accounts")
    void shouldExpandAsetAndShowChildAccounts() {
        accountsPage.navigate();

        accountsPage.clickExpandAccount("1");

        // Verify child accounts are visible after expanding
        accountsPage.assertAccountRowVisible("1.1");
        accountsPage.assertAccountNameVisible("1.1", "Aset Lancar");
        accountsPage.assertAccountRowVisible("1.2");
        accountsPage.assertAccountNameVisible("1.2", "Aset Tetap");
    }

    @Test
    @DisplayName("Should expand ASET then Aset Lancar to show leaf accounts")
    void shouldExpandToShowLeafAccounts() {
        accountsPage.navigate();

        // Expand ASET
        accountsPage.clickExpandAccount("1");
        accountsPage.assertAccountRowVisible("1.1");

        // Expand Aset Lancar
        accountsPage.clickExpandAccount("1.1");

        // Verify leaf accounts
        accountsPage.assertAccountRowVisible("1.1.01");
        accountsPage.assertAccountNameVisible("1.1.01", "Kas");
        accountsPage.assertAccountRowVisible("1.1.02");
        accountsPage.assertAccountNameVisible("1.1.02", "Bank BCA");
    }

    @Test
    @DisplayName("Should have edit button on each account")
    void shouldHaveEditButtonOnAccounts() {
        accountsPage.navigate();

        // Root accounts should have edit buttons
        accountsPage.assertEditButtonVisible("1");
        accountsPage.assertEditButtonVisible("2");
        accountsPage.assertEditButtonVisible("3");
        accountsPage.assertEditButtonVisible("4");
        accountsPage.assertEditButtonVisible("5");
    }
}
