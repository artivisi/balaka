package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Period Report Tests")
@Import(ServiceTestDataInitializer.class)
class PeriodReportTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display period report page with period selector and options")
    void shouldDisplayPeriodReportPage() {
        navigateTo("/reports/period");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Periode");
        assertThat(page.locator("#period")).isVisible();
        assertThat(page.locator("#btn-generate")).isVisible();

        // Verify period options include yearly and monthly entries
        assertThat(page.locator("#period option[value='2025']")).hasText("Tahun 2025");
        assertThat(page.locator("#period option[value='2025-01']")).hasText("  Januari 2025");
        assertThat(page.locator("#period option[value='2025-12']")).hasText("  Desember 2025");
    }

    @Test
    @DisplayName("Should generate yearly report with income statement and balance sheet")
    void shouldGenerateYearlyReport() {
        navigateTo("/reports/period?period=2025");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Periode");
        assertThat(page.locator("#period")).hasValue("2025");
        assertThat(page.locator("text=LAPORAN LABA RUGI")).isVisible();
        assertThat(page.locator("text=LAPORAN POSISI KEUANGAN")).isVisible();
    }

    @Test
    @DisplayName("Should generate monthly report with income statement and balance sheet")
    void shouldGenerateMonthlyReport() {
        navigateTo("/reports/period?period=2025-01");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Periode");
        assertThat(page.locator("#period")).hasValue("2025-01");
        assertThat(page.locator("text=LAPORAN LABA RUGI")).isVisible();
        assertThat(page.locator("text=LAPORAN POSISI KEUANGAN")).isVisible();
    }

    @Test
    @DisplayName("Should show empty state when no period is selected")
    void shouldShowEmptyStateWhenNoPeriodSelected() {
        navigateTo("/reports/period");
        waitForPageLoad();

        assertThat(page.locator("text=Pilih periode untuk menampilkan laporan keuangan")).isVisible();
        assertThat(page.locator("text=LAPORAN LABA RUGI")).not().isVisible();
    }
}
