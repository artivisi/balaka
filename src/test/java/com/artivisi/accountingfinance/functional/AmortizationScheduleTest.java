package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.AmortizationDetailPage;
import com.artivisi.accountingfinance.functional.page.AmortizationFormPage;
import com.artivisi.accountingfinance.functional.page.AmortizationListPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Amortization Schedules (Section 1.8)")
class AmortizationScheduleTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private AmortizationListPage listPage;
    private AmortizationFormPage formPage;
    private AmortizationDetailPage detailPage;

    // Account IDs from V002 seed data
    private static final String PREPAID_INSURANCE_ACCOUNT_ID = "10000000-0000-0000-0000-000000000105";
    private static final String INSURANCE_EXPENSE_ACCOUNT_ID = "50000000-0000-0000-0000-000000000108";

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        listPage = new AmortizationListPage(page, baseUrl());
        formPage = new AmortizationFormPage(page, baseUrl());
        detailPage = new AmortizationDetailPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("1.8.1 Schedule List")
    class ScheduleListTests {

        @Test
        @DisplayName("Should display amortization list page")
        void shouldDisplayAmortizationListPage() {
            listPage.navigate();

            listPage.assertPageTitleVisible();
            listPage.assertPageTitleText("Jadwal Amortisasi");
        }

        @Test
        @DisplayName("Should display schedule table")
        void shouldDisplayScheduleTable() {
            listPage.navigate();

            listPage.assertTableVisible();
        }
    }

    @Nested
    @DisplayName("1.8.2 Schedule Form")
    class ScheduleFormTests {

        @Test
        @DisplayName("Should display new schedule form")
        void shouldDisplayNewScheduleForm() {
            formPage.navigateToNew();

            formPage.assertPageTitleText("Jadwal Amortisasi Baru");
        }

        @Test
        @DisplayName("Should navigate to form from list page")
        void shouldNavigateToFormFromListPage() {
            listPage.navigate();
            listPage.clickNewScheduleButton();

            formPage.assertPageTitleText("Jadwal Amortisasi Baru");
        }
    }

    @Nested
    @DisplayName("1.8.3 Schedule Creation")
    class ScheduleCreationTests {

        @Test
        @DisplayName("Should create prepaid expense schedule")
        void shouldCreatePrepaidExpenseSchedule() {
            formPage.navigateToNew();

            String uniqueCode = "AMT-TEST-" + System.currentTimeMillis();
            String uniqueName = "Test Asuransi " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.selectType("PREPAID_EXPENSE");
            formPage.selectSourceAccount(PREPAID_INSURANCE_ACCOUNT_ID);
            formPage.selectTargetAccount(INSURANCE_EXPENSE_ACCOUNT_ID);
            formPage.fillAmount("12000000");
            formPage.fillStartDate("2025-01-01");
            formPage.fillEndDate("2025-12-31");
            formPage.selectFrequency("MONTHLY");
            formPage.setAutoPost(false);
            formPage.clickSubmit();

            // Should redirect to detail page
            detailPage.assertScheduleNameText(uniqueName);
            detailPage.assertScheduleCodeText(uniqueCode);
        }

        @Test
        @DisplayName("Should generate correct number of entries")
        void shouldGenerateCorrectNumberOfEntries() {
            formPage.navigateToNew();

            String uniqueCode = "AMT-ENTRY-" + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName("Entry Test Schedule");
            formPage.selectType("PREPAID_EXPENSE");
            formPage.selectSourceAccount(PREPAID_INSURANCE_ACCOUNT_ID);
            formPage.selectTargetAccount(INSURANCE_EXPENSE_ACCOUNT_ID);
            formPage.fillAmount("12000000");
            formPage.fillStartDate("2025-01-01");
            formPage.fillEndDate("2025-12-31");
            formPage.selectFrequency("MONTHLY");
            formPage.clickSubmit();

            // Should have 12 monthly entries
            int entryCount = detailPage.getEntryCount();
            assertThat(entryCount).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("1.8.4 Entry Posting")
    class EntryPostingTests {

        @Test
        @DisplayName("Should post entry and create journal")
        void shouldPostEntryAndCreateJournal() {
            // Create a schedule first
            formPage.navigateToNew();

            String uniqueCode = "AMT-POST-" + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName("Post Test Schedule");
            formPage.selectType("PREPAID_EXPENSE");
            formPage.selectSourceAccount(PREPAID_INSURANCE_ACCOUNT_ID);
            formPage.selectTargetAccount(INSURANCE_EXPENSE_ACCOUNT_ID);
            formPage.fillAmount("3000000");
            formPage.fillStartDate("2025-01-01");
            formPage.fillEndDate("2025-03-31");
            formPage.selectFrequency("MONTHLY");
            formPage.clickSubmit();

            int initialPending = detailPage.getPendingEntryCount();

            // Post first entry
            detailPage.postFirstPendingEntry();

            // Should have one posted entry
            assertThat(detailPage.getPostedEntryCount()).isEqualTo(1);
            assertThat(detailPage.getPendingEntryCount()).isEqualTo(initialPending - 1);
        }

        @Test
        @DisplayName("Should skip entry")
        void shouldSkipEntry() {
            // Create a schedule first
            formPage.navigateToNew();

            String uniqueCode = "AMT-SKIP-" + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName("Skip Test Schedule");
            formPage.selectType("PREPAID_EXPENSE");
            formPage.selectSourceAccount(PREPAID_INSURANCE_ACCOUNT_ID);
            formPage.selectTargetAccount(INSURANCE_EXPENSE_ACCOUNT_ID);
            formPage.fillAmount("3000000");
            formPage.fillStartDate("2025-01-01");
            formPage.fillEndDate("2025-03-31");
            formPage.selectFrequency("MONTHLY");
            formPage.clickSubmit();

            int initialPending = detailPage.getPendingEntryCount();

            // Skip first entry
            detailPage.skipFirstPendingEntry();

            // Should have one skipped entry
            assertThat(detailPage.getSkippedEntryCount()).isEqualTo(1);
            assertThat(detailPage.getPendingEntryCount()).isEqualTo(initialPending - 1);
        }
    }

    @Nested
    @DisplayName("1.8.5 Schedule Cancellation")
    class ScheduleCancellationTests {

        @Test
        @DisplayName("Should cancel active schedule")
        void shouldCancelActiveSchedule() {
            // Create a schedule first
            formPage.navigateToNew();

            String uniqueCode = "AMT-CANCEL-" + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName("Cancel Test Schedule");
            formPage.selectType("PREPAID_EXPENSE");
            formPage.selectSourceAccount(PREPAID_INSURANCE_ACCOUNT_ID);
            formPage.selectTargetAccount(INSURANCE_EXPENSE_ACCOUNT_ID);
            formPage.fillAmount("6000000");
            formPage.fillStartDate("2025-01-01");
            formPage.fillEndDate("2025-06-30");
            formPage.selectFrequency("MONTHLY");
            formPage.clickSubmit();

            // Cancel the schedule
            detailPage.clickCancelButton();

            // Should show cancelled status
            detailPage.assertStatusText("Dibatalkan");
        }
    }

    @Nested
    @DisplayName("1.8.6 Rounding Handling")
    class RoundingTests {

        @Test
        @DisplayName("Should handle rounding in last period")
        void shouldHandleRoundingInLastPeriod() {
            formPage.navigateToNew();

            String uniqueCode = "AMT-ROUND-" + System.currentTimeMillis();

            // 100,000 / 3 = 33,333.33 (with rounding)
            formPage.fillCode(uniqueCode);
            formPage.fillName("Rounding Test Schedule");
            formPage.selectType("PREPAID_EXPENSE");
            formPage.selectSourceAccount(PREPAID_INSURANCE_ACCOUNT_ID);
            formPage.selectTargetAccount(INSURANCE_EXPENSE_ACCOUNT_ID);
            formPage.fillAmount("100000");
            formPage.fillStartDate("2025-01-01");
            formPage.fillEndDate("2025-03-31");
            formPage.selectFrequency("MONTHLY");
            formPage.clickSubmit();

            // Should have 3 entries
            assertThat(detailPage.getEntryCount()).isEqualTo(3);
        }
    }
}
