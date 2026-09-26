package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PPh 21 calculator (/pph21-calculator): monthly withholding by the TER method (PMK 168/2023).
 *
 * PTKP status selects the TER category (A: TK/0, TK/1, K/0; B: TK/2, TK/3, K/1, K/2;
 * C: K/3, K/I/0-3); gross income selects the bracket; PPh 21 = gross x rate, rounded half-up.
 */
@DisplayName("PPh 21 Calculator Tests")
@Import(ServiceTestDataInitializer.class)
class Pph21CalculatorFunctionalTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should show an empty calculator form with every PTKP status and TK/0 preselected")
    void shouldShowEmptyCalculatorForm() {
        navigateTo("/pph21-calculator");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Kalkulator PPh 21 — Metode TER");
        assertThat(page.locator("#salary")).hasValue("");
        assertThat(page.locator("#ptkpStatus option")).hasText(new String[] {
            "TK/0 - Tidak Kawin tanpa tanggungan",
            "TK/1 - Tidak Kawin 1 tanggungan",
            "TK/2 - Tidak Kawin 2 tanggungan",
            "TK/3 - Tidak Kawin 3 tanggungan",
            "K/0 - Kawin tanpa tanggungan",
            "K/1 - Kawin 1 tanggungan",
            "K/2 - Kawin 2 tanggungan",
            "K/3 - Kawin 3 tanggungan",
            "K/I/0 - Kawin penghasilan istri digabung tanpa tanggungan",
            "K/I/1 - Kawin penghasilan istri digabung 1 tanggungan",
            "K/I/2 - Kawin penghasilan istri digabung 2 tanggungan",
            "K/I/3 - Kawin penghasilan istri digabung 3 tanggungan"
        });
        assertThat(page.locator("#ptkpStatus")).hasValue("TK_0");
        assertThat(page.locator("[data-testid='monthly-pph21']")).hasCount(0);

        takeManualScreenshot("pph21-calculator");
    }

    @Test
    @DisplayName("Should calculate category A at 2% for 10,000,000 TK/0 and show the breakdown")
    void shouldCalculateCategoryA() {
        calculate("10000000", "TK_0");

        assertResult("Rp 10,000,000", "A", "2.00%", "Rp 200,000");
        Locator rows = page.locator("tbody tr");
        assertThat(rows.nth(0).locator("td").nth(1)).hasText("Rp 10,000,000");
        assertThat(rows.nth(1).locator("td").nth(0).locator("div").nth(1)).hasText("TK/0 - Tidak Kawin tanpa tanggungan");
        assertThat(rows.nth(1).locator("td").nth(1)).hasText("Kategori A");
        assertThat(rows.nth(2).locator("td").nth(1)).hasText("2.00%");
        assertThat(page.locator("tfoot td").nth(1)).hasText("Rp 200,000");
        assertThat(page.locator("#salary")).hasValue("10000000");
        assertThat(page.locator("#ptkpStatus")).hasValue("TK_0");
    }

    @Test
    @DisplayName("Should calculate category B at 2.5% for 11,253,000 K/2")
    void shouldCalculateCategoryB() {
        calculate("11253000", "K_2");

        assertResult("Rp 11,253,000", "B", "2.50%", "Rp 281,325");
        assertThat(page.locator("tbody tr").nth(1).locator("td").nth(0).locator("div").nth(1))
            .hasText("K/2 - Kawin 2 tanggungan");
        assertThat(page.locator("#ptkpStatus")).hasValue("K_2");
    }

    @Test
    @DisplayName("Should map every PTKP status to its TER category and that category's rate for 9,000,000")
    void shouldMapPtkpStatusToCategory() {
        String[][] statuses = {
            {"TK_0", "A", "1.75%", "Rp 157,500"},
            {"TK_1", "A", "1.75%", "Rp 157,500"},
            {"K_0", "A", "1.75%", "Rp 157,500"},
            {"TK_2", "B", "1.00%", "Rp 90,000"},
            {"TK_3", "B", "1.00%", "Rp 90,000"},
            {"K_1", "B", "1.00%", "Rp 90,000"},
            {"K_2", "B", "1.00%", "Rp 90,000"},
            {"K_3", "C", "1.25%", "Rp 112,500"},
            {"K_I_0", "C", "1.25%", "Rp 112,500"},
            {"K_I_1", "C", "1.25%", "Rp 112,500"},
            {"K_I_2", "C", "1.25%", "Rp 112,500"},
            {"K_I_3", "C", "1.25%", "Rp 112,500"},
        };
        for (String[] s : statuses) {
            calculate("9000000", s[0]);
            assertResult("Rp 9,000,000", s[1], s[2], s[3]);
        }
    }

    @Test
    @DisplayName("Should withhold nothing up to each category's zero-rate ceiling and 0.25% one rupiah above it")
    void shouldApplyZeroRateCeilingPerCategory() {
        String[][] ceilings = {
            {"TK_0", "A", "5400000", "5,400,000", "5400001", "5,400,001", "Rp 13,500"},
            {"K_1", "B", "6200000", "6,200,000", "6200001", "6,200,001", "Rp 15,500"},
            {"K_3", "C", "6600000", "6,600,000", "6600001", "6,600,001", "Rp 16,500"},
        };
        for (String[] c : ceilings) {
            calculate(c[2], c[0]);
            assertResult("Rp " + c[3], c[1], "0.00%", "Rp 0");

            calculate(c[4], c[0]);
            assertResult("Rp " + c[5], c[1], "0.25%", c[6]);
        }
    }

    @Test
    @DisplayName("Should reject an unknown PTKP status instead of calculating")
    void shouldRejectUnknownPtkpStatus() {
        navigateTo("/pph21-calculator");
        waitForPageLoad();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");

        APIResponse response = page.request().post(baseUrl() + "/pph21-calculator/calculate",
            RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf)
                .setForm(FormData.create().set("salary", "10000000").set("ptkpStatus", "K_9")));

        assertEquals(400, response.status());
        assertFalse(response.text().contains("data-testid=\"monthly-pph21\""));
    }

    private void calculate(String salary, String ptkpStatus) {
        navigateTo("/pph21-calculator");
        waitForPageLoad();
        page.locator("#salary").fill(salary);
        page.locator("#ptkpStatus").selectOption(ptkpStatus);
        page.locator("#btn-calculate").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/pph21-calculator/calculate");
    }

    private void assertResult(String gross, String category, String rate, String pph21) {
        assertThat(page.locator("[data-testid='gross-income']")).hasText(gross);
        assertThat(page.locator("[data-testid='ter-category']")).hasText(category);
        assertThat(page.locator("[data-testid='ter-rate']")).hasText(rate);
        assertThat(page.locator("[data-testid='monthly-pph21']")).hasText(pph21);
    }
}
