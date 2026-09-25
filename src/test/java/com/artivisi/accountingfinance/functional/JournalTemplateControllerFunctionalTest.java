package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.UserTemplatePreferenceService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.SelectOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JournalTemplateController: list with category/search/favorite/tag filters, detail, create/edit/duplicate
 * form with per-line formula preview and whole-template preview, formula validation (syntax and
 * disallowed SpEL constructs), in-place update vs. new version for templates in use, execution, delete,
 * system-template protection and TEMPLATE_* permissions.
 *
 * Seeded templates are used by many other tests to post transactions, so they are only read here;
 * every change is made on templates this class creates (unique names).
 */
@DisplayName("Journal Template Controller Tests")
@Import(ServiceTestDataInitializer.class)
class JournalTemplateControllerFunctionalTest extends PlaywrightTestBase {

    private static final String BEBAN_SEWA = "5.1.05 - Beban Sewa";
    private static final String BANK_BCA = "1.1.02 - Bank BCA";
    private static final String HUTANG_PPH23 = "2.1.21 - Hutang PPh 23";
    private static final Pattern GRAY_STAR = Pattern.compile("(^|\\s)text-gray-300(\\s|$)");
    private static final Pattern AMBER_STAR = Pattern.compile("(^|\\s)text-amber-400(\\s|$)");

    @Autowired
    private JournalTemplateRepository templateRepository;

    @Autowired
    private JournalTemplateService templateService;

    @Autowired
    private UserTemplatePreferenceService preferenceService;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== LIST & FILTERS ====================

    @Test
    @DisplayName("Should list active current templates as cards with cash flow, journal preview, version or system badge and use link")
    void shouldListTemplates() {
        JournalTemplate custom = createTemplate("Daftar " + uid(), false);
        JournalTemplate system = createTemplate("Sistem " + uid(), true);
        JournalTemplate inactive = createTemplate("Nonaktif " + uid(), false);
        templateService.deactivate(inactive.getId());
        loginAsAdmin();

        navigateTo("/templates");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Template Jurnal");
        assertThat(page.locator("#btn-new-template")).isVisible();

        Locator seeded = card("Setoran Modal");
        assertThat(seeded).hasCount(1);
        assertThat(seeded).containsText("Cash Flow: FINANCING");

        Locator customCard = card(custom.getTemplateName());
        assertThat(customCard).containsText("Cash Flow: OPERATING");
        assertThat(customCard).containsText("D: Beban Sewa");
        assertThat(customCard).containsText("K: Bank BCA");
        assertThat(customCard).containsText("amount * 0.98");
        assertThat(customCard).containsText("v1");
        assertThat(customCard.locator("#btn-use-" + custom.getId()))
                .hasAttribute("href", "/transactions/new?templateId=" + custom.getId());

        assertThat(card(system.getTemplateName())).containsText("Sistem");
        assertThat(card(inactive.getTemplateName())).hasCount(0);
    }

    @Test
    @DisplayName("Should filter templates by category chip and by category query parameter")
    void shouldFilterByCategory() {
        loginAsAdmin();
        navigateTo("/templates");
        waitForPageLoad();

        page.locator("a[href='/templates?category=EXPENSE']").click();
        page.waitForURL(Pattern.compile(".*/templates\\?category=EXPENSE$"));
        assertThat(card("Bayar Beban Sewa")).hasCount(1);
        assertThat(card("Setoran Modal")).hasCount(0);
        assertThat(card("Pendapatan Jasa Konsultasi")).hasCount(0);

        navigateTo("/templates?category=RECEIPT");
        waitForPageLoad();
        assertThat(page.locator("a[href='/templates?category=RECEIPT']")).hasClass(Pattern.compile("bg-primary-600"));
        assertThat(card("Setoran Modal")).hasCount(1);
        assertThat(card("Bayar Beban Sewa")).hasCount(0);
        assertThat(page.locator("form a:text-is('Reset')")).isVisible();
    }

    @Test
    @DisplayName("Should search templates by name and description, and show the empty state when nothing matches")
    void shouldSearchTemplates() {
        String id = uid();
        String descriptionTerm = "uraian-" + uid();
        JournalTemplate template = createTemplate("Cari " + id, false);
        template.setDescription("Sewa ruang rapat " + descriptionTerm);
        templateRepository.save(template);
        loginAsAdmin();
        navigateTo("/templates");
        waitForPageLoad();

        page.locator("[data-testid='search-input']").fill("cari " + id);
        page.locator("[data-testid='search-button']").click();
        page.waitForURL(Pattern.compile(".*/templates\\?search=.*"));
        assertThat(page.locator("[data-testid='template-card']")).hasCount(1);
        assertThat(card(template.getTemplateName())).hasCount(1);

        navigateTo("/templates?search=" + descriptionTerm.toUpperCase());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='template-card']")).hasCount(1);
        assertThat(card(template.getTemplateName())).hasCount(1);

        navigateTo("/templates?search=tidak-ada-" + id);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='template-card']")).hasCount(0);
        assertThat(page.locator("#template-grid")).containsText("Tidak ada template yang cocok");
    }

    @Test
    @DisplayName("Should return only the template grid fragment for HTMX requests")
    void shouldReturnGridFragmentForHtmx() {
        loginAsAdmin();

        APIResponse response = page.request().get(baseUrl() + "/templates?category=INCOME",
                RequestOptions.create().setHeader("HX-Request", "true"));

        assertEquals(200, response.status());
        String body = response.text();
        assertTrue(body.contains("<div id=\"template-grid\">"), body);
        assertTrue(body.contains(">Pendapatan Jasa Konsultasi</h3>"));
        assertFalse(body.contains(">Bayar Beban Sewa</h3>"));
        assertFalse(body.contains("page-title"));
        assertFalse(body.contains("<html"));
    }

    // ==================== DETAIL ====================

    @Test
    @DisplayName("Should show seeded template detail: header, lines with fixed and dynamic accounts, AI metadata and actions")
    void shouldShowTemplateDetail() {
        JournalTemplate template = seeded("Pendapatan Jasa Konsultasi");
        loginAsAdmin();

        navigateTo("/templates/" + template.getId());
        waitForPageLoad();

        assertThat(page.locator("#template-name")).hasText("Pendapatan Jasa Konsultasi");
        assertThat(page.locator("#template-name-breadcrumb")).hasText("Pendapatan Jasa Konsultasi");
        assertThat(page.locator("[data-testid='template-category-label']")).hasText("Pendapatan");
        assertThat(page.locator("[data-testid='template-cash-flow']")).hasText("OPERATING");
        assertThat(page.locator("[data-testid='template-type']")).hasText("Custom");
        assertThat(page.locator("#template-version")).hasText(String.valueOf(template.getVersion()));

        Locator lines = page.locator("[data-testid='template-line']");
        assertThat(lines).hasCount(2);
        assertThat(lines.nth(0)).containsText("BANK");
        assertThat(lines.nth(0)).containsText("Dipilih saat input transaksi");
        assertThat(lines.nth(0)).containsText("Debit");
        assertThat(lines.nth(0)).containsText("Penerimaan kas/bank");
        assertThat(lines.nth(0).locator("code")).hasText("amount");
        assertThat(lines.nth(1)).containsText("4.1.02");
        assertThat(lines.nth(1)).containsText("Pendapatan Jasa Konsultasi");
        assertThat(lines.nth(1)).containsText("Akun tetap");
        assertThat(lines.nth(1)).containsText("Kredit");
        assertThat(page.locator("#debit-count")).containsText("1");
        assertThat(page.locator("#credit-count")).containsText("1");

        assertThat(page.locator("[data-testid='semantic-description']"))
                .hasText(template.getSemanticDescription());
        assertThat(page.locator("[data-testid='keywords-badges'] span")).hasCount(template.getKeywords().length);
        assertThat(page.locator("[data-testid='keywords-badges']")).containsText("konsultasi");
        assertThat(page.locator("[data-testid='example-merchants-badges']")).containsText("PT Client A");
        assertThat(page.locator("[data-testid='amount-range']")).hasText("Rp 5.000.000 - Rp 500.000.000");

        assertThat(page.locator("#btn-edit")).hasAttribute("href", "/templates/" + template.getId() + "/edit");
        assertThat(page.locator("#btn-duplicate")).hasAttribute("href", "/templates/" + template.getId() + "/duplicate");
        assertThat(page.locator("#btn-execute")).hasAttribute("href", "/transactions/new?templateId=" + template.getId());
        assertThat(page.locator("#form-delete")).hasCount(1);

        page.locator("[data-testid='metadata-ai-card']").scrollIntoViewIfNeeded();
        takeManualScreenshot("service/templates-metadata-detail");
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should create a template with fixed and dynamic accounts; per-line formula previews follow the preview amount")
    void shouldCreateTemplateThroughForm() {
        String name = "Baru " + uid();
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Template Baru");
        assertThat(page.locator("select[name$='.accountId']")).hasCount(2);
        assertThat(page.locator("input[name='lines[0].position']")).hasValue("DEBIT");
        assertThat(page.locator("input[name='lines[1].position']")).hasValue("CREDIT");
        assertThat(page.locator("#previewAmount")).hasValue("10.000.000");

        page.locator("#templateName").fill(name);
        page.locator("select[name='category']").selectOption("EXPENSE");
        page.locator("select[name='cashFlowCategory']").selectOption("OPERATING");
        page.locator("#description").fill("Sewa kantor dipotong PPh " + name);
        selectAccount(0, BEBAN_SEWA);
        selectAccount(1, BANK_BCA);
        formula(1).fill("amount * 0.9");
        page.locator("[data-testid='btn-add-line']").click();
        page.locator("#btn-credit-2").click();
        page.locator("input[name='lines[2].accountHint']").fill("Hutang PPh");
        formula(2).fill("amount * 0.1");

        assertThat(linePreview(0)).hasText("10.000.000");
        assertThat(linePreview(1)).hasText("9.000.000");
        assertThat(linePreview(2)).hasText("1.000.000");

        page.locator("#previewAmount").fill("2000000");
        assertThat(page.locator("#previewAmount")).hasValue("2.000.000");
        assertThat(linePreview(0)).hasText("2.000.000");
        assertThat(linePreview(1)).hasText("1.800.000");
        assertThat(linePreview(2)).hasText("200.000");

        page.locator("#btn-simpan").click();
        waitForPageLoad();

        JournalTemplate saved = currentByName(name);
        assertEquals(baseUrl() + "/templates/" + saved.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Template berhasil dibuat");
        assertEquals(TemplateCategory.EXPENSE, saved.getCategory());
        assertEquals(CashFlowCategory.OPERATING, saved.getCashFlowCategory());
        assertEquals(TemplateType.SIMPLE, saved.getTemplateType());
        assertEquals("Sewa kantor dipotong PPh " + name, saved.getDescription());
        assertFalse(saved.getIsSystem());
        assertTrue(saved.getActive());
        assertEquals(1, saved.getVersion());
        assertLines(saved,
                "5.1.05|DEBIT|amount",
                "1.1.02|CREDIT|amount * 0.9",
                "-|CREDIT|amount * 0.1");
        assertEquals("Hutang PPh", saved.getLines().get(2).getAccountHint());

        Locator lines = page.locator("[data-testid='template-line']");
        assertThat(lines).hasCount(3);
        assertThat(lines.nth(2)).containsText("Hutang PPh");
        assertThat(lines.nth(2)).containsText("Dipilih saat input transaksi");
        assertThat(lines.nth(2).locator("code")).hasText("amount * 0.1");
    }

    @Test
    @DisplayName("Should show formula errors in the line preview: syntax errors, type references, constructors and bean references")
    void shouldPreviewFormulasAndRejectUnsafeSpel() {
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        formula(1).fill("amount > 5000000 ? amount * 0.02 : 0");
        assertThat(linePreview(1)).hasText("200.000");

        formula(1).fill("amount * * 2");
        assertThat(linePreview(1)).hasText("Error");
        assertThat(linePreview(1).locator("span")).hasAttribute("title", Pattern.compile("^Syntax error: .+"));

        formula(1).fill("T(java.lang.Runtime).getRuntime().exec('id')");
        assertThat(linePreview(1).locator("span"))
                .hasAttribute("title", "Unsupported expression: type reference is not allowed in formulas");

        formula(1).fill("new java.io.File('/tmp').length()");
        assertThat(linePreview(1).locator("span"))
                .hasAttribute("title", "Unsupported expression: constructor is not allowed in formulas");

        formula(1).fill("@journalTemplateService.findAll().size()");
        assertThat(linePreview(1).locator("span"))
                .hasAttribute("title", "Unsupported expression: bean reference is not allowed in formulas");

        JsonNode ok = postJson("/templates/api/formula/preview", Map.of("formula", "amount * 0.11", "amount", 1000000));
        assertTrue(ok.get("valid").asBoolean());
        assertEquals(0, new BigDecimal("110000").compareTo(ok.get("result").decimalValue()));
        assertEquals("110.000", ok.get("formattedResult").asText());

        JsonNode unknownVariable = postJson("/templates/api/formula/preview", Map.of("formula", "fee * 0.11", "amount", 1000000));
        assertFalse(unknownVariable.get("valid").asBoolean());
        assertEquals("Formula evaluation failed", unknownVariable.get("errors").get(0).asText());
    }

    @Test
    @DisplayName("Should reject saving a template without a credit line or with a disallowed formula, keep the input, and save once corrected")
    void shouldRejectInvalidTemplateAndKeepInput() {
        String name = "Tolak " + uid();
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        page.locator("#templateName").fill(name);
        page.locator("select[name='category']").selectOption("EXPENSE");
        selectAccount(0, BEBAN_SEWA);
        selectAccount(1, BANK_BCA);
        page.locator("#btn-debit-1").click();
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/templates", page.url());
        assertThat(page.locator("[data-testid='form-error']"))
                .hasText("Template must have at least one debit and one credit line");
        assertFalse(templateRepository.existsByTemplateNameAndIsCurrentVersionTrue(name));

        assertThat(page.locator("#templateName")).hasValue(name);
        assertThat(page.locator("select[name='category']")).hasValue("EXPENSE");
        assertThat(page.locator("select[name='lines[0].accountId']")).hasValue(accountId("5.1.05"));
        assertThat(page.locator("select[name='lines[1].accountId']")).hasValue(accountId("1.1.02"));
        assertThat(page.locator("input[name='lines[1].position']")).hasValue("DEBIT");

        page.locator("#btn-credit-1").click();
        formula(1).fill("T(java.lang.System).exit(0)");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='form-error']")).hasText(
                "Invalid formula on line 2: Unsupported expression: type reference is not allowed in formulas");
        assertFalse(templateRepository.existsByTemplateNameAndIsCurrentVersionTrue(name));
        assertThat(formula(1)).hasValue("T(java.lang.System).exit(0)");
        assertThat(page.locator("input[name='lines[1].position']")).hasValue("CREDIT");

        formula(1).fill("amount");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        JournalTemplate saved = currentByName(name);
        assertEquals(baseUrl() + "/templates/" + saved.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Template berhasil dibuat");
        assertLines(saved, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount");
    }

    @Test
    @DisplayName("Should preview the whole journal with computed debit/credit amounts without saving the template")
    void shouldPreviewJournalWithoutSaving() {
        String name = "Pratinjau " + uid();
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        page.locator("#templateName").fill(name);
        page.locator("select[name='category']").selectOption("EXPENSE");
        selectAccount(0, BEBAN_SEWA);
        selectAccount(1, BANK_BCA);
        formula(1).fill("amount * 0.98");
        page.locator("[data-testid='btn-add-line']").click();
        page.locator("#btn-credit-2").click();
        selectAccount(2, HUTANG_PPH23);
        formula(2).fill("amount * 0.02");
        page.locator("#previewAmount").fill("5000000");

        page.locator("#btn-preview").click();

        assertThat(page.locator("#preview-modal-title")).hasText("Preview Template: " + name);
        assertThat(page.locator("#preview-amount-display")).containsText("Rp 5.000.000");
        Locator rows = page.locator("#preview-tbody tr");
        assertThat(rows).hasCount(3);
        assertThat(rows.nth(0).locator("td")).hasText(new String[]{BEBAN_SEWA, "5.000.000", "-"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[]{BANK_BCA, "-", "4.900.000"});
        assertThat(rows.nth(2).locator("td")).hasText(new String[]{HUTANG_PPH23, "-", "100.000"});
        assertThat(page.locator("#preview-total-debit")).hasText("5.000.000");
        assertThat(page.locator("#preview-total-kredit")).hasText("5.000.000");
        assertThat(page.locator("#preview-balance-status")).hasText("Jurnal seimbang");

        page.locator("#btn-preview-close").click();
        assertThat(page.locator("#previewModal")).hasCount(0);
        assertFalse(templateRepository.existsByTemplateNameAndIsCurrentVersionTrue(name));
    }

    // ==================== EDIT & VERSIONING ====================

    @Test
    @DisplayName("Should prefill the edit form and update an unused template in place, incrementing its version")
    void shouldUpdateUnusedTemplateInPlace() {
        JournalTemplate template = createTemplate("Ubah " + uid(), false);
        String newName = template.getTemplateName() + " Revisi";
        loginAsAdmin();
        navigateTo("/templates/" + template.getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Edit Template");
        assertThat(page.locator("#templateName")).hasValue(template.getTemplateName());
        assertThat(page.locator("select[name='category']")).hasValue("EXPENSE");
        assertThat(page.locator("select[name='cashFlowCategory']")).hasValue("OPERATING");
        assertThat(page.locator("#description")).hasValue(template.getDescription());
        assertThat(page.locator("select[name$='.accountId']")).hasCount(3);
        assertThat(page.locator("select[name='lines[1].accountId']")).hasValue(accountId("1.1.02"));
        assertThat(formula(1)).hasValue("amount * 0.98");
        assertThat(page.locator("input[name='lines[2].position']")).hasValue("CREDIT");

        page.locator("#templateName").fill(newName);
        formula(1).fill("amount * 0.97");
        formula(2).fill("amount * 0.03");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/templates/" + template.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Template berhasil diperbarui (versi 2)");
        assertThat(page.locator("#template-name")).hasText(newName);
        assertThat(page.locator("#template-version")).hasText("2");

        JournalTemplate updated = reload(template.getId());
        assertEquals(newName, updated.getTemplateName());
        assertEquals(2, updated.getVersion());
        assertTrue(updated.getIsCurrentVersion());
        assertLines(updated, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount * 0.97", "2.1.21|CREDIT|amount * 0.03");
        assertEquals(1, templateRepository.findAllVersions(template.getId()).size());
    }

    @Test
    @DisplayName("Should keep a template used by a transaction and save the edit as a new current version")
    void shouldCreateNewVersionWhenTemplateInUse() {
        JournalTemplate template = createTemplate("Versi " + uid(), false);
        templateService.addTag(template.getId(), "versi");
        String newName = template.getTemplateName() + " v2";
        loginAsAdmin();
        UUID transactionId = UUID.fromString(executeTemplate(template.getId(), 1000000).get("transactionId").asText());
        navigateTo("/templates/" + template.getId() + "/edit");
        waitForPageLoad();

        page.locator("#templateName").fill(newName);
        formula(1).fill("amount * 0.975");
        formula(2).fill("amount * 0.025");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        JournalTemplate newVersion = currentByName(newName);
        assertNotEquals(template.getId(), newVersion.getId());
        assertEquals(baseUrl() + "/templates/" + newVersion.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Template berhasil diperbarui (versi 2)");
        assertEquals(2, newVersion.getVersion());
        assertTrue(newVersion.getIsCurrentVersion());
        assertEquals(template.getId(), newVersion.getRootTemplateId());
        assertEquals(1, newVersion.getUsageCount());
        assertEquals(List.of("versi"), templateService.getTagsForTemplate(newVersion.getId()));
        assertLines(newVersion, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount * 0.975", "2.1.21|CREDIT|amount * 0.025");

        JournalTemplate original = reload(template.getId());
        assertFalse(original.getIsCurrentVersion());
        assertEquals(template.getTemplateName(), original.getTemplateName());
        assertEquals(1, original.getVersion());
        assertLines(original, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount * 0.98", "2.1.21|CREDIT|amount * 0.02");
        assertEquals(template.getId(),
                transactionRepository.findById(transactionId).orElseThrow().getJournalTemplate().getId());

        navigateTo("/templates");
        waitForPageLoad();
        assertThat(card(template.getTemplateName())).hasCount(0);
        assertThat(card(newName)).containsText("v2");
    }

    // ==================== EXECUTE ====================

    @Test
    @DisplayName("Should execute a template into a draft transaction with formula-computed journal lines and record usage")
    void shouldExecuteTemplate() {
        JournalTemplate template = createTemplate("Eksekusi " + uid(), false);
        loginAsAdmin();

        JsonNode result = executeTemplate(template.getId(), 1234567);

        UUID transactionId = UUID.fromString(result.get("transactionId").asText());
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        assertEquals(TransactionStatus.DRAFT, transaction.getStatus());
        assertEquals(template.getId(), transaction.getJournalTemplate().getId());
        assertEquals(0, new BigDecimal("1234567").compareTo(transaction.getAmount()));
        List<String> entries = new ArrayList<>();
        for (JournalEntry entry : journalEntryRepository.findByTransactionIdWithAccount(transactionId)) {
            entries.add(entry.getAccount().getAccountCode() + "|" + entry.getDebitAmount().stripTrailingZeros().toPlainString()
                    + "|" + entry.getCreditAmount().stripTrailingZeros().toPlainString());
        }
        entries.sort(null);
        assertEquals(List.of("1.1.02|0|1209876", "2.1.21|0|24691", "5.1.05|1234567|0"), entries);

        JournalTemplate used = reload(template.getId());
        assertEquals(1, used.getUsageCount());
        assertNotNull(used.getLastUsedAt());

        APIResponse recent = page.request().get(baseUrl() + "/templates/api/recent");
        assertEquals(200, recent.status());
        List<String> recentIds = new ArrayList<>();
        for (JsonNode node : readJson(recent)) {
            assertFalse(node.get("lastUsedAt").isNull(), "Never-used template listed as recent: " + node.get("templateName"));
            recentIds.add(node.get("id").asText());
        }
        assertTrue(recentIds.contains(template.getId().toString()));

        navigateTo("/templates");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='recent-template']:has-text('" + template.getTemplateName() + "')"))
                .hasAttribute("href", "/transactions/new?templateId=" + template.getId());

        navigateTo("/templates/" + template.getId() + "/execute");
        page.waitForURL(baseUrl() + "/transactions/new?templateId=" + template.getId());
        assertThat(page.locator("h3:has-text('Template:')")).containsText(template.getTemplateName());
    }

    // ==================== DUPLICATE ====================

    @Test
    @DisplayName("Should duplicate a template: form prefilled from the source, saved as a new template, source unchanged")
    void shouldDuplicateTemplate() {
        JournalTemplate source = createTemplate("Sumber " + uid(), false);
        source.setSemanticDescription("Deskripsi AI " + source.getTemplateName());
        source = templateRepository.save(source);
        String copyName = source.getTemplateName() + " Salinan";
        loginAsAdmin();
        navigateTo("/templates/" + source.getId());
        waitForPageLoad();

        page.locator("#btn-duplicate").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/templates/" + source.getId() + "/duplicate", page.url());
        assertThat(page.locator("#page-title")).hasText("Duplikat Template");
        assertThat(page.locator("text=Menduplikat dari: " + source.getTemplateName())).isVisible();
        assertThat(page.locator("#templateName")).hasValue(source.getTemplateName());
        assertThat(page.locator("select[name='category']")).hasValue("EXPENSE");
        assertThat(page.locator("select[name$='.accountId']")).hasCount(3);
        assertThat(page.locator("select[name='lines[2].accountId']")).hasValue(accountId("2.1.21"));
        assertThat(formula(2)).hasValue("amount * 0.02");
        assertThat(page.locator("#semanticDescription")).hasValue("Deskripsi AI " + source.getTemplateName());

        page.locator("#templateName").fill(copyName);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        JournalTemplate copy = currentByName(copyName);
        assertNotEquals(source.getId(), copy.getId());
        assertEquals(baseUrl() + "/templates/" + copy.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Template berhasil dibuat");
        assertEquals(1, copy.getVersion());
        assertEquals(copy.getId(), copy.getRootTemplateId());
        assertLines(copy, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount * 0.98", "2.1.21|CREDIT|amount * 0.02");
        assertEquals("Deskripsi AI " + source.getTemplateName(), copy.getSemanticDescription());

        JournalTemplate unchanged = reload(source.getId());
        assertEquals(source.getTemplateName(), unchanged.getTemplateName());
        assertTrue(unchanged.getIsCurrentVersion());
        assertEquals(1, unchanged.getVersion());
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("Should delete an unused template only after confirmation")
    void shouldDeleteTemplateAfterConfirmation() {
        JournalTemplate template = createTemplate("Hapus " + uid(), false);
        loginAsAdmin();
        navigateTo("/templates/" + template.getId());
        waitForPageLoad();

        page.onceDialog(dialog -> {
            assertEquals("Apakah Anda yakin ingin menghapus template ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#btn-delete").click();
        assertEquals(baseUrl() + "/templates/" + template.getId(), page.url());
        assertTrue(templateRepository.findById(template.getId()).isPresent());

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-delete").click();
        page.waitForURL(baseUrl() + "/templates");

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Template berhasil dihapus");
        assertThat(card(template.getTemplateName())).hasCount(0);
        assertTrue(templateRepository.findById(template.getId()).isEmpty());
    }

    @Test
    @DisplayName("Should refuse to delete a template used by a transaction and show why")
    void shouldRefuseDeletingTemplateInUse() {
        JournalTemplate template = createTemplate("Terpakai " + uid(), false);
        loginAsAdmin();
        executeTemplate(template.getId(), 500000);
        navigateTo("/templates/" + template.getId());
        waitForPageLoad();

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-delete").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/templates/" + template.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-error']"))
                .hasText("Template tidak dapat dihapus karena sudah digunakan oleh data lain");
        JournalTemplate kept = reload(template.getId());
        assertEquals(3, kept.getLines().size());
        assertTrue(kept.getIsCurrentVersion());
    }

    // ==================== SYSTEM TEMPLATES ====================

    @Test
    @DisplayName("Should hide edit/delete for system templates and reject update, delete and deactivate on the server")
    void shouldProtectSystemTemplates() {
        JournalTemplate system = createTemplate("Sistem " + uid(), true);
        loginAsAdmin();
        navigateTo("/templates/" + system.getId());
        waitForPageLoad();

        assertThat(page.locator("[data-testid='template-type']")).hasText("Sistem");
        assertThat(page.locator("#btn-edit")).hasCount(0);
        assertThat(page.locator("#form-delete")).hasCount(0);
        String csrf = csrf();

        navigateTo("/templates/" + system.getId() + "/edit");
        waitForPageLoad();
        page.locator("#templateName").fill(system.getTemplateName() + " Diubah");
        formula(1).fill("amount * 0.5");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='form-error']")).hasText("Cannot modify system template");
        assertThat(page.locator("form[data-testid='template-form']"))
                .hasAttribute("action", "/templates/" + system.getId());

        APIResponse delete = page.request().post(baseUrl() + "/templates/" + system.getId() + "/delete",
                RequestOptions.create().setForm(FormData.create().set("_csrf", csrf)));
        assertEquals(200, delete.status());
        assertEquals(baseUrl() + "/templates/" + system.getId(), delete.url());
        assertTrue(delete.text().contains("Cannot delete system template"));

        Map<String, Object> body = templateJson(system.getTemplateName() + " API");
        assertEquals(409, page.request().put(baseUrl() + "/templates/api/" + system.getId(),
                withCsrf(csrf).setData(body)).status());
        assertEquals(409, page.request().delete(baseUrl() + "/templates/api/" + system.getId(), withCsrf(csrf)).status());
        assertEquals(409, page.request().post(baseUrl() + "/templates/api/" + system.getId() + "/deactivate",
                withCsrf(csrf)).status());

        JournalTemplate unchanged = reload(system.getId());
        assertEquals(system.getTemplateName(), unchanged.getTemplateName());
        assertTrue(unchanged.getActive());
        assertTrue(unchanged.getIsCurrentVersion());
        assertEquals(1, unchanged.getVersion());
        assertLines(unchanged, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount * 0.98", "2.1.21|CREDIT|amount * 0.02");
    }

    // ==================== FAVORITES & TAGS ====================

    @Test
    @DisplayName("Should toggle a favorite from the detail page and the list card, and filter the list by favorites")
    void shouldToggleFavorite() {
        JournalTemplate template = createTemplate("Favorit " + uid(), false);
        loginAsAdmin();
        navigateTo("/templates/" + template.getId());
        waitForPageLoad();

        Locator star = page.locator("[data-testid='favorite-button']");
        assertThat(star).hasClass(GRAY_STAR);
        star.click();
        assertThat(star).hasClass(AMBER_STAR);
        assertTrue(preferenceService.isFavorite("admin", template.getId()));

        navigateTo("/templates");
        waitForPageLoad();
        page.locator("[data-testid='filter-favorites']").click();
        page.waitForURL(baseUrl() + "/templates?favorites=true");
        Locator favoriteCard = card(template.getTemplateName());
        assertThat(favoriteCard).hasCount(1);
        assertThat(card("Setoran Modal")).hasCount(0);

        Locator cardStar = favoriteCard.locator("[data-testid='favorite-button']");
        assertThat(cardStar).hasClass(AMBER_STAR);
        cardStar.click();
        assertThat(cardStar).hasClass(GRAY_STAR);
        assertFalse(preferenceService.isFavorite("admin", template.getId()));

        navigateTo("/templates?favorites=true");
        waitForPageLoad();
        assertThat(card(template.getTemplateName())).hasCount(0);
    }

    @Test
    @DisplayName("Should add a normalized tag, filter the list by it, and remove it")
    void shouldAddFilterAndRemoveTag() {
        String id = uid();
        String tag = "uji-" + id;
        JournalTemplate template = createTemplate("Tag " + id, false);
        loginAsAdmin();
        navigateTo("/templates/" + template.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='detail-tag']")).hasCount(0);

        page.locator("[data-testid='tag-input']").fill("  UJI-" + id.toUpperCase() + " ");
        page.locator("[data-testid='add-tag-button']").click();

        assertThat(page.locator("[data-testid='detail-tag']")).hasText(new String[]{tag});
        assertEquals(List.of(tag), templateService.getTagsForTemplate(template.getId()));

        navigateTo("/templates?tag=" + tag);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='template-card']")).hasCount(1);
        assertThat(card(template.getTemplateName()).locator("[data-testid='template-tag']")).hasText(tag);
        assertThat(page.locator("[data-testid='tag-filter']:text-is('" + tag + "')")).hasClass(Pattern.compile("bg-blue-600"));

        navigateTo("/templates/" + template.getId());
        waitForPageLoad();
        page.locator("[data-testid='detail-tag'] [data-testid='remove-tag-button']").click();
        assertThat(page.locator("[data-testid='detail-tag']")).hasCount(0);
        assertTrue(templateService.getTagsForTemplate(template.getId()).isEmpty());

        navigateTo("/templates?tag=" + tag);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='template-card']")).hasCount(0);
    }

    // ==================== AI METADATA ====================

    @Test
    @DisplayName("Should create a template with AI metadata and show it on the detail page")
    void shouldCreateTemplateWithMetadata() {
        String name = "Metadata " + uid();
        loginAsAdmin();
        navigateTo("/templates/new");
        waitForPageLoad();

        page.locator("#templateName").fill(name);
        page.locator("select[name='category']").selectOption("EXPENSE");
        page.locator("select[name='cashFlowCategory']").selectOption("OPERATING");
        selectAccount(0, BEBAN_SEWA);
        selectAccount(1, BANK_BCA);
        page.locator("[data-testid='metadata-ai-section'] summary").click();
        page.locator("#semanticDescription").fill("New template with metadata");
        page.locator("#keywordsText").fill("new, test, , create");
        page.locator("#typicalAmountMin").fill("50000");
        page.locator("#typicalAmountMax").fill("1000000");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        JournalTemplate saved = currentByName(name);
        assertEquals(baseUrl() + "/templates/" + saved.getId(), page.url());
        assertEquals("New template with metadata", saved.getSemanticDescription());
        assertEquals(List.of("new", "test", "create"), List.of(saved.getKeywords()));
        assertEquals(0, new BigDecimal("50000").compareTo(saved.getTypicalAmountMin()));
        assertEquals(0, new BigDecimal("1000000").compareTo(saved.getTypicalAmountMax()));
        assertNull(saved.getExampleMerchants());
        assertNull(saved.getMerchantPatterns());

        assertThat(page.locator("[data-testid='semantic-description']")).hasText("New template with metadata");
        assertThat(page.locator("[data-testid='keywords-badges'] span")).hasText(new String[]{"new", "test", "create"});
        assertThat(page.locator("[data-testid='amount-range']")).hasText("Rp 50.000 - Rp 1.000.000");
        assertThat(page.locator("[data-testid='example-merchants-badges']")).hasCount(0);
    }

    @Test
    @DisplayName("Should edit AI metadata of a template and show every field on the detail page")
    void shouldEditAiMetadata() {
        JournalTemplate template = createTemplate("Edit Metadata " + uid(), false);
        loginAsAdmin();
        navigateTo("/templates/" + template.getId() + "/edit");
        waitForPageLoad();

        page.locator("[data-testid='metadata-ai-section'] summary").click();
        page.locator("[data-testid='metadata-ai-section']").scrollIntoViewIfNeeded();
        takeManualScreenshot("service/templates-metadata-form");

        page.locator("#semanticDescription").fill("Test semantic description for automation");
        page.locator("#keywordsText").fill("test, automation, metadata");
        page.locator("#exampleMerchantsText").fill("Merchant A, Merchant B");
        page.locator("#typicalAmountMin").fill("100000");
        page.locator("#typicalAmountMax").fill("5000000");
        page.locator("#merchantPatternsText").fill(".*test.*, .*auto.*");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/templates/" + template.getId(), page.url());
        assertThat(page.locator("[data-testid='semantic-description']")).hasText("Test semantic description for automation");
        assertThat(page.locator("[data-testid='keywords-badges'] span")).hasText(new String[]{"test", "automation", "metadata"});
        assertThat(page.locator("[data-testid='example-merchants-badges'] span")).hasText(new String[]{"Merchant A", "Merchant B"});
        assertThat(page.locator("[data-testid='amount-range']")).hasText("Rp 100.000 - Rp 5.000.000");
        assertThat(page.locator("[data-testid='merchant-patterns-badges'] code")).hasText(new String[]{".*test.*", ".*auto.*"});

        JournalTemplate updated = reload(template.getId());
        assertEquals(List.of("Merchant A", "Merchant B"), List.of(updated.getExampleMerchants()));
        assertEquals(List.of(".*test.*", ".*auto.*"), List.of(updated.getMerchantPatterns()));
        assertLines(updated, "5.1.05|DEBIT|amount", "1.1.02|CREDIT|amount * 0.98", "2.1.21|CREDIT|amount * 0.02");
    }

    // ==================== JSON API ====================

    @Test
    @DisplayName("Should serve templates as JSON by category, search and id; unknown id is 404")
    void shouldServeTemplatesAsJson() {
        String id = uid();
        JournalTemplate template = createTemplate("Json " + id, false);
        templateService.addTag(template.getId(), "json");
        loginAsAdmin();

        List<String> incomeNames = new ArrayList<>();
        for (JsonNode node : readJson(page.request().get(baseUrl() + "/templates/api?category=INCOME"))) {
            assertEquals("INCOME", node.get("category").asText(), node.toString());
            incomeNames.add(node.get("templateName").asText());
        }
        assertTrue(incomeNames.contains("Pendapatan Jasa Konsultasi"));
        assertFalse(incomeNames.contains("Bayar Beban Sewa"));

        JsonNode search = readJson(page.request().get(baseUrl() + "/templates/api/search?q=json%20" + id));
        assertEquals(1, search.get("content").size());
        assertEquals(template.getId().toString(), search.get("content").get(0).get("id").asText());

        JsonNode one = readJson(page.request().get(baseUrl() + "/templates/api/" + template.getId()));
        assertEquals(template.getTemplateName(), one.get("templateName").asText());
        assertEquals("EXPENSE", one.get("category").asText());
        assertEquals("OPERATING", one.get("cashFlowCategory").asText());
        assertEquals(1, one.get("version").asInt());
        assertEquals("json", one.get("tagNames").get(0).asText());

        assertEquals(404, page.request().get(baseUrl() + "/templates/api/" + UUID.randomUUID()).status());
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should let a view-only user browse templates but not create, edit, delete, tag or execute them")
    void shouldDenyChangesWithoutTemplatePermissions() {
        JournalTemplate template = createTemplate("Auditor " + uid(), false);
        templateService.addTag(template.getId(), "audit");
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/templates");
        waitForPageLoad();
        assertThat(card(template.getTemplateName())).hasCount(1);
        assertThat(page.locator("#btn-new-template")).hasCount(0);

        navigateTo("/templates/" + template.getId());
        waitForPageLoad();
        assertThat(page.locator("#template-name")).hasText(template.getTemplateName());
        assertThat(page.locator("#btn-edit")).hasCount(0);
        assertThat(page.locator("#form-delete")).hasCount(0);
        assertThat(page.locator("#btn-duplicate")).hasCount(0);
        assertThat(page.locator("[data-testid='detail-tag']")).hasText(new String[]{"audit"});
        assertThat(page.locator("[data-testid='tag-input']")).hasCount(0);
        assertThat(page.locator("[data-testid='remove-tag-button']")).hasCount(0);
        String csrf = csrf();

        for (String path : List.of("/templates/new", "/templates/" + template.getId() + "/edit",
                "/templates/" + template.getId() + "/duplicate")) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page.locator("#access-denied-page")).isVisible();
        }

        String name = "Ilegal " + uid();
        assertEquals(403, postForm("/templates", templateForm(csrf, name)).status());
        assertEquals(403, postForm("/templates/" + template.getId(), templateForm(csrf, name)).status());
        assertEquals(403, postForm("/templates/" + template.getId() + "/delete", FormData.create().set("_csrf", csrf)).status());
        assertEquals(403, postForm("/templates/" + template.getId() + "/tags",
                FormData.create().set("_csrf", csrf).set("tag", "ilegal")).status());
        assertEquals(403, postForm("/templates/" + template.getId() + "/tags/audit/delete",
                FormData.create().set("_csrf", csrf)).status());
        assertEquals(403, page.request().post(baseUrl() + "/templates/api",
                withCsrf(csrf).setData(templateJson(name))).status());
        assertEquals(403, page.request().put(baseUrl() + "/templates/api/" + template.getId(),
                withCsrf(csrf).setData(templateJson(name))).status());
        assertEquals(403, page.request().delete(baseUrl() + "/templates/api/" + template.getId(), withCsrf(csrf)).status());
        assertEquals(403, page.request().post(baseUrl() + "/templates/api/" + template.getId() + "/deactivate",
                withCsrf(csrf)).status());
        assertEquals(403, page.request().post(baseUrl() + "/templates/api/" + template.getId() + "/execute",
                withCsrf(csrf).setData(Map.of("transactionDate", "2030-06-15", "amount", 1000,
                        "description", "Ilegal"))).status());

        assertFalse(templateRepository.existsByTemplateNameAndIsCurrentVersionTrue(name));
        JournalTemplate unchanged = reload(template.getId());
        assertEquals(template.getTemplateName(), unchanged.getTemplateName());
        assertTrue(unchanged.getActive());
        assertEquals(0, unchanged.getUsageCount());
        assertEquals(List.of("audit"), templateService.getTagsForTemplate(template.getId()));
    }

    // ==================== helpers ====================

    private JournalTemplate createTemplate(String name, boolean system) {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName(name);
        template.setCategory(TemplateCategory.EXPENSE);
        template.setCashFlowCategory(CashFlowCategory.OPERATING);
        template.setTemplateType(TemplateType.SIMPLE);
        template.setDescription("Deskripsi " + name);
        template.setIsSystem(system);
        template.addLine(line(1, "5.1.05", JournalPosition.DEBIT, "amount"));
        template.addLine(line(2, "1.1.02", JournalPosition.CREDIT, "amount * 0.98"));
        template.addLine(line(3, "2.1.21", JournalPosition.CREDIT, "amount * 0.02"));
        return templateService.create(template);
    }

    private JournalTemplateLine line(int order, String accountCode, JournalPosition position, String formula) {
        JournalTemplateLine line = new JournalTemplateLine();
        line.setLineOrder(order);
        line.setAccount(account(accountCode));
        line.setPosition(position);
        line.setFormula(formula);
        return line;
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private String accountId(String code) {
        return account(code).getId().toString();
    }

    private JournalTemplate seeded(String name) {
        return templateRepository.findByTemplateNameWithLines(name)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + name));
    }

    private JournalTemplate currentByName(String name) {
        return templateRepository.findByTemplateNameWithLines(name)
                .orElseThrow(() -> new AssertionError("Template not saved: " + name));
    }

    private JournalTemplate reload(UUID id) {
        return templateRepository.findByIdWithLines(id).orElseThrow();
    }

    private void assertLines(JournalTemplate template, String... expected) {
        List<String> actual = new ArrayList<>();
        for (JournalTemplateLine line : template.getLines()) {
            actual.add((line.getAccount() == null ? "-" : line.getAccount().getAccountCode())
                    + "|" + line.getPosition() + "|" + line.getFormula());
        }
        assertEquals(List.of(expected), actual);
    }

    private Locator card(String templateName) {
        return page.locator("[data-testid='template-card']:has(h3:text-is('" + templateName + "'))");
    }

    private Locator formula(int index) {
        return page.locator("input[name='lines[" + index + "].formula']");
    }

    private Locator linePreview(int index) {
        return page.locator("[data-testid='line-preview-" + index + "']");
    }

    private void selectAccount(int index, String label) {
        page.locator("select[name='lines[" + index + "].accountId']").selectOption(new SelectOption().setLabel(label));
    }

    private RequestOptions withCsrf(String csrf) {
        return RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf);
    }

    private String csrf() {
        return page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private APIResponse postForm(String path, FormData form) {
        return page.request().post(baseUrl() + path, RequestOptions.create().setMaxRedirects(0).setForm(form));
    }

    private FormData templateForm(String csrf, String name) {
        return FormData.create().set("_csrf", csrf).set("templateName", name).set("category", "EXPENSE")
                .set("cashFlowCategory", "OPERATING").set("templateType", "SIMPLE")
                .set("lines[0].accountId", accountId("5.1.05")).set("lines[0].position", "DEBIT")
                .set("lines[0].formula", "amount").set("lines[0].lineOrder", "1")
                .set("lines[1].accountId", accountId("1.1.02")).set("lines[1].position", "CREDIT")
                .set("lines[1].formula", "amount").set("lines[1].lineOrder", "2");
    }

    private Map<String, Object> templateJson(String name) {
        return Map.of("templateName", name, "category", "EXPENSE", "cashFlowCategory", "OPERATING",
                "templateType", "SIMPLE", "lines", List.of(
                        Map.of("accountId", accountId("5.1.05"), "position", "DEBIT", "formula", "amount", "lineOrder", 1),
                        Map.of("accountId", accountId("1.1.02"), "position", "CREDIT", "formula", "amount", "lineOrder", 2)));
    }

    private JsonNode executeTemplate(UUID templateId, long amount) {
        APIResponse response = page.request().post(baseUrl() + "/templates/api/" + templateId + "/execute",
                withCsrf(csrf()).setData(Map.of("transactionDate", "2030-06-15", "amount", amount,
                        "description", "Eksekusi template " + templateId)));
        assertEquals(200, response.status(), response.text());
        return readJson(response);
    }

    private JsonNode postJson(String path, Map<String, Object> body) {
        APIResponse response = page.request().post(baseUrl() + path, withCsrf(csrf()).setData(body));
        assertEquals(200, response.status(), response.text());
        return readJson(response);
    }

    private JsonNode readJson(APIResponse response) {
        assertEquals(200, response.status(), response.text());
        try {
            return objectMapper.readTree(response.text());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON: " + response.text(), e);
        }
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("tpl-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("tpl-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
