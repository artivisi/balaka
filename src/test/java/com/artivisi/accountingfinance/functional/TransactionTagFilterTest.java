package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tag filter on the transaction list. Each test creates its own tag type, tag and transactions
 * dated January 2062, which is reserved for this class.
 */
@DisplayName("Transaction Tag Filter Tests")
@Import(ServiceTestDataInitializer.class)
class TransactionTagFilterTest extends PlaywrightTestBase {

    private static final String JANUARY = "startDate=2062-01-01&endDate=2062-01-31";

    @Autowired private TagTypeRepository tagTypeRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;

    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    @Test
    @DisplayName("Tag filter lists active tags grouped under their tag type")
    void shouldListTagsGroupedByType() {
        Tag tag = createTag();

        navigateTo("/transactions");
        waitForPageLoad();

        assertThat(page.locator("#filter-tag option").first()).hasText("Semua Label");
        assertThat(page.locator("#filter-tag optgroup[label='" + tag.getTagType().getName() + "'] option[value='" + tag.getId() + "']"))
                .hasText(tag.getName());
    }

    @Test
    @DisplayName("Filtering by tag shows only tagged transactions; clearing the filter shows all again")
    void shouldFilterTransactionsByTag() {
        Tag tag = createTag();
        String marker = "Tag " + UUID.randomUUID().toString().substring(0, 8);
        UUID tagged = createDraft(marker + " berlabel", List.of(tag.getId()));
        UUID untagged = createDraft(marker + " polos", List.of());

        navigateTo("/transactions?" + JANUARY);
        waitForPageLoad();
        assertThat(page.locator("#trx-row-" + tagged)).isVisible();
        assertThat(page.locator("#trx-row-" + untagged)).isVisible();

        page.locator("#filter-tag").selectOption(tag.getId().toString());
        assertThat(page.locator("#trx-row-" + untagged)).hasCount(0);
        assertThat(page.locator("#trx-row-" + tagged)).isVisible();
        assertThat(page).hasURL(Pattern.compile(".*tagId=" + tag.getId() + ".*"));

        page.locator("#filter-tag").selectOption("");
        assertThat(page.locator("#trx-row-" + untagged)).isVisible();
        assertThat(page.locator("#trx-row-" + tagged)).isVisible();

        navigateTo("/transactions?" + JANUARY + "&tagId=" + tag.getId());
        waitForPageLoad();
        assertThat(page.locator("#filter-tag")).hasValue(tag.getId().toString());
        assertThat(page.locator("#trx-row-" + tagged)).isVisible();
        assertThat(page.locator("#trx-row-" + untagged)).hasCount(0);
    }

    private Tag createTag() {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TagType type = new TagType();
        type.setCode("TT" + code);
        type.setName("Jenis " + code);
        type.setActive(true);
        type = tagTypeRepository.save(type);

        Tag tag = new Tag();
        tag.setTagType(type);
        tag.setCode("TG" + code);
        tag.setName("Label " + code);
        tag.setActive(true);
        return tagRepository.save(tag);
    }

    private UUID createDraft(String description, List<UUID> tagIds) {
        JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue("Bayar Beban Listrik")
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: Bayar Beban Listrik"));
        String bankLine = templateRepository.findByIdWithLines(template.getId()).orElseThrow().getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Template Bayar Beban Listrik has no BANK line"))
                .getId().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateId", template.getId().toString());
        body.put("transactionDate", "2062-01-10");
        body.put("amount", 150000);
        body.put("description", description);
        body.put("accountMappings", Map.of(bankLine, accountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.02")).getId().toString()));
        body.put("tagIds", tagIds.stream().map(UUID::toString).toList());
        APIResponse response = page.request().post(baseUrl() + "/transactions/api",
                RequestOptions.create().setHeader(csrfHeader, csrfToken).setData(body));
        assertThat(response.status()).as(response.text()).isEqualTo(200);
        try {
            return UUID.fromString(new ObjectMapper().readTree(response.text()).get("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable create response: " + response.text(), e);
        }
    }
}
