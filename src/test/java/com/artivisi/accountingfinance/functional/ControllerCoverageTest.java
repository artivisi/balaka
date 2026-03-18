package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for controller coverage improvement.
 * Tests About, DataExport, FiscalPeriod, and Pph21Calculator controllers.
 */
@DisplayName("Controller Coverage Tests")
@Import(ServiceTestDataInitializer.class)
class ControllerCoverageTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("About Controller")
    class AboutControllerTests {

        @Test
        @DisplayName("Should display about page with version info")
        void shouldDisplayAboutPage() {
            navigateTo("/about");
            waitForPageLoad();

            assertThat(page.title())
                .as("Page title should contain 'Tentang'")
                .contains("Tentang");

            assertThat(page.locator("#about-page-content").isVisible())
                .as("About page content should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should show git commit ID")
        void shouldShowGitCommitId() {
            navigateTo("/about");
            waitForPageLoad();

            assertThat(page.locator("#commit-id").isVisible())
                .as("Commit ID should be visible")
                .isTrue();

            String commitId = page.locator("#commit-id").textContent();
            assertThat(commitId)
                .as("Commit ID should not be empty")
                .isNotEmpty();
        }

        @Test
        @DisplayName("Should show version info section")
        void shouldShowVersionInfoSection() {
            navigateTo("/about");
            waitForPageLoad();

            assertThat(page.locator("#version-info").isVisible())
                .as("Version info section should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display application name")
        void shouldDisplayApplicationName() {
            navigateTo("/about");
            waitForPageLoad();

            assertThat(page.locator("text=Balaka").isVisible())
                .as("Application name should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should have link back to settings")
        void shouldHaveLinkBackToSettings() {
            navigateTo("/about");
            waitForPageLoad();

            assertThat(page.locator("text=Kembali ke Pengaturan").isVisible())
                .as("Back to settings link should be visible")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Data Export Controller")
    class DataExportControllerTests {

        @Test
        @DisplayName("Should display export page")
        void shouldDisplayExportPage() {
            navigateTo("/settings/export");
            waitForPageLoad();

            assertThat(page.title())
                .as("Page title should contain 'Ekspor'")
                .contains("Ekspor");

            assertThat(page.locator("#export-content").isVisible())
                .as("Export page content should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should show export statistics")
        void shouldShowExportStatistics() {
            navigateTo("/settings/export");
            waitForPageLoad();

            assertThat(page.locator("#stat-account").isVisible())
                .as("Account statistics should be visible")
                .isTrue();

            assertThat(page.locator("#stat-journal").isVisible())
                .as("Journal statistics should be visible")
                .isTrue();

            assertThat(page.locator("#stat-transaction").isVisible())
                .as("Transaction statistics should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should have export button")
        void shouldHaveExportButton() {
            navigateTo("/settings/export");
            waitForPageLoad();

            assertThat(page.locator("#btn-export").isVisible())
                .as("Export button should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should have back link to settings")
        void shouldHaveBackLinkToSettings() {
            navigateTo("/settings/export");
            waitForPageLoad();

            assertThat(page.locator("#link-back-to-settings").isVisible())
                .as("Back to settings link should be visible")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Fiscal Period Controller")
    class FiscalPeriodControllerTests {

        @Test
        @DisplayName("Should display fiscal periods list")
        void shouldDisplayFiscalPeriodsList() {
            navigateTo("/fiscal-periods");
            waitForPageLoad();

            assertThat(page.title())
                .as("Page title should contain 'Periode Fiskal'")
                .contains("Periode Fiskal");
        }

        @Test
        @DisplayName("Should have new period button")
        void shouldHaveNewPeriodButton() {
            navigateTo("/fiscal-periods");
            waitForPageLoad();

            assertThat(page.locator("#btn-new-period").isVisible())
                .as("New period button should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should have generate year button")
        void shouldHaveGenerateYearButton() {
            navigateTo("/fiscal-periods");
            waitForPageLoad();

            assertThat(page.locator("#btn-generate-year").isVisible())
                .as("Generate year button should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display new period form")
        void shouldDisplayNewPeriodForm() {
            navigateTo("/fiscal-periods/new");
            waitForPageLoad();

            assertThat(page.locator("input[name='year']").isVisible())
                .as("Year input should be visible")
                .isTrue();

            assertThat(page.locator("select[name='month']").isVisible())
                .as("Month select should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should create new fiscal period")
        void shouldCreateNewFiscalPeriod() {
            navigateTo("/fiscal-periods/new");
            waitForPageLoad();

            // Use a year that doesn't have periods yet (future year)
            page.locator("input[name='year']").fill("2030");
            page.locator("select[name='month']").selectOption("7");

            page.click("button[type='submit']");
            waitForPageLoad();

            // Should redirect to period detail or list
            assertThat(page.url())
                .as("Should navigate away from new form")
                .doesNotContain("/new");
        }

        @Test
        @DisplayName("Should generate fiscal year")
        void shouldGenerateFiscalYear() {
            navigateTo("/fiscal-periods");
            waitForPageLoad();

            // Change year input to 2031 to avoid conflicts
            page.locator("input[name='year']").fill("2031");
            page.locator("#btn-generate-year").click();
            waitForPageLoad();

            // Should show success or info message
            assertThat(page.url())
                .as("Should redirect to fiscal periods list")
                .contains("/fiscal-periods");
        }
    }

    @Nested
    @DisplayName("PPh 21 Calculator Controller (TER Method)")
    class Pph21CalculatorControllerTests {

        @Test
        @DisplayName("Should display calculator form")
        void shouldDisplayCalculatorForm() {
            navigateTo("/pph21-calculator");
            waitForPageLoad();

            assertThat(page.title())
                .as("Page title should contain 'Kalkulator PPh 21'")
                .contains("Kalkulator PPh 21");

            assertThat(page.locator("#salary").isVisible())
                .as("Salary input should be visible")
                .isTrue();

            assertThat(page.locator("#ptkpStatus").isVisible())
                .as("PTKP status select should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should calculate PPh 21 using TER method")
        void shouldCalculatePph21UsingTer() {
            navigateTo("/pph21-calculator");
            waitForPageLoad();

            page.locator("#salary").fill("10000000");
            page.locator("#ptkpStatus").selectOption("TK_0");
            page.locator("#btn-calculate").click();
            waitForPageLoad();

            assertThat(page.getByTestId("gross-income").isVisible())
                .as("Gross income should be displayed")
                .isTrue();

            assertThat(page.getByTestId("ter-category").isVisible())
                .as("TER category should be displayed")
                .isTrue();

            assertThat(page.getByTestId("ter-rate").isVisible())
                .as("TER rate should be displayed")
                .isTrue();

            assertThat(page.getByTestId("monthly-pph21").isVisible())
                .as("Monthly PPh 21 should be displayed")
                .isTrue();
        }

        @Test
        @DisplayName("Should show correct TER category for K_2")
        void shouldShowCorrectTerCategoryForK2() {
            navigateTo("/pph21-calculator");
            waitForPageLoad();

            page.locator("#salary").fill("11253000");
            page.locator("#ptkpStatus").selectOption("K_2");
            page.locator("#btn-calculate").click();
            waitForPageLoad();

            assertThat(page.getByTestId("ter-category").isVisible())
                .as("TER category should be visible")
                .isTrue();

            assertThat(page.getByTestId("ter-category").textContent())
                .as("K_2 should map to TER Category B")
                .contains("B");

            assertThat(page.getByTestId("ter-rate").textContent())
                .as("TER rate for Cat B, 11.253M should be 2.50%")
                .contains("2.50%");
        }

        @Test
        @DisplayName("Should show zero PPh 21 for low salary")
        void shouldShowZeroPph21ForLowSalary() {
            navigateTo("/pph21-calculator");
            waitForPageLoad();

            page.locator("#salary").fill("5000000");
            page.locator("#ptkpStatus").selectOption("K_2");
            page.locator("#btn-calculate").click();
            waitForPageLoad();

            assertThat(page.getByTestId("ter-rate").textContent())
                .as("TER rate should be 0% for low salary Category B")
                .contains("0.00%");
        }

        @Test
        @DisplayName("Should preserve form values after calculation")
        void shouldPreserveFormValuesAfterCalculation() {
            navigateTo("/pph21-calculator");
            waitForPageLoad();

            page.locator("#salary").fill("25000000");
            page.locator("#ptkpStatus").selectOption("K_2");
            page.locator("#btn-calculate").click();
            waitForPageLoad();

            assertThat(page.locator("#salary").inputValue())
                .as("Salary should be preserved")
                .isEqualTo("25000000");

            assertThat(page.locator("#ptkpStatus").inputValue())
                .as("PTKP status should be preserved")
                .isEqualTo("K_2");
        }
    }
}
