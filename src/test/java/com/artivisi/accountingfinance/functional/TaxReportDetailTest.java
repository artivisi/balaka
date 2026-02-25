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
 * Functional tests for Phase 12.7 Tax Report Detail pages:
 * - PPN Detail (per-faktur)
 * - PPh 23 Detail (per-bupot)
 * - PPN Cross-check (faktur vs ledger)
 * - Rekonsiliasi Fiskal (fiscal reconciliation + PPh Badan)
 */
@DisplayName("Tax Report Detail Tests")
@Import(ServiceTestDataInitializer.class)
class TaxReportDetailTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("Report Index Links")
    class ReportIndexTests {

        @Test
        @DisplayName("Should display PPN detail link in report index")
        void shouldDisplayPpnDetailLink() {
            navigateTo("/reports");
            waitForPageLoad();

            assertThat(page.locator("a[href*='ppn-detail']").first().isVisible())
                .as("PPN detail link should be visible")
                .isTrue();
            assertThat(page.locator("text=Rincian PPN").first().isVisible())
                .as("PPN detail label should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display PPh 23 detail link in report index")
        void shouldDisplayPph23DetailLink() {
            navigateTo("/reports");
            waitForPageLoad();

            assertThat(page.locator("a[href*='pph23-detail']").first().isVisible())
                .as("PPh 23 detail link should be visible")
                .isTrue();
            assertThat(page.locator("text=Rincian PPh 23").first().isVisible())
                .as("PPh 23 detail label should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display PPN crosscheck link in report index")
        void shouldDisplayPpnCrosscheckLink() {
            navigateTo("/reports");
            waitForPageLoad();

            assertThat(page.locator("a[href*='ppn-crosscheck']").first().isVisible())
                .as("PPN crosscheck link should be visible")
                .isTrue();
            assertThat(page.locator("text=Cross-check PPN").first().isVisible())
                .as("PPN crosscheck label should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display rekonsiliasi fiskal link in report index")
        void shouldDisplayRekonsiliasiFiskalLink() {
            navigateTo("/reports");
            waitForPageLoad();

            assertThat(page.locator("a[href*='rekonsiliasi-fiskal']").first().isVisible())
                .as("Rekonsiliasi fiskal link should be visible")
                .isTrue();
            assertThat(page.locator("text=Rekonsiliasi Fiskal").first().isVisible())
                .as("Rekonsiliasi fiskal label should be visible")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("PPN Detail Report")
    class PPNDetailTests {

        @Test
        @DisplayName("Should display PPN detail page with title and filter form")
        void shouldDisplayPpnDetailPage() {
            navigateTo("/reports/ppn-detail");
            waitForPageLoad();

            assertThat(page.title()).contains("Rincian PPN");
            assertThat(page.locator("#page-title").textContent()).contains("Rincian PPN");
            assertThat(page.locator("#startDate").isVisible()).isTrue();
            assertThat(page.locator("#endDate").isVisible()).isTrue();
            assertThat(page.locator("#btn-generate").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display PPN keluaran and masukan sections")
        void shouldDisplayPpnSections() {
            navigateTo("/reports/ppn-detail?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#section-keluaran").isVisible())
                .as("Keluaran section should be visible")
                .isTrue();
            assertThat(page.locator("#section-masukan").isVisible())
                .as("Masukan section should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display export buttons")
        void shouldDisplayExportButtons() {
            navigateTo("/reports/ppn-detail?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#btn-export-pdf").isVisible())
                .as("PDF export button should be visible")
                .isTrue();
            assertThat(page.locator("#btn-export-excel").isVisible())
                .as("Excel export button should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display totals for keluaran and masukan")
        void shouldDisplayTotals() {
            navigateTo("/reports/ppn-detail?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#total-dpp-keluaran").isVisible())
                .as("Total DPP keluaran should be visible")
                .isTrue();
            assertThat(page.locator("#total-ppn-keluaran").isVisible())
                .as("Total PPN keluaran should be visible")
                .isTrue();
            assertThat(page.locator("#total-dpp-masukan").isVisible())
                .as("Total DPP masukan should be visible")
                .isTrue();
            assertThat(page.locator("#total-ppn-masukan").isVisible())
                .as("Total PPN masukan should be visible")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("PPh 23 Detail Report")
    class PPh23DetailTests {

        @Test
        @DisplayName("Should display PPh 23 detail page with title and filter form")
        void shouldDisplayPph23DetailPage() {
            navigateTo("/reports/pph23-detail");
            waitForPageLoad();

            assertThat(page.title()).contains("Rincian PPh 23");
            assertThat(page.locator("#page-title").textContent()).contains("Rincian PPh 23");
            assertThat(page.locator("#startDate").isVisible()).isTrue();
            assertThat(page.locator("#endDate").isVisible()).isTrue();
            assertThat(page.locator("#btn-generate").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display PPh 23 report with table and totals")
        void shouldDisplayPph23Report() {
            navigateTo("/reports/pph23-detail?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#report-title").isVisible())
                .as("Report title should be visible")
                .isTrue();
            assertThat(page.locator("#total-gross").isVisible())
                .as("Total gross should be visible")
                .isTrue();
            assertThat(page.locator("#total-tax").isVisible())
                .as("Total tax should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display export buttons for PPh 23")
        void shouldDisplayExportButtons() {
            navigateTo("/reports/pph23-detail?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#btn-export-pdf").isVisible()).isTrue();
            assertThat(page.locator("#btn-export-excel").isVisible()).isTrue();
        }
    }

    @Nested
    @DisplayName("PPN Cross-check Report")
    class PPNCrossCheckTests {

        @Test
        @DisplayName("Should display PPN crosscheck page with title and filter form")
        void shouldDisplayPpnCrosscheckPage() {
            navigateTo("/reports/ppn-crosscheck");
            waitForPageLoad();

            assertThat(page.title()).contains("Cross-check PPN");
            assertThat(page.locator("#page-title").textContent()).contains("Cross-check PPN");
            assertThat(page.locator("#startDate").isVisible()).isTrue();
            assertThat(page.locator("#endDate").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display comparison data and status")
        void shouldDisplayComparisonData() {
            navigateTo("/reports/ppn-crosscheck?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#faktur-ppn-keluaran").isVisible())
                .as("Faktur PPN keluaran should be visible")
                .isTrue();
            assertThat(page.locator("#ledger-ppn-keluaran").isVisible())
                .as("Ledger PPN keluaran should be visible")
                .isTrue();
            assertThat(page.locator("#keluaran-diff").isVisible())
                .as("Keluaran difference should be visible")
                .isTrue();
            assertThat(page.locator("#crosscheck-status").isVisible())
                .as("Crosscheck status should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display export buttons for crosscheck")
        void shouldDisplayExportButtons() {
            navigateTo("/reports/ppn-crosscheck?startDate=2025-01-01&endDate=2025-12-31");
            waitForPageLoad();

            assertThat(page.locator("#btn-export-pdf").isVisible()).isTrue();
            assertThat(page.locator("#btn-export-excel").isVisible()).isTrue();
        }
    }

    @Nested
    @DisplayName("Rekonsiliasi Fiskal Report")
    class RekonsiliasiFiskalTests {

        @Test
        @DisplayName("Should display rekonsiliasi fiskal page with title and year filter")
        void shouldDisplayRekonsiliasiFiskalPage() {
            navigateTo("/reports/rekonsiliasi-fiskal");
            waitForPageLoad();

            assertThat(page.title()).contains("Rekonsiliasi Fiskal");
            assertThat(page.locator("#page-title").textContent()).contains("Rekonsiliasi Fiskal");
            assertThat(page.locator("#year").isVisible()).isTrue();
            assertThat(page.locator("#btn-generate").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display commercial P&L, adjustments, and PPh Badan sections")
        void shouldDisplayAllSections() {
            navigateTo("/reports/rekonsiliasi-fiskal?year=2025");
            waitForPageLoad();

            assertThat(page.locator("#total-revenue").isVisible())
                .as("Total revenue should be visible")
                .isTrue();
            assertThat(page.locator("#total-expense").isVisible())
                .as("Total expense should be visible")
                .isTrue();
            assertThat(page.locator("#commercial-net-income").isVisible())
                .as("Commercial net income should be visible")
                .isTrue();
            assertThat(page.locator("#pkp").isVisible())
                .as("PKP should be visible")
                .isTrue();
            assertThat(page.locator("#pph-terutang").isVisible())
                .as("PPh terutang should be visible")
                .isTrue();
            assertThat(page.locator("#pph29").isVisible())
                .as("PPh 29 should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display add adjustment form")
        void shouldDisplayAddAdjustmentForm() {
            navigateTo("/reports/rekonsiliasi-fiskal?year=2025");
            waitForPageLoad();

            assertThat(page.locator("#add-adjustment-form").isVisible())
                .as("Add adjustment form should be visible")
                .isTrue();
            assertThat(page.locator("#adj-description").isVisible())
                .as("Description field should be visible")
                .isTrue();
            assertThat(page.locator("#adj-category").isVisible())
                .as("Category select should be visible")
                .isTrue();
            assertThat(page.locator("#adj-direction").isVisible())
                .as("Direction select should be visible")
                .isTrue();
            assertThat(page.locator("#adj-amount").isVisible())
                .as("Amount field should be visible")
                .isTrue();
            assertThat(page.locator("#btn-add-adjustment").isVisible())
                .as("Add button should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should add fiscal adjustment and display it")
        void shouldAddFiscalAdjustment() {
            navigateTo("/reports/rekonsiliasi-fiskal?year=2025");
            waitForPageLoad();

            // Fill in the adjustment form
            page.locator("#adj-description").fill("Biaya entertainment tanpa daftar nominatif");
            page.locator("#adj-category").selectOption("PERMANENT");
            page.locator("#adj-direction").selectOption("POSITIVE");
            page.locator("#adj-amount").fill("5000000");

            // Submit
            page.locator("#btn-add-adjustment").click();
            waitForPageLoad();

            // Verify adjustment appears in the list
            assertThat(page.locator("#adjustment-items").textContent())
                .as("Adjustment list should contain the added item")
                .contains("Biaya entertainment tanpa daftar nominatif");

            // Verify totals updated
            assertThat(page.locator("#total-positive").textContent())
                .as("Positive total should include the new adjustment")
                .contains("5.000.000");
        }

        @Test
        @DisplayName("Should delete fiscal adjustment")
        void shouldDeleteFiscalAdjustment() {
            // First add an adjustment
            navigateTo("/reports/rekonsiliasi-fiskal?year=2025");
            waitForPageLoad();

            page.locator("#adj-description").fill("Penyusutan fiskal vs komersial");
            page.locator("#adj-category").selectOption("TEMPORARY");
            page.locator("#adj-direction").selectOption("NEGATIVE");
            page.locator("#adj-amount").fill("3000000");
            page.locator("#btn-add-adjustment").click();
            waitForPageLoad();

            // Verify it was added
            assertThat(page.locator("#adjustment-items").textContent())
                .contains("Penyusutan fiskal vs komersial");

            // Delete it
            page.onDialog(dialog -> dialog.accept());
            page.locator("#adjustment-items button:has-text('Hapus')").last().click();
            waitForPageLoad();

            // Verify it was deleted
            assertThat(page.locator("#adjustment-items").textContent())
                .doesNotContain("Penyusutan fiskal vs komersial");
        }

        @Test
        @DisplayName("Should display kredit pajak details")
        void shouldDisplayKreditPajakDetails() {
            navigateTo("/reports/rekonsiliasi-fiskal?year=2025");
            waitForPageLoad();

            assertThat(page.locator("#kredit-pph23").isVisible())
                .as("Kredit PPh 23 should be visible")
                .isTrue();
            assertThat(page.locator("#kredit-pph25").isVisible())
                .as("Kredit PPh 25 should be visible")
                .isTrue();
            assertThat(page.locator("#total-kredit").isVisible())
                .as("Total kredit pajak should be visible")
                .isTrue();
        }

        @Test
        @DisplayName("Should display export buttons for rekonsiliasi fiskal")
        void shouldDisplayExportButtons() {
            navigateTo("/reports/rekonsiliasi-fiskal?year=2025");
            waitForPageLoad();

            assertThat(page.locator("#btn-export-pdf").isVisible()).isTrue();
            assertThat(page.locator("#btn-export-excel").isVisible()).isTrue();
        }
    }
}
