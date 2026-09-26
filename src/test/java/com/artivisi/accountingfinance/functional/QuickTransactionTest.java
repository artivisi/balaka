package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.page.QuickTransactionModal;
import com.artivisi.accountingfinance.functional.page.TransactionFormPage;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick transaction modal (FAB on every page): frequent/recent template picker, quick form,
 * draft creation, cancel and ESC; plus the full form's journal preview.
 *
 * Each test creates its own templates with a usage count and last-used time above every seeded
 * template, so they head the frequent and recent lists: one with fixed accounts
 * (5.1.07 D / 1.1.02 C) and one whose credit line is a BANK account picker. Drafts are dated
 * February 2108 (reserved for this class).
 */
@DisplayName("Quick Transaction Flow")
@Import(ServiceTestDataInitializer.class)
class QuickTransactionTest extends PlaywrightTestBase {

    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbc;

    private QuickTransactionModal modal;
    private JournalTemplate fixed;
    private JournalTemplate withPicker;

    @BeforeEach
    void setup() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        // Above every seeded template and every template an earlier test of this class created
        int usage = (int) (System.currentTimeMillis() / 1000);
        LocalDateTime future = LocalDateTime.now().plusYears(10);
        fixed = saveTemplate("Cepat Tetap " + id, usage, future, false);
        withPicker = saveTemplate("Cepat Bank " + id, usage - 1, future.minusNanos(1_000), true);

        modal = new QuickTransactionModal(page, baseUrl());
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();
    }

    @Test
    @DisplayName("FAB opens the modal listing top templates by usage and by last use")
    void shouldListFrequentAndRecentTemplates() {
        assertThat(page.getByTestId("quick-transaction-fab")).hasText("Transaksi Cepat");

        modal.openModal();

        assertThat(page.getByTestId("quick-transaction-modal")).isVisible();
        Locator frequent = page.locator("[data-testid^='template-frequent-']");
        List<String> expectedFrequent = templateRepository.findByActiveAndIsCurrentVersionTrueOrderByUsageCountDesc(true).stream()
                .filter(t -> t.getUsageCount() > 0).limit(6).map(t -> "template-frequent-" + t.getId()).toList();
        assertThat(frequent).hasCount(expectedFrequent.size());
        assertThat(frequent.nth(0)).hasAttribute("data-testid", "template-frequent-" + fixed.getId());
        assertThat(frequent.nth(0)).containsText(fixed.getTemplateName());
        assertThat(frequent.nth(0)).containsText(fixed.getUsageCount() + "x");
        assertThat(frequent.nth(1)).hasAttribute("data-testid", "template-frequent-" + withPicker.getId());
        assertThat(frequent.evaluateAll("els => els.map(e => e.dataset.testid)")).asList()
                .containsExactlyInAnyOrderElementsOf(expectedFrequent);
        Locator recent = page.locator("[data-testid^='template-recent-']");
        List<String> expectedRecent = templateRepository.findByActiveAndIsCurrentVersionTrueOrderByLastUsedAtDesc(true).stream()
                .filter(t -> t.getLastUsedAt() != null).limit(5).map(t -> "template-recent-" + t.getId()).toList();
        assertThat(recent).hasCount(expectedRecent.size());
        assertThat(recent.nth(0)).hasAttribute("data-testid", "template-recent-" + fixed.getId());
        assertThat(recent.nth(0)).containsText(fixed.getLastUsedAt()
                .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.of("id", "ID"))));
        assertThat(recent.nth(1)).hasAttribute("data-testid", "template-recent-" + withPicker.getId());
        assertThat(recent.evaluateAll("els => els.map(e => e.dataset.testid)")).asList()
                .containsExactlyInAnyOrderElementsOf(expectedRecent);
    }

    @Test
    @DisplayName("Choosing a template loads its quick form with the name as description and today's date")
    void shouldLoadFormForSelectedTemplate() {
        modal.openModal();

        page.getByTestId("template-frequent-" + fixed.getId()).click();

        waitForQuickForm();
        assertThat(page.locator("#quick-transaction-content h3")).hasText(fixed.getTemplateName());
        assertThat(page.locator("#quick-template-id")).hasValue(fixed.getId().toString());
        assertThat(page.getByTestId("quick-description")).hasValue(fixed.getTemplateName());
        assertThat(page.locator("#quick-date")).hasValue(LocalDate.now().toString());
        assertThat(page.locator("[id^='quick-accountMapping_']")).hasCount(0);
    }

    @Test
    @DisplayName("Submitting the quick form saves a draft with the entered values and opens it")
    void shouldCreateTransactionViaQuickForm() {
        String description = "Transaksi cepat " + UUID.randomUUID().toString().substring(0, 8);
        modal.openModal();
        page.getByTestId("template-frequent-" + fixed.getId()).click();
        waitForQuickForm();

        page.locator("#quick-amount-display").pressSequentially("7500000");
        assertThat(page.locator("#quick-amount")).hasValue("7500000");
        page.getByTestId("quick-description").fill(description);
        page.locator("#quick-date").fill("2108-02-10");
        modal.submit();

        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}$"));
        UUID id = UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));
        assertThat(page.getByTestId("status-draft")).isVisible();
        Transaction saved = transactionRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getAmount()).isEqualByComparingTo("7500000");
        assertThat(saved.getTransactionDate()).isEqualTo(LocalDate.of(2108, 2, 10));
        assertThat(templateOf(id)).isEqualTo(fixed.getId());
    }

    @Test
    @DisplayName("A template with an account hint asks for the account and stores the chosen one")
    void shouldStoreAccountPickedInQuickForm() {
        String description = "Transaksi cepat bank " + UUID.randomUUID().toString().substring(0, 8);
        UUID bankLine = withPicker.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst().orElseThrow().getId();
        String mandiri = accountRepository.findByAccountCode("1.1.03")
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.03")).getId().toString();
        modal.openModal();
        page.getByTestId("template-frequent-" + withPicker.getId()).click();
        waitForQuickForm();

        Locator picker = page.locator("#quick-accountMapping_" + bankLine);
        picker.click();
        picker.fill("1.1.03");
        picker.locator("xpath=..").locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText("1.1.03 — ")).click();
        assertThat(picker).hasValue("1.1.03 - Bank Mandiri");
        assertThat(page.locator("input[name='accountMapping[" + bankLine + "]']")).hasValue(mandiri);
        page.locator("#quick-amount-display").pressSequentially("250000");
        page.getByTestId("quick-description").fill(description);
        page.locator("#quick-date").fill("2108-02-11");
        assertThat(page.locator("#quick-amount")).hasValue("250000");
        modal.submit();

        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}$"));
        UUID id = UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));
        assertThat(jdbc.queryForList("SELECT a.account_code FROM transaction_account_mappings m "
                + "JOIN chart_of_accounts a ON a.id = m.id_account WHERE m.id_transaction = ?", String.class, id))
                .containsExactly("1.1.03");
        assertThat(transactionRepository.findById(id).orElseThrow().getAmount()).isEqualByComparingTo("250000");
    }

    @Test
    @DisplayName("Cancel closes the modal without saving")
    void cancelTransaction() {
        String description = "Transaksi batal " + UUID.randomUUID().toString().substring(0, 8);
        modal.openModal();
        page.getByTestId("template-frequent-" + fixed.getId()).click();
        waitForQuickForm();
        page.locator("#quick-amount-display").pressSequentially("1000000");
        page.getByTestId("quick-description").fill(description);

        modal.cancel();

        assertThat(page.getByTestId("quick-transaction-modal")).isHidden();
        assertThat(page).hasURL(baseUrl() + "/dashboard");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE description = ?", Long.class, description))
                .isZero();
    }

    @Test
    @DisplayName("ESC closes the modal")
    void closeModalWithEscKey() {
        modal.openModal();
        assertThat(page.getByTestId("quick-transaction-modal")).isVisible();

        modal.closeModal();

        assertThat(page.getByTestId("quick-transaction-modal")).isHidden();
    }

    @Test
    @DisplayName("Full form preview shows the template's lines for the entered amount")
    void previewUpdatesOnAmountEntry() {
        TransactionFormPage formPage = new TransactionFormPage(page, baseUrl());
        formPage.navigateWithTemplate(fixed.getId());

        formPage.fillAmount("5000000");
        formPage.waitForPreviewUpdate();

        assertThat(page.locator("#entry-account-code-0")).hasText("5.1.07");
        assertThat(page.locator("#entry-debit-0")).containsText("5.000.000");
        assertThat(page.locator("#entry-account-code-1")).hasText("1.1.02");
        assertThat(page.locator("#entry-credit-1")).containsText("5.000.000");
        assertThat(formPage.getPreviewTotalDebit()).isEqualTo(5_000_000L);
        assertThat(formPage.getPreviewTotalCredit()).isEqualTo(5_000_000L);
    }

    /** The form is swapped in by HTMX; typing before Alpine binds it would be overwritten by the empty amount. */
    private void waitForQuickForm() {
        assertThat(page.getByTestId("quick-transaction-form")).isVisible();
        assertThat(page.locator("#quick-amount")).hasValue("0");
    }

    private UUID templateOf(UUID transactionId) {
        return jdbc.queryForObject("SELECT id_journal_template FROM transactions WHERE id = ?", UUID.class, transactionId);
    }

    private JournalTemplate saveTemplate(String name, int usageCount, LocalDateTime lastUsedAt, boolean bankPicker) {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName(name);
        template.setCategory(TemplateCategory.EXPENSE);
        template.setCashFlowCategory(CashFlowCategory.OPERATING);
        template.setTemplateType(TemplateType.SIMPLE);
        template.setDescription("Template transaksi cepat");
        template.setUsageCount(usageCount);
        template.setLastUsedAt(lastUsedAt);
        template.addLine(line(1, JournalPosition.DEBIT, "5.1.07", null));
        template.addLine(bankPicker ? line(2, JournalPosition.CREDIT, null, "BANK") : line(2, JournalPosition.CREDIT, "1.1.02", null));
        JournalTemplate saved = templateRepository.save(template);
        return templateRepository.findByIdWithLines(saved.getId()).orElseThrow();
    }

    private JournalTemplateLine line(int order, JournalPosition position, String accountCode, String hint) {
        JournalTemplateLine line = new JournalTemplateLine();
        line.setLineOrder(order);
        line.setPosition(position);
        line.setFormula("amount");
        line.setAccountHint(hint);
        if (accountCode != null) {
            line.setAccount(accountRepository.findByAccountCode(accountCode)
                    .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + accountCode)));
        }
        return line;
    }
}
