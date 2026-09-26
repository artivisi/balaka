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

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * BPJS calculator (/bpjs-calculator): every contribution for given salary and JKK risk class.
 *
 * Expected amounts follow the rates and wage ceilings in BpjsCalculationService, each rounded
 * half-up to whole rupiah:
 * - Kesehatan 4% company + 1% employee of min(salary, 12,000,000)
 * - JKK 0.24 / 0.54 / 0.89 / 1.27 / 1.74% (class 1-5) and JKM 0.3% of salary, company only
 * - JHT 3.7% company + 2% employee of salary, no ceiling
 * - JP 2% company + 1% employee of min(salary, 10,042,300)
 */
@DisplayName("BPJS Calculator Tests")
@Import(ServiceTestDataInitializer.class)
class BpjsCalculatorFunctionalTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should show an empty calculator form with the five JKK risk classes and no result")
    void shouldShowEmptyCalculatorForm() {
        navigateTo("/bpjs-calculator");
        waitForPageLoad();

        assertThat(page).hasTitle(Pattern.compile("^Kalkulator BPJS"));
        assertThat(page.locator("#page-title")).hasText("Kalkulator BPJS");
        assertThat(page.locator("#salary")).hasValue("");
        assertThat(page.locator("#riskClass option")).hasText(new String[] {
            "Kelas 1 - Risiko Sangat Rendah (IT, Jasa) - 0.24%",
            "Kelas 2 - Risiko Rendah (Retail, Perdagangan) - 0.54%",
            "Kelas 3 - Risiko Sedang (Manufaktur Ringan) - 0.89%",
            "Kelas 4 - Risiko Tinggi (Konstruksi) - 1.27%",
            "Kelas 5 - Risiko Sangat Tinggi (Pertambangan) - 1.74%"
        });
        for (int i = 0; i < 5; i++) {
            assertThat(page.locator("#riskClass option").nth(i)).hasAttribute("value", String.valueOf(i + 1));
        }
        assertThat(page.locator("#riskClass")).hasValue("1");
        assertThat(page.locator("#btn-calculate")).hasText("Hitung");
        assertThat(page.getByTestId("grand-total")).hasCount(0);

        takeManualScreenshot("bpjs-calculator");
    }

    @Test
    @DisplayName("Should calculate every contribution below both wage ceilings (10,000,000, class 1)")
    void shouldCalculateBelowCeilings() {
        calculate("10000000", "1");

        assertRate("0.24%");
        assertComponents(
            "Rp 400,000", "Rp 100,000",     // Kesehatan 4% / 1%
            "Rp 24,000", "Rp 30,000",       // JKK 0.24%, JKM 0.3%
            "Rp 370,000", "Rp 200,000",     // JHT 3.7% / 2%
            "Rp 200,000", "Rp 100,000");    // JP 2% / 1%
        assertTotals("Rp 1,024,000", "Rp 400,000", "Rp 1,424,000");
        assertThat(page.locator("text=Catatan Batas Upah")).hasCount(0);

        assertThat(page.locator("#salary")).hasValue("10000000");
        assertThat(page.locator("#riskClass")).hasValue("1");
    }

    @Test
    @DisplayName("Should cap JP at 10,042,300 but not Kesehatan for a salary between the two ceilings (11,000,000, class 2)")
    void shouldCapOnlyJpBetweenCeilings() {
        calculate("11000000", "2");

        assertRate("0.54%");
        assertComponents(
            "Rp 440,000", "Rp 110,000",     // Kesehatan on full 11,000,000
            "Rp 59,400", "Rp 33,000",
            "Rp 407,000", "Rp 220,000",
            "Rp 200,846", "Rp 100,423");    // JP on 10,042,300
        assertTotals("Rp 1,140,246", "Rp 430,423", "Rp 1,570,669");

        Locator notes = ceilingNotes();
        assertThat(notes).hasCount(1);
        assertThat(notes.first()).hasText(
            "BPJS JP: Gaji melebihi batas Rp 10,042,300, iuran dihitung dari batas maksimal.");
    }

    @Test
    @DisplayName("Should cap Kesehatan at 12,000,000 and JP at 10,042,300 above both ceilings, JHT/JKK/JKM uncapped (15,000,000, class 3)")
    void shouldCapKesehatanAndJpAboveCeilings() {
        calculate("15000000", "3");

        assertRate("0.89%");
        assertComponents(
            "Rp 480,000", "Rp 120,000",     // Kesehatan on 12,000,000
            "Rp 133,500", "Rp 45,000",      // JKK, JKM on full 15,000,000
            "Rp 555,000", "Rp 300,000",     // JHT on full 15,000,000
            "Rp 200,846", "Rp 100,423");    // JP on 10,042,300
        assertTotals("Rp 1,414,346", "Rp 520,423", "Rp 1,934,769");

        Locator notes = ceilingNotes();
        assertThat(notes).hasText(new String[] {
            "BPJS Kesehatan: Gaji melebihi batas Rp 12,000,000, iuran dihitung dari batas maksimal.",
            "BPJS JP: Gaji melebihi batas Rp 10,042,300, iuran dihitung dari batas maksimal."
        });
        assertThat(page.locator("#riskClass")).hasValue("3");
    }

    @Test
    @DisplayName("Should apply each risk class's JKK rate and leave the other contributions unchanged")
    void shouldApplyJkkRatePerRiskClass() {
        String[][] classes = {
            {"1", "0.24%", "Rp 24,000", "Rp 1,024,000"},
            {"2", "0.54%", "Rp 54,000", "Rp 1,054,000"},
            {"3", "0.89%", "Rp 89,000", "Rp 1,089,000"},
            {"4", "1.27%", "Rp 127,000", "Rp 1,127,000"},
            {"5", "1.74%", "Rp 174,000", "Rp 1,174,000"},
        };
        for (String[] c : classes) {
            calculate("10000000", c[0]);

            assertRate(c[1]);
            assertThat(page.getByTestId("jkk")).hasText(c[2]);
            assertThat(page.getByTestId("jkm")).hasText("Rp 30,000");
            assertThat(page.getByTestId("total-company")).hasText(c[3]);
            assertThat(page.getByTestId("total-employee")).hasText("Rp 400,000");
        }
    }

    @Test
    @DisplayName("Should round each contribution half-up to whole rupiah (5,555,555, class 4)")
    void shouldRoundHalfUp() {
        calculate("5555555", "4");

        assertRate("1.27%");
        assertComponents(
            "Rp 222,222", "Rp 55,556",      // 222,222.2 / 55,555.55
            "Rp 70,556", "Rp 16,667",       // 70,555.5485 / 16,666.665
            "Rp 205,556", "Rp 111,111",     // 205,555.535 / 111,111.1
            "Rp 111,111", "Rp 55,556");     // 111,111.1 / 55,555.55
        assertTotals("Rp 626,112", "Rp 222,223", "Rp 848,335");
    }

    @Test
    @DisplayName("Should reject a JKK risk class outside 1-5 instead of calculating with another class")
    void shouldRejectUnknownRiskClass() {
        navigateTo("/bpjs-calculator");
        waitForPageLoad();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");

        APIResponse response = page.request().post(baseUrl() + "/bpjs-calculator/calculate",
            RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf)
                .setForm(FormData.create().set("salary", "10000000").set("riskClass", "6")));

        assertEquals(400, response.status());
        assertFalse(response.text().contains("data-testid=\"grand-total\""));
    }

    private void calculate(String salary, String riskClass) {
        navigateTo("/bpjs-calculator");
        waitForPageLoad();
        page.locator("#salary").fill(salary);
        page.locator("#riskClass").selectOption(riskClass);
        page.locator("#btn-calculate").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/bpjs-calculator/calculate");
    }

    private void assertRate(String jkkRate) {
        Locator jkkRow = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText("BPJS JKK"));
        assertThat(jkkRow.locator("td").nth(1)).hasText(jkkRate);
    }

    private void assertComponents(String kesehatanCompany, String kesehatanEmployee, String jkk, String jkm,
                                  String jhtCompany, String jhtEmployee, String jpCompany, String jpEmployee) {
        assertThat(page.getByTestId("kesehatan-company")).hasText(kesehatanCompany);
        assertThat(page.getByTestId("kesehatan-employee")).hasText(kesehatanEmployee);
        assertThat(page.getByTestId("jkk")).hasText(jkk);
        assertThat(page.getByTestId("jkm")).hasText(jkm);
        assertThat(page.getByTestId("jht-company")).hasText(jhtCompany);
        assertThat(page.getByTestId("jht-employee")).hasText(jhtEmployee);
        assertThat(page.getByTestId("jp-company")).hasText(jpCompany);
        assertThat(page.getByTestId("jp-employee")).hasText(jpEmployee);
    }

    private void assertTotals(String company, String employee, String grandTotal) {
        assertThat(page.getByTestId("total-company")).hasText(company);
        assertThat(page.getByTestId("total-employee")).hasText(employee);
        assertThat(page.getByTestId("grand-total")).hasText(grandTotal);
        Locator footer = page.locator("tfoot tr td");
        assertThat(footer.nth(1)).hasText(company);
        assertThat(footer.nth(2)).hasText(employee);
    }

    private Locator ceilingNotes() {
        return page.locator("p:text-is('Catatan Batas Upah:') + ul li");
    }
}
