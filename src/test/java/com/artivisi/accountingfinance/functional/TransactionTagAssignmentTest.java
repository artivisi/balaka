package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assigning tags on the transaction form. Each test creates its own tag type with two tags and
 * saves drafts of "Bayar Beban Listrik" dated January 2104 (reserved for this class).
 */
@DisplayName("Transaction Tag Assignment Tests")
@Import(ServiceTestDataInitializer.class)
class TransactionTagAssignmentTest extends PlaywrightTestBase {

    private static final String TEMPLATE = "Bayar Beban Listrik";

    @Autowired private TagTypeRepository tagTypeRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JdbcTemplate jdbc;

    private JournalTemplate template;
    private TagType department;
    private Tag marketing;
    private Tag finance;

    @BeforeEach
    void setup() {
        JournalTemplate current = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(TEMPLATE)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + TEMPLATE));
        template = templateRepository.findByIdWithLines(current.getId()).orElseThrow();

        String id = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        department = new TagType();
        department.setCode("DP" + id);
        department.setName("Departemen " + id);
        department.setActive(true);
        department = tagTypeRepository.save(department);
        marketing = saveTag("MKT" + id, "Marketing " + id);
        finance = saveTag("FIN" + id, "Finance " + id);
        loginAsAdmin();
    }

    @Test
    @DisplayName("Form lists active tags as checkboxes under their tag type, none checked")
    void shouldShowTagCheckboxesOnForm() {
        openNewForm();

        Locator group = page.locator("div:has(> p:text-is('" + department.getName() + "'))");
        assertThat(group.locator("label")).hasText(new String[] {finance.getName(), marketing.getName()});
        assertThat(checkbox(marketing)).not().isChecked();
        assertThat(checkbox(finance)).not().isChecked();
    }

    @Test
    @DisplayName("Tags checked on the form are stored and shown on the detail page; editing replaces them")
    void shouldCreateTransactionWithTags() {
        String description = "Transaksi berlabel " + UUID.randomUUID().toString().substring(0, 8);
        openNewForm();
        fillDraft("2104-01-15", "100000", description);
        checkbox(marketing).check();
        checkbox(finance).check();
        UUID id = saveDraft();

        assertThat(tagIds(id)).containsExactlyInAnyOrder(marketing.getId(), finance.getId());
        Locator labels = page.locator("div:has(> p:text-is('Label')) span.rounded-full");
        assertThat(labels).hasCount(2);
        assertThat(labels.filter(new Locator.FilterOptions().setHasText(marketing.getName())))
                .hasText(department.getName() + ": " + marketing.getName());
        assertThat(labels.filter(new Locator.FilterOptions().setHasText(finance.getName())))
                .hasText(department.getName() + ": " + finance.getName());

        navigateTo("/transactions/" + id + "/edit");
        waitForPageLoad();
        assertThat(checkbox(marketing)).isChecked();
        assertThat(checkbox(finance)).isChecked();
        checkbox(finance).uncheck();
        page.locator("#btn-simpan-draft").click();
        page.waitForURL(baseUrl() + "/transactions/" + id);
        waitForPageLoad();

        assertThat(tagIds(id)).containsExactly(marketing.getId());
        assertThat(labels).hasCount(1);
        assertThat(labels).hasText(department.getName() + ": " + marketing.getName());
    }

    @Test
    @DisplayName("A transaction saved without checking tags has no tags and no label section")
    void shouldCreateTransactionWithoutTags() {
        String description = "Transaksi tanpa label " + UUID.randomUUID().toString().substring(0, 8);
        openNewForm();
        fillDraft("2104-01-16", "200000", description);
        UUID id = saveDraft();

        assertThat(jdbc.queryForObject("SELECT description FROM transactions WHERE id = ?", String.class, id))
                .isEqualTo(description);
        assertThat(tagIds(id)).isEmpty();
        assertThat(page.locator("div:has(> p:text-is('Label'))")).hasCount(0);
    }

    private void openNewForm() {
        navigateTo("/transactions/new?templateId=" + template.getId());
        waitForPageLoad();
        page.locator("#amount").waitFor();
    }

    private void fillDraft(String date, String amount, String description) {
        page.locator("#transactionDate").fill(date);
        page.locator("#amount").fill(amount);
        page.locator("#description").fill(description);
        UUID bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template " + TEMPLATE + " has no BANK line"))
                .getId();
        ChartOfAccount bank = accountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.02"));
        Locator input = page.locator("#accountMapping_" + bankLine);
        input.click();
        input.fill("1.1.02");
        input.locator("xpath=..").locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText("1.1.02 — ")).click();
        assertThat(page.locator("input[name='accountMapping[" + bankLine + "]']")).hasValue(bank.getId().toString());
    }

    private UUID saveDraft() {
        page.locator("#btn-simpan-draft").click();
        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}$"));
        waitForPageLoad();
        return UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));
    }

    private Locator checkbox(Tag tag) {
        return page.locator("input[name='tagIds'][value='" + tag.getId() + "']");
    }

    private List<UUID> tagIds(UUID transactionId) {
        return jdbc.queryForList("SELECT id_tag FROM transaction_tags WHERE id_transaction = ?", UUID.class, transactionId);
    }

    private Tag saveTag(String code, String name) {
        Tag tag = new Tag();
        tag.setTagType(department);
        tag.setCode(code);
        tag.setName(name);
        tag.setActive(true);
        return tagRepository.save(tag);
    }
}
