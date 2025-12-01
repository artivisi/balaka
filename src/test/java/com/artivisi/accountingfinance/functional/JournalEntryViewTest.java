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

@DisplayName("Journal Entry - View Detail (Section 6)")
class JournalEntryViewTest extends PlaywrightTestBase {

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

    private JournalEntryInfo createDraftJournalEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-02-10");
        journalFormPage.setReferenceNumber("VIEW-TEST-001");
        journalFormPage.setDescription("Test Entry for View");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "1000000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "1000000");

        journalFormPage.clickSaveDraft();

        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        APIResponse response = page.context().request().get(baseUrl() + "/journals/api/" + journalNumber);
        String body = response.text();
        String id = extractJsonValue(body, "id");

        return new JournalEntryInfo(id, journalNumber);
    }

    private JournalEntryInfo createPostedJournalEntry() {
        journalFormPage.navigate();
        journalFormPage.waitForAlpineInit();

        journalFormPage.setJournalDate("2025-02-11");
        journalFormPage.setReferenceNumber("VIEW-POSTED-001");
        journalFormPage.setDescription("Test Posted Entry for View");

        journalFormPage.selectLineAccount(0, "1.1.01 - Kas");
        journalFormPage.setLineDebit(0, "2000000");

        journalFormPage.selectLineAccount(1, "4.1.01 - Pendapatan Jasa Konsultasi");
        journalFormPage.setLineCredit(1, "2000000");

        journalFormPage.clickSaveAndPost();

        page.waitForURL(url -> url.matches(".*/journals/JE-\\d{4}-\\d{4}$"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));

        String url = page.url();
        String journalNumber = url.substring(url.lastIndexOf("/") + 1);

        APIResponse response = page.context().request().get(baseUrl() + "/journals/api/" + journalNumber);
        String body = response.text();
        String id = extractJsonValue(body, "id");

        return new JournalEntryInfo(id, journalNumber);
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
    @DisplayName("6.1 Display Header Fields from DB")
    class HeaderFieldsTests {

        @Test
        @DisplayName("Should display journal number from database")
        void shouldDisplayJournalNumber() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertJournalNumberVisible();
            journalDetailPage.assertJournalNumberText(info.journalNumber());
        }

        @Test
        @DisplayName("Should display description from database")
        void shouldDisplayDescription() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertJournalDescriptionText("Test Entry for View");
        }

        @Test
        @DisplayName("Should display journal date from database")
        void shouldDisplayJournalDate() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertJournalDateContains("2025");
        }

        @Test
        @DisplayName("Should display reference number from database")
        void shouldDisplayReferenceNumber() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertReferenceNumberText("VIEW-TEST-001");
        }
    }

    @Nested
    @DisplayName("6.2 Display Journal Lines")
    class JournalLinesTests {

        @Test
        @DisplayName("Should display all journal lines")
        void shouldDisplayJournalLines() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertJournalLinesVisible();
            journalDetailPage.assertJournalLineCount(2);
        }

        @Test
        @DisplayName("Should display calculated totals")
        void shouldDisplayCalculatedTotals() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertTotalDebitText("1.000.000");
            journalDetailPage.assertTotalCreditText("1.000.000");
        }

        @Test
        @DisplayName("Should show balanced status for balanced entry")
        void shouldShowBalancedStatus() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertBalanceStatusVisible();
            journalDetailPage.assertBalanced();
        }
    }

    @Nested
    @DisplayName("6.3 Draft Entry View")
    class DraftEntryViewTests {

        @Test
        @DisplayName("Should show draft status badge")
        void shouldShowDraftStatusBadge() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertStatusBadgeVisible();
            journalDetailPage.assertStatusBadgeText("Draft");
        }

        @Test
        @DisplayName("Should show draft status banner")
        void shouldShowDraftStatusBanner() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertDraftBannerVisible();
            journalDetailPage.assertPostedBannerNotVisible();
            journalDetailPage.assertVoidBannerNotVisible();
        }

        @Test
        @DisplayName("Should show edit button for draft entry")
        void shouldShowEditButtonForDraft() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertEditButtonVisible();
        }

        @Test
        @DisplayName("Should show post button for draft entry")
        void shouldShowPostButtonForDraft() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertPostButtonVisible();
            journalDetailPage.assertPostButtonEnabled();
        }

        @Test
        @DisplayName("Should not show void button for draft entry")
        void shouldNotShowVoidButtonForDraft() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertVoidButtonNotVisible();
        }
    }

    @Nested
    @DisplayName("6.4 Posted Entry View")
    class PostedEntryViewTests {

        @Test
        @DisplayName("Should show posted status badge")
        void shouldShowPostedStatusBadge() {
            JournalEntryInfo info = createPostedJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertStatusBadgeVisible();
            journalDetailPage.assertStatusBadgeText("Posted");
        }

        @Test
        @DisplayName("Should show posted status banner")
        void shouldShowPostedStatusBanner() {
            JournalEntryInfo info = createPostedJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertPostedBannerVisible();
            journalDetailPage.assertDraftBannerNotVisible();
            journalDetailPage.assertVoidBannerNotVisible();
        }

        @Test
        @DisplayName("Should not show edit button for posted entry")
        void shouldNotShowEditButtonForPosted() {
            JournalEntryInfo info = createPostedJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertEditButtonNotVisible();
        }

        @Test
        @DisplayName("Should not show post button for posted entry")
        void shouldNotShowPostButtonForPosted() {
            JournalEntryInfo info = createPostedJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertPostButtonNotVisible();
        }

        @Test
        @DisplayName("Should show void button for posted entry")
        void shouldShowVoidButtonForPosted() {
            JournalEntryInfo info = createPostedJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertVoidButtonVisible();
        }

        @Test
        @DisplayName("Should show posted timestamp for posted entry")
        void shouldShowPostedTimestamp() {
            JournalEntryInfo info = createPostedJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertPostedAtVisible();
        }
    }

    @Nested
    @DisplayName("6.5 Account Impact Section")
    class AccountImpactTests {

        @Test
        @DisplayName("Should display account impact section")
        void shouldDisplayAccountImpactSection() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertAccountImpactSectionVisible();
        }

        @Test
        @DisplayName("Should display correct number of account impact rows")
        void shouldDisplayCorrectNumberOfRows() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            // Should have 2 rows - one for Kas and one for Pendapatan Jasa Konsultasi
            journalDetailPage.assertAccountImpactRowCount(2);
        }

        @Test
        @DisplayName("Should display accounts in impact section")
        void shouldDisplayAccountsInImpactSection() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            journalDetailPage.assertAccountImpactContainsAccount("1.1.01");
            journalDetailPage.assertAccountImpactContainsAccount("4.1.01");
        }

        @Test
        @DisplayName("Should display debit and credit movements")
        void shouldDisplayDebitAndCreditMovements() {
            JournalEntryInfo info = createDraftJournalEntry();

            journalDetailPage.navigate(info.journalNumber());

            // Kas has debit of 1,000,000
            journalDetailPage.assertAccountImpactRowHasDebitMovement("1.1.01", "1.000.000");
            journalDetailPage.assertAccountImpactRowHasCreditMovement("1.1.01", "-");

            // Pendapatan Jasa Konsultasi has credit of 1,000,000
            journalDetailPage.assertAccountImpactRowHasDebitMovement("4.1.01", "-");
            journalDetailPage.assertAccountImpactRowHasCreditMovement("4.1.01", "1.000.000");
        }
    }
}
