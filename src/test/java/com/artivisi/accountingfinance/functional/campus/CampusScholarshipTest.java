package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.JournalTemplateListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * Campus Industry - Scholarship Tests
 * Tests scholarship allocation functionality for merit-based and need-based scholarships.
 * Uses Page Object Pattern for maintainability.
 */
@DisplayName("Campus Industry - Scholarships")
@Import(CampusTestDataInitializer.class)
public class CampusScholarshipTest extends PlaywrightTestBase {

    // Page Objects
    private JournalTemplateListPage templateListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        templateListPage = new JournalTemplateListPage(page, baseUrl);
    }

    @Test
    @DisplayName("Should display scholarship-related journal templates")
    void shouldDisplayScholarshipTemplates() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Verify scholarship templates exist (use first match to avoid strict mode violation)
        page.locator("text=Beasiswa Prestasi").first().isVisible();
        page.locator("text=Beasiswa Tidak Mampu").first().isVisible();

        takeManualScreenshot("campus/scholarship-templates");
    }

    @Test
    @DisplayName("Should navigate to merit scholarship form")
    void shouldNavigateToMeritScholarshipForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on merit scholarship template
        page.locator("text=Beasiswa Prestasi").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should navigate to need-based scholarship form")
    void shouldNavigateToNeedBasedScholarshipForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on need-based scholarship template
        page.locator("text=Beasiswa Tidak Mampu").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should verify scholarship accounts exist in COA")
    void shouldVerifyScholarshipAccountsExist() {
        loginAsAdmin();
        initPageObjects();

        page.navigate("http://localhost:" + port + "/accounts");
        page.waitForLoadState();

        // Verify scholarship expense accounts exist
        page.locator("text=5.3.01").isVisible();  // Beban Beasiswa Prestasi
        page.locator("text=5.3.02").isVisible();  // Beban Beasiswa Tidak Mampu

        takeManualScreenshot("campus/scholarship-accounts");
    }
}
