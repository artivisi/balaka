package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * /about page (AboutController): version details read from git.properties.
 * Data export, fiscal period and PPh 21 calculator pages are covered by
 * DataExportControllerFunctionalTest, FiscalPeriodControllerFunctionalTest and
 * Pph21CalculatorFunctionalTest.
 */
@DisplayName("About Controller Tests")
@Import(ServiceTestDataInitializer.class)
class AboutControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Should show the build's abbreviated git commit and tag from git.properties with a link back to settings")
    void shouldShowBuildVersion() {
        String commitId = environment.getRequiredProperty("git.commit.id.abbrev");
        String tags = environment.getRequiredProperty("git.tags");

        loginAsAdmin();
        navigateTo("/about");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Tentang Aplikasi");
        assertThat(page.locator("#commit-id")).hasText(commitId);
        if (tags.isBlank()) {
            assertThat(page.locator("#git-tag")).hasCount(0);
        } else {
            assertThat(page.locator("#git-tag")).hasText(tags);
        }
        assertThat(page.locator("#about-page-content a:has-text('Kembali ke Pengaturan')"))
                .hasAttribute("href", "/settings");
    }
}
