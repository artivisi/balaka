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

@DisplayName("Journal Entry - Void (Section 8)")
class JournalEntryVoidTest extends PlaywrightTestBase {

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

    private JournalEntryInfo createAndPostJournalEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-04-01");
        journalFormPage.setReferenceNumber("VOID-TEST-001");
        journalFormPage.setDescription("Test Entry for Voiding");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "3000000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "3000000");

        journalFormPage.clickSaveAndPost();

        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        return new JournalEntryInfo(journalNumber, journalNumber);
    }

    private JournalEntryInfo createDraftJournalEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-04-02");
        journalFormPage.setReferenceNumber("VOID-DRAFT-001");
        journalFormPage.setDescription("Draft Entry - Cannot Void");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "2000000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "2000000");

        journalFormPage.clickSaveDraft();

        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        return new JournalEntryInfo(journalNumber, journalNumber);
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
    @DisplayName("8.1 Void Button Visibility")
    class VoidButtonVisibilityTests {

        @Test
        @DisplayName("Void button should be visible on posted entry")
        void voidButtonVisibleOnPosted() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());

            journalDetailPage.assertVoidButtonVisible();
        }

        @Test
        @DisplayName("Void button should not be visible on draft entry")
        void voidButtonNotVisibleOnDraft() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.id());

            journalDetailPage.assertVoidButtonNotVisible();
        }
    }

    @Nested
    @DisplayName("8.2 Void Dialog")
    class VoidDialogTests {

        @Test
        @DisplayName("Clicking void button should show void dialog")
        void clickingVoidShowsDialog() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();

            journalDetailPage.assertVoidDialogVisible();
        }

        @Test
        @DisplayName("Void dialog should have reason input")
        void voidDialogHasReasonInput() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();

            journalDetailPage.assertVoidDialogVisible();
            // The dialog is visible, reason input should be accessible
            journalDetailPage.fillVoidReason("Test reason");
        }

        @Test
        @DisplayName("Canceling void dialog should close without voiding")
        void cancelingVoidClosesDialog() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.assertVoidDialogVisible();

            journalDetailPage.cancelVoid();

            journalDetailPage.assertVoidDialogNotVisible();
            journalDetailPage.assertStatusBadgeText("Posted");
        }
    }

    @Nested
    @DisplayName("8.3 Void Execution")
    class VoidExecutionTests {

        @Test
        @DisplayName("Confirming void should change status to VOID")
        void confirmingVoidChangesStatus() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Kesalahan input data");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            journalDetailPage.assertStatusBadgeText("Void");
        }

        @Test
        @DisplayName("Voided entry should show voided timestamp")
        void voidedEntryShowsTimestamp() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Salah jurnal");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            journalDetailPage.assertVoidedAtVisible();
        }

        @Test
        @DisplayName("Voided entry should show void status banner")
        void voidedEntryShowsBanner() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Pembatalan transaksi");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            journalDetailPage.assertVoidBannerVisible();
            journalDetailPage.assertPostedBannerNotVisible();
            journalDetailPage.assertDraftBannerNotVisible();
        }

        @Test
        @DisplayName("Voided entry should display void reason")
        void voidedEntryShowsReason() {
            JournalEntryInfo info = createAndPostJournalEntry();

            String voidReason = "Koreksi kesalahan pencatatan";

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason(voidReason);
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            // Verify the void reason is displayed in the banner
            journalDetailPage.assertVoidBannerContainsReason(voidReason);
        }
    }

    @Nested
    @DisplayName("8.4 Void Validation")
    class VoidValidationTests {

        @Test
        @DisplayName("Voided entry should not show void button")
        void voidedEntryNoVoidButton() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Test void");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            journalDetailPage.assertVoidButtonNotVisible();
        }

        @Test
        @DisplayName("Voided entry should not show edit button")
        void voidedEntryNoEditButton() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Test void");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            journalDetailPage.assertEditButtonNotVisible();
        }

        @Test
        @DisplayName("Voided entry should not show post button")
        void voidedEntryNoPostButton() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());
            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Test void");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            journalDetailPage.assertPostButtonNotVisible();
        }

        @Test
        @DisplayName("Original entry data preserved after void")
        void originalEntryPreservedAfterVoid() {
            JournalEntryInfo info = createAndPostJournalEntry();

            journalDetailPage.navigate(info.id());

            // Capture original data
            String originalJournalNumber = journalDetailPage.getJournalNumber();
            String originalDescription = journalDetailPage.getJournalDescription();

            journalDetailPage.clickVoidButton();
            journalDetailPage.fillVoidReason("Audit trail test");
            journalDetailPage.confirmVoid();

            page.waitForLoadState();

            // Verify original data is still displayed (audit trail)
            journalDetailPage.assertJournalNumberText(originalJournalNumber);
            journalDetailPage.assertJournalDescriptionText(originalDescription);
            journalDetailPage.assertJournalLinesVisible();
        }
    }
}
