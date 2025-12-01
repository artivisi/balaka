package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.JournalDetailPage;
import com.artivisi.accountingfinance.functional.page.JournalFormPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Journal Entry - Post (Section 7)")
class JournalEntryPostTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private JournalFormPage journalFormPage;
    private JournalDetailPage journalDetailPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        journalFormPage = new JournalFormPage(page, baseUrl());
        journalDetailPage = new JournalDetailPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    private record JournalEntryInfo(String id, String journalNumber) {}

    private JournalEntryInfo createBalancedDraftEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-03-01");
        journalFormPage.setReferenceNumber("POST-TEST-001");
        journalFormPage.setDescription("Test Entry for Posting");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "5000000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "5000000");

        journalFormPage.clickSaveDraft();

        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        return new JournalEntryInfo(journalNumber, journalNumber);
    }

    private JournalEntryInfo createUnbalancedDraftEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-03-02");
        journalFormPage.setReferenceNumber("POST-UNBAL-001");
        journalFormPage.setDescription("Unbalanced Entry for Testing");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "5000000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "3000000");

        // Save using API to bypass client-side validation
        APIResponse response = page.context().request().post(
                baseUrl() + "/journals/api",
                com.microsoft.playwright.options.RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData("{\"journalDate\":\"2024-03-02\",\"referenceNumber\":\"POST-UNBAL-001\"," +
                                "\"description\":\"Unbalanced Entry for Testing\",\"postImmediately\":false," +
                                "\"lines\":[{\"accountId\":null,\"debit\":5000000,\"credit\":0}," +
                                "{\"accountId\":null,\"debit\":0,\"credit\":3000000}]}")
        );

        // This will fail due to balance validation, so let's use a different approach
        // Instead, we'll test that the post button is disabled for unbalanced entries
        // by creating a draft first, then checking the API validation
        return null;
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    @Nested
    @DisplayName("7.1 Post Button Visibility")
    class PostButtonTests {

        @Test
        @DisplayName("Post button should be visible on draft entry")
        void postButtonVisibleOnDraft() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());

            journalDetailPage.assertPostButtonVisible();
        }

        @Test
        @DisplayName("Post button should be enabled for balanced entry")
        void postButtonEnabledForBalanced() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());

            journalDetailPage.assertPostButtonEnabled();
        }
    }

    @Nested
    @DisplayName("7.2 Confirmation Dialog")
    class ConfirmationDialogTests {

        @Test
        @DisplayName("Clicking post button should show confirmation dialog")
        void clickingPostShowsDialog() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();

            journalDetailPage.assertPostDialogVisible();
        }

        @Test
        @DisplayName("Confirmation dialog should have warning message")
        void confirmationDialogHasWarning() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();

            journalDetailPage.assertPostDialogContainsText("tidak dapat diubah");
        }

        @Test
        @DisplayName("Canceling dialog should close it without posting")
        void cancelingDialogClosesWithoutPosting() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.assertPostDialogVisible();

            journalDetailPage.cancelPost();

            journalDetailPage.assertPostDialogNotVisible();
            journalDetailPage.assertStatusBadgeText("Draft");
        }
    }

    @Nested
    @DisplayName("7.3 Post Execution")
    class PostExecutionTests {

        @Test
        @DisplayName("Confirming post should change status to POSTED")
        void confirmingPostChangesStatus() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            journalDetailPage.assertStatusBadgeText("Posted");
        }

        @Test
        @DisplayName("Posted entry should show posted timestamp")
        void postedEntryShowsTimestamp() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            journalDetailPage.assertPostedAtVisible();
        }

        @Test
        @DisplayName("Posted entry should show posted status banner")
        void postedEntryShowsBanner() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            journalDetailPage.assertPostedBannerVisible();
            journalDetailPage.assertDraftBannerNotVisible();
        }
    }

    @Nested
    @DisplayName("7.4 Immutability After Posting")
    class ImmutabilityTests {

        @Test
        @DisplayName("Posted entry should not show edit button")
        void postedEntryNoEditButton() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            journalDetailPage.assertEditButtonNotVisible();
        }

        @Test
        @DisplayName("Posted entry should not show post button")
        void postedEntryNoPostButton() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            journalDetailPage.assertPostButtonNotVisible();
        }

        @Test
        @DisplayName("Posted entry should show void button")
        void postedEntryShowsVoidButton() {
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            journalDetailPage.assertVoidButtonVisible();
        }
    }

    @Nested
    @DisplayName("7.5 Balance Validation")
    class BalanceValidationTests {

        @Test
        @DisplayName("Post button should be disabled for unbalanced entry in UI")
        void postButtonDisabledForUnbalanced() {
            // Create a draft entry via form
            journalFormPage.navigate();
            journalFormPage.waitForAlpineInit();

            journalFormPage.setJournalDate("2025-03-03");
            journalFormPage.setReferenceNumber("UNBAL-UI-001");
            journalFormPage.setDescription("Unbalanced Entry UI Test");

            journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
            journalFormPage.setLineDebit(0, "5000000");

            journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
            journalFormPage.setLineCredit(1, "3000000"); // Unbalanced

            // Save & Post button should be disabled for unbalanced entry
            journalFormPage.assertSavePostButtonDisabled();
        }

        @Test
        @DisplayName("Posted entry cannot be posted again via UI")
        void postedEntryCannotBePostedAgainViaUI() {
            // Create and post a balanced entry
            JournalEntryInfo info = createBalancedDraftEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickPostButton();
            journalDetailPage.confirmPost();

            page.waitForLoadState();

            // Verify post button is no longer visible
            journalDetailPage.assertPostButtonNotVisible();

            // Verify status is Posted
            journalDetailPage.assertStatusBadgeText("Posted");
        }
    }
}
