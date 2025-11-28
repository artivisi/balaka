package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.functional.page.TaxCalendarListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tax Calendar (Section 2.8)")
class TaxCalendarTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private TaxCalendarListPage listPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        listPage = new TaxCalendarListPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("2.8.1 Tax Calendar List")
    class TaxCalendarListTests {

        @Test
        @DisplayName("Should display tax calendar page")
        void shouldDisplayTaxCalendarPage() {
            listPage.navigate();

            listPage.assertPageTitleVisible();
            listPage.assertPageTitleText("Kalender Pajak");
        }

        @Test
        @DisplayName("Should display summary cards")
        void shouldDisplaySummaryCards() {
            listPage.navigate();

            listPage.assertSummaryCardsVisible();
        }

        @Test
        @DisplayName("Should display checklist")
        void shouldDisplayChecklist() {
            listPage.navigate();

            listPage.assertChecklistVisible();
        }

        @Test
        @DisplayName("Should show all tax deadlines from seed data")
        void shouldShowAllTaxDeadlines() {
            listPage.navigate();

            // Should show all 8 tax deadlines from seed data
            int totalCount = listPage.getTotalCount();
            assertThat(totalCount).isEqualTo(8);
        }

        @Test
        @DisplayName("Should display PPh 21 deadline")
        void shouldDisplayPph21Deadline() {
            listPage.navigate();

            assertThat(listPage.hasDeadlineWithName("Setor PPh 21")).isTrue();
        }

        @Test
        @DisplayName("Should display PPN deadline")
        void shouldDisplayPpnDeadline() {
            listPage.navigate();

            assertThat(listPage.hasDeadlineWithName("Setor PPN")).isTrue();
        }
    }

    @Nested
    @DisplayName("2.8.2 Tax Calendar Period Selection")
    class TaxCalendarPeriodTests {

        @Test
        @DisplayName("Should navigate to specific period")
        void shouldNavigateToSpecificPeriod() {
            listPage.navigateWithPeriod(2025, 1);

            // Should show Januari 2025 in the checklist header
            String content = listPage.getChecklistContent();
            assertThat(content).contains("Januari").contains("2025");
        }

        @Test
        @DisplayName("Should change period via month selector")
        void shouldChangePeriodViaMonthSelector() {
            listPage.navigateWithPeriod(2025, 1);
            listPage.selectMonth(6);

            // Should show Juni in the checklist
            String content = listPage.getChecklistContent();
            assertThat(content).contains("Juni");
        }

        @Test
        @DisplayName("Should change period via year selector")
        void shouldChangePeriodViaYearSelector() {
            listPage.navigateWithPeriod(2025, 1);
            listPage.selectYear(2026);

            // Should show 2026 in the checklist
            String content = listPage.getChecklistContent();
            assertThat(content).contains("2026");
        }
    }

    @Nested
    @DisplayName("2.8.3 Mark Tax Deadline Complete")
    class TaxCalendarCompleteTests {

        @Test
        @DisplayName("Should show mark complete button for incomplete deadline")
        void shouldShowMarkCompleteButton() {
            listPage.navigateWithPeriod(2099, 1);

            // Should have at least one mark complete button (deadlines are not completed for future periods)
            String content = listPage.getChecklistContent();
            assertThat(content).contains("Tandai Selesai");
        }

        @Test
        @DisplayName("Should show initial zero completed count for new period")
        void shouldShowZeroCompletedForNewPeriod() {
            listPage.navigateWithPeriod(2099, 1);

            // For a fresh period in the future, completed should be 0
            int completedCount = listPage.getCompletedCount();
            assertThat(completedCount).isEqualTo(0);
        }
    }
}
