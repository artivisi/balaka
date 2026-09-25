package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Alpine.js components (CSP build): each interaction asserts the state it produces.
 */
@DisplayName("Alpine.js Interactivity Tests")
@Import(ServiceTestDataInitializer.class)
class AlpineInteractivityTest extends PlaywrightTestBase {

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Template form header badges follow the category and cash flow selections")
    void templateFormBadgesFollowSelections() {
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        Locator badges = page.locator("span[x-text='getCategoryLabel(category)'], span[x-text='getCashFlowLabel(cashFlowCategory)']");
        page.locator("select[name='category']").selectOption("EXPENSE");
        page.locator("select[name='cashFlowCategory']").selectOption("INVESTING");
        assertThat(badges.nth(0)).hasText("Pengeluaran");
        assertThat(badges.nth(1)).hasText("Investasi");

        page.locator("select[name='category']").selectOption("RECEIPT");
        page.locator("select[name='cashFlowCategory']").selectOption("FINANCING");
        assertThat(badges.nth(0)).hasText("Penerimaan");
        assertThat(badges.nth(1)).hasText("Pendanaan");
    }

    @Test
    @DisplayName("Template form lines can be added, toggled D/K, reordered and removed down to the two-line minimum")
    void templateFormLinesReorderAndRemove() {
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        Locator accountSelects = page.locator("select[name$='.accountId']");
        assertThat(accountSelects).hasCount(2);
        assertThat(accountSelects.first().locator("option", new Locator.LocatorOptions().setHasText("5.1.05 - Beban Sewa")))
                .hasCount(1);

        page.locator("[data-testid='btn-add-line']").click();
        assertThat(accountSelects).hasCount(3);
        assertThat(page.locator("input[name='lines[2].position']")).hasValue("DEBIT");
        page.locator("#btn-credit-2").click();
        assertThat(page.locator("input[name='lines[2].position']")).hasValue("CREDIT");
        page.locator("#btn-debit-2").click();
        assertThat(page.locator("input[name='lines[2].position']")).hasValue("DEBIT");
        page.locator("#btn-credit-2").click();
        page.locator("input[name='lines[2].formula']").fill("amount * 0.1");

        Locator moveUp = page.locator("button[\\@click='moveUp(index)']");
        Locator moveDown = page.locator("button[\\@click='moveDown(index)']");
        Locator remove = page.locator("button[\\@click='removeLine(line.id)']");
        assertThat(moveUp.first()).isDisabled();
        assertThat(moveDown.last()).isDisabled();

        moveUp.nth(2).click();
        assertThat(page.locator("input[name='lines[1].formula']")).hasValue("amount * 0.1");
        assertThat(page.locator("input[name='lines[1].position']")).hasValue("CREDIT");
        assertThat(page.locator("input[name='lines[2].formula']")).hasValue("amount");
        assertThat(page.locator("input[name='lines[0].lineOrder']")).hasValue("1");
        assertThat(page.locator("input[name='lines[2].lineOrder']")).hasValue("3");

        moveDown.nth(0).click();
        assertThat(page.locator("input[name='lines[0].formula']")).hasValue("amount * 0.1");
        assertThat(page.locator("input[name='lines[0].position']")).hasValue("CREDIT");
        assertThat(page.locator("input[name='lines[1].position']")).hasValue("DEBIT");

        remove.nth(0).click();
        assertThat(accountSelects).hasCount(2);
        assertThat(page.locator("input[name='lines[0].position']")).hasValue("DEBIT");
        assertThat(page.locator("input[name='lines[1].position']")).hasValue("CREDIT");
        assertThat(page.locator("input[name='lines[0].formula']")).hasValue("amount");
        assertThat(remove.nth(0)).isDisabled();
        assertThat(remove.nth(1)).isDisabled();
    }

    @Test
    @DisplayName("Formula help opens, switches tabs and evaluates the tried formula on the server")
    void formulaHelpEvaluatesTriedFormula() {
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        Locator content = page.locator("[data-testid='formula-help-content']");
        Locator tryFormula = page.locator("#tryFormula");
        assertThat(content).isHidden();
        page.locator("[data-testid='btn-formula-help-toggle']").click();
        assertThat(content).isVisible();
        assertThat(tryFormula).isVisible();

        content.locator("button", new Locator.LocatorOptions().setHasText("amount * 0.11")).click();
        assertThat(tryFormula).hasValue("amount * 0.11");
        assertThat(content.locator("span.text-primary-700")).hasText("Rp 1.100.000");

        page.locator("#tryAmount").fill("2500000");
        assertThat(page.locator("#tryAmount")).hasValue("2.500.000");
        assertThat(content.locator("span.text-primary-700")).hasText("Rp 275.000");

        tryFormula.fill("amount *");
        assertThat(content.locator("span.text-red-600")).isVisible();
        assertThat(content.locator("span.text-primary-700")).hasCount(0);

        content.locator("button", new Locator.LocatorOptions().setHasText("Sintaks")).click();
        assertThat(tryFormula).isHidden();
        page.locator("[data-testid='btn-formula-help-toggle']").click();
        assertThat(content).isHidden();
    }

    @Test
    @DisplayName("Cash flow help tooltip toggles on its button and closes on an outside click")
    void cashFlowHelpTooltipToggles() {
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        Locator tooltip = page.getByText("Untuk Laporan Arus Kas:");
        Locator helpButton = page.locator("div[x-data='showState']:has-text('Untuk Laporan Arus Kas:') > button");
        assertThat(tooltip).isHidden();
        helpButton.click();
        assertThat(tooltip).isVisible();
        page.locator("#page-title").click();
        assertThat(tooltip).isHidden();
        helpButton.click();
        helpButton.click();
        assertThat(tooltip).isHidden();
    }

    @Test
    @DisplayName("Account tree expands a header to show its children and collapses them again")
    void accountTreeExpandsAndCollapses() {
        loginAsAdmin();
        navigateTo("/accounts");
        waitForPageLoad();

        Locator child = page.locator("#account-row-5-1");
        assertThat(child).isHidden();
        page.locator("#btn-expand-5").click();
        assertThat(child).isVisible();
        assertThat(page.locator("#account-name-5-1")).hasText("Beban Operasional");
        page.locator("#btn-expand-5").click();
        assertThat(child).isHidden();
    }

    @Test
    @DisplayName("Transaction form formats the amount, feeds the journal preview and toggles the help panel")
    void transactionFormFormatsAmountAndRefreshesPreview() {
        UUID templateId = journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Pendapatan Jasa Training")
                .orElseThrow(() -> new IllegalStateException("Seeded template 'Pendapatan Jasa Training' missing"))
                .getId();
        loginAsAdmin();
        navigateTo("/transactions/new?templateId=" + templateId);
        waitForPageLoad();

        assertThat(page.locator("#btn-simpan-draft")).hasText("Simpan Draft");
        assertThat(page.locator("#btn-simpan-posting")).hasText("Simpan & Posting");

        page.locator("#amount").pressSequentially("2500000");
        assertThat(page.locator("#amount")).hasValue("2.500.000");
        assertThat(page.locator("#amountHidden")).hasValue("2500000");
        assertThat(page.locator("#total-debit")).hasText("Rp 2.500.000");
        assertThat(page.locator("#total-credit")).hasText("Rp 2.500.000");

        Locator help = page.getByText("Status Transaksi");
        assertThat(help).isHidden();
        page.locator("button", new Page.LocatorOptions().setHasText("Bantuan Pengisian Transaksi")).click();
        assertThat(help).isVisible();
        page.locator("button", new Page.LocatorOptions().setHasText("Bantuan Pengisian Transaksi")).click();
        assertThat(help).isHidden();
    }

    @Test
    @DisplayName("Void form enables the void button only when a reason is chosen and the consequence is confirmed")
    void voidButtonRequiresReasonAndConfirmation() {
        UUID transactionId = transactionRepository.findByTransactionNumber("SVC-TRX-002")
                .orElseThrow(() -> new IllegalStateException("Seeded transaction SVC-TRX-002 missing"))
                .getId();
        loginAsAdmin();
        navigateTo("/transactions/" + transactionId + "/void");
        waitForPageLoad();

        Locator voidButton = page.locator("#btn-void");
        Locator confirm = page.locator("input[name='confirmVoid']");
        assertThat(voidButton).isDisabled();
        page.locator("input[name='voidReason'][value='DUPLICATE']").check();
        assertThat(voidButton).isDisabled();
        confirm.check();
        assertThat(voidButton).isEnabled();
        confirm.uncheck();
        assertThat(voidButton).isDisabled();
    }

    @Test
    @DisplayName("Inventory 'Transaksi Baru' menu opens on its button and closes on an outside click")
    void inventoryNewTransactionMenuToggles() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        Locator menu = page.locator("#new-transaction-menu");
        assertThat(menu).isHidden();
        page.locator("#btn-new-transaction").click();
        assertThat(menu).isVisible();
        assertThat(page.locator("#menu-purchase")).isVisible();
        page.locator("#page-title").click();
        assertThat(menu).isHidden();
        page.locator("#btn-new-transaction").click();
        page.locator("#btn-new-transaction").click();
        assertThat(menu).isHidden();
    }
}
