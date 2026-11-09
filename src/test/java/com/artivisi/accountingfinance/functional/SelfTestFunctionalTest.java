package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.enums.SelfTestRole;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.SelfTestExplanationRepository;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/** Self-test page, explanation editing and the account role field. IT service pack. Reserved test year 2021. */
@DisplayName("Tax self-test UI")
@Import(ServiceTestDataInitializer.class)
class SelfTestFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2021;

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private SelfTestExplanationRepository explanationRepository;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("An other expense without evidence shows as the difference; the explanation is saved, shown again and removed")
    void unitemisedLineAndExplanation() {
        ChartOfAccount other = account("5.1.99");
        assertThat(other.getSelfTestRole()).isEqualTo(SelfTestRole.OTHER_EXPENSE);
        UUID trx = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(YEAR, 3, 10),
                "Biaya lain tanpa bukti", null, List.of(
                        new JournalEntryRequest.JournalLineRequest(other.getId(), new BigDecimal("8000000"), BigDecimal.ZERO),
                        new JournalEntryRequest.JournalLineRequest(account("1.1.02").getId(), BigDecimal.ZERO, new BigDecimal("8000000")))),
                "admin").transactionId();
        transactionService.post(trx, "admin");

        navigateTo("/dashboard");
        waitForPageLoad();
        page.evaluate("document.querySelectorAll('details').forEach(d => d.open = true)");
        page.locator("#nav-self-test").click();
        waitForPageLoad();
        assertThat(page.getByTestId("self-test-threshold")).hasText("5.000.000");
        page.getByTestId("self-test-year").fill(String.valueOf(YEAR));
        page.getByTestId("btn-show-self-test").click();
        waitForPageLoad();

        Locator row = page.getByTestId("self-test-row-UNITEMISED_OTHER_EXPENSE-0");
        assertThat(row.getByTestId("row-book")).hasText("8.000.000,00");
        assertThat(row.getByTestId("row-tax")).hasText("0,00");
        assertThat(row.getByTestId("row-difference")).hasText("8.000.000,00");
        assertThat(row.getByTestId("row-status")).hasText("Ada selisih");
        assertThat(page.getByTestId("self-test-row-CAPITAL_MOVEMENT-0").getByTestId("row-tax")).hasText("-");
        assertThat(page.getByTestId("self-test-row-REVENUE_VS_PPN_DPP-3")).isVisible();

        row.getByTestId("row-explanation-input").fill("Biaya rapat direksi; bukti menyusul");
        row.getByTestId("btn-save-explanation").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).containsText("Penjelasan Beban lain-lain tanpa rincian disimpan");
        Locator saved = page.getByTestId("self-test-row-UNITEMISED_OTHER_EXPENSE-0");
        assertThat(saved.getByTestId("row-explanation-input")).hasValue("Biaya rapat direksi; bukti menyusul");
        assertThat(saved.getByTestId("row-explained-by")).containsText("Diubah admin");
        assertThat(explanationRepository.findByReportYearAndLineAndPeriodMonth(YEAR, SelfTestLine.UNITEMISED_OTHER_EXPENSE, 0))
                .hasValueSatisfying(e -> assertThat(e.getExplanation()).isEqualTo("Biaya rapat direksi; bukti menyusul"));

        navigateTo("/reports/self-test?year=" + YEAR);
        waitForPageLoad();
        Locator reloaded = page.getByTestId("self-test-row-UNITEMISED_OTHER_EXPENSE-0");
        assertThat(reloaded.getByTestId("row-explanation-input")).hasValue("Biaya rapat direksi; bukti menyusul");
        reloaded.getByTestId("row-explanation-input").fill("");
        reloaded.getByTestId("btn-save-explanation").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).containsText("dihapus");
        assertThat(explanationRepository.findByReportYearAndLineAndPeriodMonth(YEAR, SelfTestLine.UNITEMISED_OTHER_EXPENSE, 0)).isEmpty();
    }

    @Test
    @DisplayName("Account form sets the self-test role")
    void accountFormSetsRole() {
        ChartOfAccount account = account("5.1.07");
        assertThat(account.getSelfTestRole()).isNull();
        navigateTo("/accounts/" + account.getId() + "/edit");
        waitForPageLoad();
        page.getByTestId("self-test-role").selectOption("SERVICE_PPH23");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(account("5.1.07").getSelfTestRole()).isEqualTo(SelfTestRole.SERVICE_PPH23);

        navigateTo("/accounts/" + account.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.getByTestId("self-test-role")).hasValue("SERVICE_PPH23");
        page.getByTestId("self-test-role").selectOption("");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(account("5.1.07").getSelfTestRole()).isNull();
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow();
    }
}
