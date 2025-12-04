package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.DashboardPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Dashboard KPIs (Section 1.10)")
class DashboardKPITest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private DashboardPage dashboardPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        dashboardPage = new DashboardPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("1.10.1 Dashboard Page")
    class DashboardPageTests {

        @Test
        @DisplayName("Should display dashboard page title")
        void shouldDisplayDashboardPageTitle() {
            dashboardPage.navigate();

            dashboardPage.assertPageTitleVisible();
            dashboardPage.assertPageTitleText("Dashboard");
        }

        @Test
        @DisplayName("Should display KPI container")
        void shouldDisplayKPIContainer() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            dashboardPage.assertKPIContainerVisible();
        }
    }

    @Nested
    @DisplayName("1.10.2 KPI Cards")
    class KPICardsTests {

        @Test
        @DisplayName("Should display revenue card with value")
        void shouldDisplayRevenueCardWithValue() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            dashboardPage.assertRevenueCardVisible();
            assertThat(dashboardPage.hasRevenueValue()).isTrue();
        }

        @Test
        @DisplayName("Should display expense card with value")
        void shouldDisplayExpenseCardWithValue() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            dashboardPage.assertExpenseCardVisible();
            assertThat(dashboardPage.hasExpenseValue()).isTrue();
        }

        @Test
        @DisplayName("Should display profit card with value")
        void shouldDisplayProfitCardWithValue() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            dashboardPage.assertProfitCardVisible();
            assertThat(dashboardPage.hasProfitValue()).isTrue();
        }

        @Test
        @DisplayName("Should display percentage change indicators")
        void shouldDisplayPercentageChangeIndicators() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            assertThat(dashboardPage.hasPercentageChange()).isTrue();
        }
    }

    @Nested
    @DisplayName("1.10.3 Month Selector")
    class MonthSelectorTests {

        @Test
        @DisplayName("Should display month selector")
        void shouldDisplayMonthSelector() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            dashboardPage.assertMonthSelectorVisible();
        }

        @Test
        @DisplayName("Should display current month period")
        void shouldDisplayCurrentMonthPeriod() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            dashboardPage.assertKPIPeriodVisible();
            String periodText = dashboardPage.getKPIPeriodText();
            // Should contain year
            String currentYear = String.valueOf(YearMonth.now().getYear());
            assertThat(periodText).contains(currentYear);
        }

        @Test
        @DisplayName("Should update KPIs when month changes")
        void shouldUpdateKPIsWhenMonthChanges() {
            dashboardPage.navigate();
            dashboardPage.waitForKPIsToLoad();

            // Change to a specific month (June 2024 - has test data)
            dashboardPage.selectMonth("2024-06");

            // Verify period changed to June 2024
            String newPeriod = dashboardPage.getKPIPeriodText();
            assertThat(newPeriod).contains("2024");
            assertThat(newPeriod).containsIgnoringCase("juni");
        }
    }

    @Nested
    @DisplayName("1.10.4 HTMX Loading")
    class HTMXLoadingTests {

        @Test
        @DisplayName("Should load KPIs via HTMX on page load")
        void shouldLoadKPIsViaHTMXOnPageLoad() {
            dashboardPage.navigate();

            // Wait for HTMX to load the KPIs
            dashboardPage.waitForKPIsToLoad();

            // All KPI cards should be visible
            dashboardPage.assertRevenueCardVisible();
            dashboardPage.assertExpenseCardVisible();
            dashboardPage.assertProfitCardVisible();
        }
    }

    @Nested
    @DisplayName("1.10.5 Amortization Widget")
    class AmortizationWidgetTests {

        @Test
        @DisplayName("Should display amortization widget")
        void shouldDisplayAmortizationWidget() {
            dashboardPage.navigate();
            dashboardPage.waitForAmortizationWidgetToLoad();

            dashboardPage.assertAmortizationWidgetVisible();
            dashboardPage.assertAmortizationWidgetContentVisible();
        }

        @Test
        @DisplayName("Should display amortization widget title")
        void shouldDisplayAmortizationWidgetTitle() {
            dashboardPage.navigate();
            dashboardPage.waitForAmortizationWidgetToLoad();

            assertThat(dashboardPage.hasAmortizationTitle()).isTrue();
        }

        @Test
        @DisplayName("Should have link to amortization page")
        void shouldHaveLinkToAmortizationPage() {
            dashboardPage.navigate();
            dashboardPage.waitForAmortizationWidgetToLoad();

            assertThat(dashboardPage.hasAmortizationLink()).isTrue();
        }
    }
}
