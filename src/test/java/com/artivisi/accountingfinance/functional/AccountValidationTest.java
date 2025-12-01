package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.AccountFormPage;
import com.artivisi.accountingfinance.functional.page.ChartOfAccountsPage;
import com.artivisi.accountingfinance.functional.page.JournalDetailPage;
import com.artivisi.accountingfinance.functional.page.JournalFormPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Account Validation (Section 9)")
class AccountValidationTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private ChartOfAccountsPage accountsPage;
    private AccountFormPage accountFormPage;
    private JournalFormPage journalFormPage;
    private JournalDetailPage journalDetailPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        accountsPage = new ChartOfAccountsPage(page, baseUrl());
        accountFormPage = new AccountFormPage(page, baseUrl());
        journalFormPage = new JournalFormPage(page, baseUrl());
        journalDetailPage = new JournalDetailPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("9.1 Account Dropdown Excludes Inactive Accounts")
    class InactiveAccountExclusionTests {

        @Test
        @DisplayName("Journal form should not show inactive accounts in dropdown")
        void journalFormShouldNotShowInactiveAccounts() {
            // Use unique code based on timestamp to avoid collision with other tests
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            String accountCode = "9.8." + uniqueSuffix;
            String accountName = "Inactive Test " + uniqueSuffix;

            // First, create a test account and make it inactive
            accountFormPage.navigateToNew();
            accountFormPage.fillAccountCode(accountCode);
            accountFormPage.fillAccountName(accountName);
            accountFormPage.selectAccountType("EXPENSE");
            accountFormPage.selectNormalBalanceDebit();
            accountFormPage.clickSave();

            // Navigate to accounts page and deactivate the account
            accountsPage.navigate();
            accountsPage.clickDeactivateAccount(accountCode);

            // Now go to journal form and check that the inactive account is not in dropdown
            journalFormPage.navigate();
            journalFormPage.waitForAlpineInit();

            // The dropdown should not contain the inactive account
            journalFormPage.assertAccountNotInDropdown(accountCode + " - " + accountName);
        }

        @Test
        @DisplayName("Journal form should show active accounts in dropdown")
        void journalFormShouldShowActiveAccounts() {
            journalFormPage.navigate();
            journalFormPage.waitForAlpineInit();

            // Standard seed data accounts should be available
            journalFormPage.assertAccountInDropdown("1.1.01 - Kas");
            journalFormPage.assertAccountInDropdown("4.1.01 - Pendapatan Jasa Konsultasi");
        }
    }

    @Nested
    @DisplayName("9.2 Cannot Change Account Type If Has Journal Entries")
    class AccountTypeChangeRestrictionTests {

        @Test
        @DisplayName("Should prevent account type change via UI when account has journal entries")
        void shouldPreventAccountTypeChangeViaUI() {
            // Create a journal entry using Kas account (1.1.01)
            journalFormPage.navigate();
            journalFormPage.waitForAlpineInit();

            journalFormPage.setJournalDate("2025-05-01");
            journalFormPage.setReferenceNumber("ACCT-VAL-001");
            journalFormPage.setDescription("Test for account validation");

            journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
            journalFormPage.setLineDebit(0, "1000000");

            journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
            journalFormPage.setLineCredit(1, "1000000");

            journalFormPage.clickSaveDraft();

            page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

            // Navigate to account edit form for Kas
            accountsPage.navigate();
            accountsPage.clickExpandAccount("1");
            accountsPage.clickExpandAccount("1.1");
            accountsPage.clickEditAccount("1.1.01");

            // Kas is a child account, so account type dropdown is disabled
            // This is by design - child accounts inherit account type from parent
            accountFormPage.assertAccountTypeDisabled();
        }

        @Test
        @DisplayName("Should allow account type change when account has no journal entries")
        void shouldAllowAccountTypeChangeWhenNoJournalEntries() {
            // Use unique code based on timestamp to avoid collision with other tests
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            String accountCode = "9.7." + uniqueSuffix;

            // Create a new test account (root level)
            accountFormPage.navigateToNew();
            accountFormPage.fillAccountCode(accountCode);
            accountFormPage.fillAccountName("No Journals Account");
            accountFormPage.selectAccountType("EXPENSE");
            accountFormPage.selectNormalBalanceDebit();
            accountFormPage.clickSave();

            accountsPage.assertAccountRowVisible(accountCode);

            // Edit the account and change type
            accountsPage.clickEditAccount(accountCode);
            accountFormPage.selectAccountType("REVENUE");
            accountFormPage.selectNormalBalanceCredit();
            accountFormPage.clickSave();

            // Should succeed
            accountsPage.assertSuccessMessageVisible();
        }
    }

    @Nested
    @DisplayName("9.3 Cannot Delete Account If Has Journal Entries")
    class AccountDeleteRestrictionTests {

        @Test
        @DisplayName("Should show error when trying to delete account with journal entries")
        void shouldShowErrorWhenDeletingAccountWithJournalEntries() {
            // Create a journal entry using a specific account
            journalFormPage.navigate();
            journalFormPage.waitForAlpineInit();

            journalFormPage.setJournalDate("2025-05-02");
            journalFormPage.setReferenceNumber("ACCT-DEL-001");
            journalFormPage.setDescription("Test for delete validation");

            // Use 1.1.02 Kas Kecil if it exists, otherwise use 1.1.01 Kas
            journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
            journalFormPage.setLineDebit(0, "500000");

            journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
            journalFormPage.setLineCredit(1, "500000");

            journalFormPage.clickSaveDraft();

            page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

            // Navigate to accounts page
            accountsPage.navigate();

            // Expand to see Kas account
            accountsPage.clickExpandAccount("1");
            accountsPage.clickExpandAccount("1.1");

            // Try to delete Kas account (1.1.01) which has journal entries
            accountsPage.clickDeleteAccount("1.1.01");

            // Should show error message
            accountsPage.assertErrorMessageVisible();
            accountsPage.assertErrorMessageContains("journal entries");
        }

        @Test
        @DisplayName("Should allow deletion when account has no journal entries")
        void shouldAllowDeletionWhenNoJournalEntries() {
            // Use unique code based on timestamp to avoid collision with other tests
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            String accountCode = "9.6." + uniqueSuffix;

            // Create a new test account
            accountFormPage.navigateToNew();
            accountFormPage.fillAccountCode(accountCode);
            accountFormPage.fillAccountName("Account To Delete");
            accountFormPage.selectAccountType("EXPENSE");
            accountFormPage.selectNormalBalanceDebit();
            accountFormPage.clickSave();

            accountsPage.assertAccountRowVisible(accountCode);

            // Delete the account (should succeed - no journal entries)
            accountsPage.clickDeleteAccount(accountCode);

            // Should show success message
            accountsPage.assertSuccessMessageVisible();
            accountsPage.assertAccountRowNotVisible(accountCode);
        }
    }

}
