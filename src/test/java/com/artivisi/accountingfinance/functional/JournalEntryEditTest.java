package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.JournalFormPage;
import com.artivisi.accountingfinance.functional.page.JournalListPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Journal Entry - Edit (Section 5)")
class JournalEntryEditTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private JournalListPage journalListPage;
    private JournalFormPage journalFormPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        journalListPage = new JournalListPage(page, baseUrl());
        journalFormPage = new JournalFormPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    private record JournalEntryInfo(String id, String journalNumber) {}

    private JournalEntryInfo createDraftJournalEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-01-15");
        journalFormPage.setReferenceNumber("TEST-EDIT-001");
        journalFormPage.setDescription("Test Entry for Edit");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "500000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "500000");

        journalFormPage.clickSaveDraft();

        // Wait for redirect to detail page with journal number
        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        return new JournalEntryInfo(journalNumber, journalNumber);
    }

    private JournalEntryInfo createPostedJournalEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-01-16");
        journalFormPage.setReferenceNumber("TEST-POSTED-001");
        journalFormPage.setDescription("Test Posted Entry");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "750000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "750000");

        journalFormPage.clickSaveAndPost();

        // Wait for redirect to detail page with journal number
        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        return new JournalEntryInfo(journalNumber, journalNumber);
    }

    private String extractJsonValue(String json, String key) {
        // Simple JSON value extraction for "key":"value"
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    @Nested
    @DisplayName("5.1 Navigation to Edit Form")
    class NavigationTests {

        @Test
        @DisplayName("Should navigate to edit form for draft entry")
        void shouldNavigateToEditFormForDraftEntry() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());

            assertThat(page.url()).contains("/journals/" + info.journalNumber() + "/edit");
            journalFormPage.assertPageTitleVisible();
            journalFormPage.assertPageTitleText("Edit Jurnal");
        }

        @Test
        @DisplayName("Should redirect to detail when trying to edit posted entry")
        void shouldRedirectToDetailForPostedEntry() {
            JournalEntryInfo info = createPostedJournalEntry();

            page.navigate(baseUrl() + "/journals/" + info.journalNumber() + "/edit");
            page.waitForLoadState();

            // Should redirect to detail page, not edit page
            assertThat(page.url()).doesNotContain("/edit");
        }
    }

    @Nested
    @DisplayName("5.2 Form Displays Existing Data")
    class FormDataDisplayTests {

        @Test
        @DisplayName("Should populate journal date from existing entry")
        void shouldPopulateJournalDate() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.assertJournalDateValue("2025-01-15");
        }

        @Test
        @DisplayName("Should populate description from existing entry")
        void shouldPopulateDescription() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.assertDescriptionValue("Test Entry for Edit");
        }

        @Test
        @DisplayName("Should populate journal lines from existing entry")
        void shouldPopulateJournalLines() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            // Verify correct number of lines
            journalFormPage.assertLineCount(2);

            // Verify totals (order-independent)
            journalFormPage.assertTotalDebitText("500.000");
            journalFormPage.assertTotalCreditText("500.000");
            journalFormPage.assertBalanced();
        }

        @Test
        @DisplayName("Should show balanced status for existing balanced entry")
        void shouldShowBalancedStatus() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.assertBalanced();
        }
    }

    @Nested
    @DisplayName("5.3 Modify and Save")
    class ModifyAndSaveTests {

        @Test
        @DisplayName("Should allow modifying description")
        void shouldAllowModifyingDescription() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.setDescription("Updated Description");

            String newDescription = journalFormPage.getDescription();
            assertThat(newDescription).isEqualTo("Updated Description");
        }

        @Test
        @DisplayName("Should allow modifying amounts")
        void shouldAllowModifyingAmounts() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.setLineDebit(0, "600000");
            journalFormPage.setLineCredit(1, "600000");

            journalFormPage.assertTotalDebitText("600.000");
            journalFormPage.assertTotalCreditText("600.000");
            journalFormPage.assertBalanced();
        }

        @Test
        @DisplayName("Should save updated draft entry")
        void shouldSaveUpdatedDraftEntry() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            // Update description only (amount changes are order-dependent)
            journalFormPage.setDescription("Updated via Edit");

            // Click save and wait for redirect
            journalFormPage.clickSaveDraft();

            // Wait for redirect to detail page with journal number
            page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

            // Navigate back to edit to verify changes persisted
            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            // Verify description was updated
            journalFormPage.assertDescriptionValue("Updated via Edit");

            // Verify total amounts are still correct (500000 each)
            journalFormPage.assertTotalDebitText("500.000");
            journalFormPage.assertTotalCreditText("500.000");
        }

        @Test
        @DisplayName("Should allow posting from edit form")
        void shouldAllowPostingFromEditForm() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.clickSaveAndPost();

            // Wait for redirect to detail page with journal number
            page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

            // Try to edit again - should redirect since it's now posted
            page.navigate(baseUrl() + "/journals/" + info.journalNumber() + "/edit");
            page.waitForLoadState();

            assertThat(page.url()).doesNotContain("/edit");
        }
    }

    @Nested
    @DisplayName("5.4 Add and Remove Lines in Edit Mode")
    class LinesManagementTests {

        @Test
        @DisplayName("Should allow adding new line in edit mode")
        void shouldAllowAddingNewLine() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.assertLineCount(2);

            journalFormPage.clickAddLine();

            journalFormPage.assertLineCount(3);
        }

        @Test
        @DisplayName("Should allow removing line in edit mode")
        void shouldAllowRemovingLine() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalFormPage.navigateToEdit(info.journalNumber());
            journalFormPage.waitForAlpineInit();

            journalFormPage.clickAddLine();
            journalFormPage.assertLineCount(3);

            journalFormPage.removeLineAt(2);
            journalFormPage.assertLineCount(2);
        }
    }
}
