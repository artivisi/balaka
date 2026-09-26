package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tag summary report (/reports/tag-summary): posted transactions grouped by tag type and tag,
 * split into income and expense by template category.
 *
 * The report test posts its own tagged transactions into March 2104 (reserved for this class)
 * and reads that month only, so it sees exactly one tag type section.
 */
@DisplayName("Tag Report Tests")
@Import(ServiceTestDataInitializer.class)
class TagReportTest extends PlaywrightTestBase {

    private static final String MARCH = "startDate=2104-03-01&endDate=2104-03-31";

    @Autowired private TagTypeRepository tagTypeRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Report sums posted income and expense per tag with subtotals, ignoring drafts and other months")
    void shouldSummarizePostedTransactionsPerTag() {
        assertThat(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
                LocalDate.of(2104, 3, 1), LocalDate.of(2104, 3, 31)))
                .as("Another test posted into reserved tag report month 2104-03").isEmpty();
        String id = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TagType type = saveType("RP" + id, "Proyek " + id);
        Tag alpha = saveTag(type, "A" + id, "Alpha " + id);
        Tag beta = saveTag(type, "B" + id, "Beta " + id);

        record(alpha, "Pendapatan Jasa Training", "2104-03-05", "5000000", true);
        record(alpha, "Bayar Beban Listrik", "2104-03-12", "1200000", true);
        record(beta, "Bayar Beban Listrik", "2104-03-20", "800000", true);
        record(alpha, "Pendapatan Jasa Training", "2104-03-25", "9999999", false);
        record(alpha, "Pendapatan Jasa Training", "2104-04-01", "7000000", true);

        loginAsAdmin();
        navigateTo("/reports/tag-summary?" + MARCH);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Ringkasan per Label");
        assertThat(page.locator("#startDate")).hasValue("2104-03-01");
        assertThat(page.locator("#endDate")).hasValue("2104-03-31");
        Locator sections = page.locator("section div.border-b:has(h3)");
        assertThat(sections).hasCount(1);
        assertThat(sections.locator("h3")).hasText("Proyek " + id);
        Locator rows = sections.locator("tbody tr");
        assertThat(rows).hasCount(2);
        assertThat(rows.nth(0).locator("td")).hasText(new String[] {"Alpha " + id, "2", "5.000.000", "1.200.000", "3.800.000"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[] {"Beta " + id, "1", "0", "800.000", "-800.000"});
        assertThat(sections.locator("tfoot td")).hasText(new String[] {"Subtotal", "3", "5.000.000", "2.000.000", "3.000.000"});
    }

    @Test
    @DisplayName("A period without posted tagged transactions shows the empty state")
    void shouldShowEmptyStateWhenNoTaggedTransactions() {
        loginAsAdmin();
        navigateTo("/reports/tag-summary?startDate=2000-01-01&endDate=2000-12-31");
        waitForPageLoad();

        assertThat(page.locator("section div.border-b:has(h3)")).hasCount(0);
        assertThat(page.getByText("Tidak ada data transaksi dengan label pada periode ini")).isVisible();
    }

    @Test
    @DisplayName("Reports index links to the tag summary")
    void shouldShowTagSummaryLinkOnReportsIndex() {
        loginAsAdmin();
        navigateTo("/reports");
        waitForPageLoad();

        Locator link = page.locator("a[href='/reports/tag-summary']");
        assertThat(link).containsText("Ringkasan per Label");
        link.click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/reports/tag-summary");
        assertThat(page.locator("#page-title")).hasText("Ringkasan per Label");
    }

    private void record(Tag tag, String templateName, String date, String amount, boolean post) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template has no BANK line: " + templateName));
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(LocalDate.parse(date));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(templateName + " " + tag.getName());
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(),
                accountRepository.findByAccountCode("1.1.02")
                        .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.02")).getId()));
        transactionService.assignTags(created, List.of(tag.getId()));
        if (post) {
            transactionService.post(created.getId(), "admin");
        }
    }

    private TagType saveType(String code, String name) {
        TagType type = new TagType();
        type.setCode(code);
        type.setName(name);
        type.setActive(true);
        return tagTypeRepository.save(type);
    }

    private Tag saveTag(TagType type, String code, String name) {
        Tag tag = new Tag();
        tag.setTagType(type);
        tag.setCode(code);
        tag.setName(name);
        tag.setActive(true);
        return tagRepository.save(tag);
    }
}
