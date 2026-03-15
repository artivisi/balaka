package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.taxdetail.TaxDetailTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for SPT Tahunan Badan checklist page.
 * Uses TaxDetailTestDataInitializer to have transactions with revenue/expense accounts.
 */
@DisplayName("SPT Tahunan Badan Checklist Tests")
@Import(TaxDetailTestDataInitializer.class)
class SptChecklistFunctionalTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("Checklist Page Display")
    class ChecklistPageDisplayTests {

        @Test
        @DisplayName("Should display SPT checklist page with title")
        void shouldDisplaySptChecklistPage() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            assertThat(page.title())
                .as("Page title should contain 'Checklist SPT Tahunan'")
                .contains("Checklist SPT Tahunan");

            assertThat(page.locator("#page-title").textContent())
                .as("Page heading should show Checklist SPT Tahunan Badan")
                .contains("Checklist SPT Tahunan Badan");

            takeManualScreenshot("spt-checklist/spt-checklist-page");
        }

        @Test
        @DisplayName("Should display year selection form")
        void shouldDisplayYearSelectionForm() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            assertThat(page.locator("#year").isVisible())
                .as("Year select should be visible")
                .isTrue();

            assertThat(page.locator("button:has-text('Tampilkan')").isVisible())
                .as("Submit button should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display all 7 checklist items")
        void shouldDisplayAllChecklistItems() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            assertThat(page.locator(".font-medium:text-is('Laporan Keuangan')").isVisible())
                .as("Financial statements checklist item should be visible")
                .isTrue();

            assertThat(page.locator(".font-medium:text-is('Koreksi Fiskal')").isVisible())
                .as("Fiscal adjustments checklist item should be visible")
                .isTrue();

            assertThat(page.locator(".font-medium:text-is('PPh Badan Terutang')").isVisible())
                .as("PPh Badan checklist item should be visible")
                .isTrue();

            assertThat(page.locator(".font-medium:text-is('Periode Fiskal')").isVisible())
                .as("Fiscal periods checklist item should be visible")
                .isTrue();

            assertThat(page.locator(".font-medium:text-is('Penyusutan Aset Tetap')").isVisible())
                .as("Depreciation checklist item should be visible")
                .isTrue();

            assertThat(page.locator(".font-medium:text-is('Payroll & PPh 21')").isVisible())
                .as("Payroll checklist item should be visible")
                .isTrue();

            assertThat(page.locator(".font-medium:text-is('Kompensasi Kerugian')").isVisible())
                .as("Loss carryforward checklist item should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display readiness heading with year")
        void shouldDisplayReadinessHeading() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            int expectedYear = Year.now().getValue() - 1;

            assertThat(page.locator("text=Kesiapan Data SPT Tahunan").first().isVisible())
                .as("Readiness heading should be visible")
                .isTrue();

            assertThat(page.locator("h2").first().textContent())
                .as("Heading should contain the target year")
                .contains(String.valueOf(expectedYear));
        }
    }

    @Nested
    @DisplayName("Download Section")
    class DownloadSectionTests {

        @Test
        @DisplayName("Should display all 6 download cards")
        void shouldDisplayAllDownloadCards() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            assertThat(page.locator("text=L1 — Rekonsiliasi Fiskal").isVisible())
                .as("L1 download card should be visible")
                .isTrue();

            assertThat(page.locator("text=L4 — Penghasilan Final").isVisible())
                .as("L4 download card should be visible")
                .isTrue();

            assertThat(page.locator("text=L9 — Penyusutan").first().isVisible())
                .as("L9 download card should be visible")
                .isTrue();

            assertThat(page.locator("text=Transkrip 8A").first().isVisible())
                .as("Transkrip 8A download card should be visible")
                .isTrue();

            assertThat(page.locator("text=BPA1 — e-Bupot PPh 21").isVisible())
                .as("BPA1 download card should be visible")
                .isTrue();

            assertThat(page.locator("text=Rekonsiliasi Fiskal (detail)").isVisible())
                .as("Rekonsiliasi Fiskal download card should be visible")
                .isTrue();

            takeManualScreenshot("spt-checklist/spt-checklist-downloads");
        }

        @Test
        @DisplayName("Should have correct L1 download link")
        void shouldHaveCorrectL1DownloadLink() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            int expectedYear = Year.now().getValue() - 1;
            var link = page.locator("a[href*='spt-tahunan/l1']");

            assertThat(link.isVisible())
                .as("L1 download link should be visible")
                .isTrue();

            assertThat(link.getAttribute("href"))
                .as("L1 link should include year and format=excel")
                .contains("year=" + expectedYear)
                .contains("format=excel");
        }

        @Test
        @DisplayName("Should have correct BPA1 download link")
        void shouldHaveCorrectBpa1DownloadLink() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            int expectedYear = Year.now().getValue() - 1;
            var link = page.locator("a[href*='ebupot-pph21']");

            assertThat(link.isVisible())
                .as("BPA1 download link should be visible")
                .isTrue();

            assertThat(link.getAttribute("href"))
                .as("BPA1 link should include year and format=excel")
                .contains("year=" + expectedYear)
                .contains("format=excel");
        }
    }

    @Nested
    @DisplayName("Year Selection")
    class YearSelectionTests {

        @Test
        @DisplayName("Should default to previous year")
        void shouldDefaultToPreviousYear() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            int expectedYear = Year.now().getValue() - 1;

            assertThat(page.url())
                .as("Default page should load without year param (defaults to previous year)")
                .contains("/reports/spt-checklist");

            assertThat(page.locator("h2").first().textContent())
                .as("Heading should show previous year")
                .contains(String.valueOf(expectedYear));
        }

        @Test
        @DisplayName("Should change year on form submit")
        void shouldChangeYearOnSubmit() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            int twoYearsAgo = Year.now().getValue() - 2;

            page.locator("#year").selectOption(String.valueOf(twoYearsAgo));
            page.locator("button:has-text('Tampilkan')").click();
            waitForPageLoad();

            assertThat(page.url())
                .as("URL should contain selected year")
                .contains("year=" + twoYearsAgo);

            assertThat(page.locator("h2").first().textContent())
                .as("Heading should show selected year")
                .contains(String.valueOf(twoYearsAgo));
        }

        @Test
        @DisplayName("Should have 3 years available")
        void shouldHaveThreeYearsAvailable() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            var options = page.locator("#year option").all();

            assertThat(options)
                .as("Should have 3 years available")
                .hasSize(3);
        }
    }

    @Nested
    @DisplayName("API Info Section")
    class ApiInfoTests {

        @Test
        @DisplayName("Should display API info footer")
        void shouldDisplayApiInfoFooter() {
            navigateTo("/reports/spt-checklist");
            waitForPageLoad();

            assertThat(page.locator("text=/api/tax-export/spt-tahunan/").isVisible())
                .as("API info should mention the SPT Tahunan API path")
                .isTrue();

            assertThat(page.locator("text=format=excel").isVisible())
                .as("API info should mention format=excel parameter")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Navigation")
    class NavigationTests {

        @Test
        @DisplayName("Should navigate to SPT checklist from sidebar")
        void shouldNavigateFromSidebar() {
            navigateTo("/dashboard");
            waitForPageLoad();

            // Expand the "Laporan" sidebar group (collapsed <details> by default)
            page.locator("#nav-group-laporan").click();

            var sptLink = page.locator("a[href*='/reports/spt-checklist']").first();

            assertThat(sptLink.isVisible())
                .as("SPT Tahunan sidebar link should be visible")
                .isTrue();

            sptLink.click();
            waitForPageLoad();

            assertThat(page.url())
                .as("Should navigate to SPT checklist page")
                .contains("/reports/spt-checklist");
        }
    }
}
