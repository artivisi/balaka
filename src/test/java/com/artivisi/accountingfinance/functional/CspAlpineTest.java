package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AnalysisReport;
import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.enums.BankStatementParserType;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.ReconciliationStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AnalysisReportRepository;
import com.artivisi.accountingfinance.repository.BankReconciliationRepository;
import com.artivisi.accountingfinance.repository.BankStatementParserConfigRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.service.BankReconciliationService;
import com.artivisi.accountingfinance.service.BankStatementImportService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Security Policy enforcement and the Alpine.js CSP build.
 *
 * Pages are loaded in a browser context without PlaywrightTestBase's animation-disabling
 * init script: that script injects an un-nonced &lt;style&gt; element, which the policy
 * blocks and which would otherwise show up as a violation on every page.
 */
@DisplayName("CSP and Alpine.js CSP build")
@Import(ServiceTestDataInitializer.class)
class CspAlpineTest extends PlaywrightTestBase {

    private static final Path TEMPLATES_DIR = Paths.get("src/main/resources/templates");
    private static final Path ALPINE_COMPONENTS_JS = Paths.get("src/main/resources/static/js/alpine-components.js");
    private static final Pattern NONCE_SOURCE = Pattern.compile("'nonce-([A-Za-z0-9+/=]+)'");
    private static final Pattern TAG_ATTRIBUTES = Pattern.compile("<(script|style)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NONCE_ATTRIBUTE = Pattern.compile("\\snonce=\"([^\"]*)\"");
    private static final Pattern ALPINE_ATTRIBUTE = Pattern.compile(
            "\\s(x-[a-z][a-z0-9:.-]*|@[a-z][a-z0-9:.-]*|:[a-z][a-z0-9-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern NON_EXPRESSION_DIRECTIVE = Pattern.compile("^x-(ref|cloak|transition|teleport|collapse|ignore)\\b.*");
    private static final Pattern COMPONENT_REGISTRATION = Pattern.compile("Alpine\\.data\\(\\s*['\"]([A-Za-z_$][\\w$]*)['\"]");
    private static final Pattern COMPONENT_REFERENCE = Pattern.compile("^([A-Za-z_$][\\w$]*)\\s*(\\(.*\\))?$", Pattern.DOTALL);
    private static final String BCA_STATEMENT_CSV = "src/test/resources/testdata/bank-reconciliation/bca-statement-202401.csv";
    private static final int FISCAL_ADJUSTMENT_YEAR = 2076;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private AnalysisReportRepository analysisReportRepository;

    @Autowired
    private FiscalAdjustmentRepository fiscalAdjustmentRepository;

    @Autowired
    private BankStatementParserConfigRepository parserConfigRepository;

    @Autowired
    private CompanyBankAccountRepository bankAccountRepository;

    @Autowired
    private BankReconciliationRepository reconciliationRepository;

    @Autowired
    private BankStatementImportService statementImportService;

    @Autowired
    private BankReconciliationService reconciliationService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceService invoiceService;

    private final List<String> violations = new CopyOnWriteArrayList<>();

    @BeforeEach
    void openCspEnforcingPage() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setLocale("id-ID"));
        context.exposeBinding("reportCspViolation", (source, args) -> {
            violations.add(String.valueOf(args[0]));
            return null;
        });
        context.addInitScript("""
                document.addEventListener('securitypolicyviolation', e => window.reportCspViolation(
                    e.violatedDirective + ' blocked ' + (e.blockedURI || 'inline') + ' at ' + e.sourceFile + ':' + e.lineNumber));
                """);
        page = context.newPage();
        page.setDefaultTimeout(15000);
        page.onConsoleMessage(msg -> {
            String text = msg.text();
            if (text.contains("Content Security Policy") || text.startsWith("Alpine")) {
                violations.add("console " + msg.type() + ": " + text);
            }
        });
        page.onPageError(error -> violations.add("page error: " + error));
    }

    @Test
    @DisplayName("Every response carries one CSP header with a fresh nonce shared by all inline scripts and styles")
    void cspHeaderCarriesFreshNonceMatchingInlineBlocks() {
        String firstLoginNonce = assertCspAndInlineBlocks("/login", false);
        String secondLoginNonce = assertCspAndInlineBlocks("/login", false);
        assertThat(secondLoginNonce).isNotEqualTo(firstLoginNonce);

        loginAsAdmin();
        String dashboardNonce = assertCspAndInlineBlocks("/dashboard", true);
        String formNonce = assertCspAndInlineBlocks("/templates/new", true);
        assertThat(Set.of(firstLoginNonce, secondLoginNonce, dashboardNonce, formNonce)).hasSize(4);

        APIResponse missing = page.request().get(baseUrl() + "/no-such-page-" + UUID.randomUUID());
        assertThat(missing.status()).isEqualTo(404);
        assertSinglePolicy(missing);
    }

    @Test
    @DisplayName("Templates have no inline event handlers, style attributes, javascript: URLs or un-nonced script/style blocks")
    void templatesContainOnlyCspCompliantMarkup() throws IOException {
        Pattern inlineHandler = Pattern.compile("\\son[a-z]+\\s*=\\s*[\"']");
        Pattern styleAttribute = Pattern.compile("(\\s|th:)style\\s*=\\s*[\"']");
        Pattern javascriptUrl = Pattern.compile("(href|src|action)\\s*=\\s*[\"']\\s*javascript:", Pattern.CASE_INSENSITIVE);
        List<String> offenders = new ArrayList<>();

        for (Path template : templateFiles()) {
            String source = Files.readString(template);
            collect(offenders, template, source, inlineHandler, "inline event handler");
            collect(offenders, template, source, styleAttribute, "style attribute");
            collect(offenders, template, source, javascriptUrl, "javascript: URL");
            Matcher tag = TAG_ATTRIBUTES.matcher(source);
            while (tag.find()) {
                String attributes = tag.group(2);
                boolean external = tag.group(1).equalsIgnoreCase("script") && attributes.contains("src=");
                if (!external && !attributes.contains("nonce=")) {
                    offenders.add(location(template, source, tag.start()) + " <" + tag.group(1) + "> without nonce");
                }
            }
        }

        assertThat(offenders).as("CSP-blocked markup in templates").isEmpty();
    }

    @Test
    @DisplayName("Every Alpine expression in the templates parses with the CSP build's expression parser")
    void alpineExpressionsParseWithCspBuild() throws IOException {
        List<Map<String, String>> expressions = new ArrayList<>();
        for (Path template : templateFiles()) {
            String source = Files.readString(template);
            Matcher m = ALPINE_ATTRIBUTE.matcher(source);
            while (m.find()) {
                String directive = m.group(1);
                String expression = decodeEntities(m.group(3) != null ? m.group(3) : m.group(4)).trim();
                if (expression.isEmpty() || NON_EXPRESSION_DIRECTIVE.matcher(directive).matches()) {
                    continue;
                }
                if (directive.equals("x-for")) {
                    expression = expression.replaceFirst("^.*?\\s(in|of)\\s", "");
                }
                expressions.add(Map.of(
                        "location", location(template, source, m.start()) + " " + directive,
                        "expression", expression));
            }
        }
        assertThat(expressions).as("Alpine attributes found in templates").hasSizeGreaterThan(400);

        loginAsAdmin();
        @SuppressWarnings("unchecked")
        List<String> parseFailures = (List<String>) page.evaluate("""
                entries => {
                    const failures = [];
                    let current = null;
                    Alpine.setErrorHandler(error => {
                        if (current && String(error.message).startsWith('CSP Parser Error')) {
                            failures.push(current.location + '="' + current.expression + '" -> ' + error.message);
                        }
                    });
                    for (const entry of entries) {
                        current = entry;
                        Alpine.evaluate(document.createElement('div'), entry.expression);
                    }
                    current = null;
                    return failures;
                }""", expressions);

        assertThat(parseFailures)
                .as("Expressions the Alpine CSP build rejects (the handler silently does nothing)")
                .isEmpty();
    }

    @Test
    @DisplayName("Every x-data component referenced in the templates is registered with Alpine.data()")
    void everyReferencedComponentIsRegistered() throws IOException {
        Set<String> registered = new HashSet<>();
        List<String> sources = new ArrayList<>();
        sources.add(Files.readString(ALPINE_COMPONENTS_JS));
        for (Path template : templateFiles()) {
            sources.add(Files.readString(template));
        }
        for (String source : sources) {
            Matcher m = COMPONENT_REGISTRATION.matcher(source);
            while (m.find()) {
                registered.add(m.group(1));
            }
        }

        List<String> unregistered = new ArrayList<>();
        int references = 0;
        for (Path template : templateFiles()) {
            String source = Files.readString(template);
            Matcher m = ALPINE_ATTRIBUTE.matcher(source);
            while (m.find()) {
                if (!m.group(1).equals("x-data")) {
                    continue;
                }
                Matcher reference = COMPONENT_REFERENCE.matcher(decodeEntities(m.group(3) != null ? m.group(3) : m.group(4)).trim());
                if (reference.matches()) {
                    references++;
                    if (!registered.contains(reference.group(1))) {
                        unregistered.add(location(template, source, m.start()) + " x-data=\"" + reference.group(1) + "\"");
                    }
                }
            }
        }

        assertThat(references).as("x-data component references in templates").isGreaterThan(20);
        assertThat(unregistered).as("x-data components never registered via Alpine.data()").isEmpty();
    }

    @Test
    @DisplayName("Key pages render without CSP violations or Alpine errors")
    void keyPagesRenderWithoutViolations() {
        UUID templateId = seededTemplateId();
        loginAsAdmin();

        List<String> paths = List.of(
                "/dashboard",
                "/transactions",
                "/transactions/new?templateId=" + templateId,
                "/transactions/journal-entry/new",
                "/drafts",
                "/journals",
                "/templates",
                "/templates/new",
                "/accounts",
                "/accounts/new",
                "/reports",
                "/reports/rekonsiliasi-fiskal?year=" + FISCAL_ADJUSTMENT_YEAR,
                "/clients/new",
                "/projects",
                "/projects/new",
                "/invoices",
                "/invoices/new",
                "/vendors/new",
                "/bills",
                "/bills/new",
                "/products/new",
                "/assets/new",
                "/amortization/new",
                "/recurring/new",
                "/employees/new",
                "/salary-components/new",
                "/payroll",
                "/payroll/new",
                "/fiscal-periods",
                "/tax-calendar",
                "/bank-reconciliation/parser-configs",
                "/analysis-reports",
                "/settings",
                "/settings/import",
                "/users/new",
                "/device/success");

        Map<String, List<String>> violationsByPage = new LinkedHashMap<>();
        for (String path : paths) {
            violations.clear();
            Response response = page.navigate(baseUrl() + path);
            assertThat(response.status()).as("HTTP status of " + path).isEqualTo(200);
            waitForPageLoad();
            if (!violations.isEmpty()) {
                violationsByPage.put(path, List.copyOf(violations));
            }
        }

        assertThat(violationsByPage).isEmpty();
    }

    @Test
    @DisplayName("Collapsed sidebar groups expand on click without CSP violations")
    void sidebarGroupsExpand() {
        loginAsAdmin();
        violations.clear();

        assertThat(page.locator("#nav-transaksi")).isVisible();
        Map<String, String> groupToLink = Map.of(
                "#nav-group-laporan", "#nav-laporan",
                "#nav-group-proyek", "#nav-proyek",
                "#nav-group-inventori", "#nav-products",
                "#nav-group-payroll", "#nav-payroll",
                "#nav-group-master", "#nav-coa");
        groupToLink.forEach((group, link) -> {
            assertThat(page.locator(link)).isHidden();
            page.click(group);
            assertThat(page.locator(link)).isVisible();
        });

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Quick transaction form swapped in by HTMX initialises Alpine and formats the amount")
    void quickTransactionFormInitialisesAfterHtmxSwap() {
        UUID templateId = seededTemplateId();
        loginAsAdmin();
        violations.clear();

        page.click("#quick-transaction-fab");
        assertThat(page.locator("#quick-transaction-modal")).isVisible();
        page.evaluate("id => htmx.ajax('GET', '/transactions/quick/form?templateId=' + id, '#quick-transaction-content')",
                templateId.toString());

        Locator amountDisplay = page.locator("#quick-amount-display");
        assertThat(page.locator("#quick-transaction-form")).isVisible();
        amountDisplay.pressSequentially("1500000");

        assertThat(amountDisplay).hasValue("1.500.000");
        assertThat(page.locator("#quick-amount")).hasValue("1500000");
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Bill line expense account picker opens on focus and fills the line on selection")
    void billExpenseAccountPickerOpensOnFocus() {
        ChartOfAccount account = chartOfAccountRepository.findByAccountCode("5.1.05")
                .orElseThrow(() -> new IllegalStateException("Seeded account 5.1.05 missing"));
        loginAsAdmin();
        page.navigate(baseUrl() + "/bills/new");
        waitForPageLoad();
        violations.clear();

        Locator label = page.locator("#bill-lineExpenseAccountLabel-0");
        label.focus();
        Locator results = page.locator("[data-testid='line-account-picker-result']");
        assertThat(results.first()).isVisible();

        label.fill("5.1.05");
        Locator match = results.filter(new Locator.FilterOptions().setHasText("Beban Sewa"));
        assertThat(match).hasCount(1);
        match.click();

        assertThat(label).hasValue("5.1.05 - Beban Sewa");
        assertThat(page.locator("input[name='lineExpenseAccountId']")).hasValue(account.getId().toString());
        assertThat(results.first()).isHidden();
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Deleting an analysis report asks for confirmation; dismissing keeps it, accepting deletes it")
    void analysisReportDeleteAsksForConfirmation() {
        AnalysisReport report = new AnalysisReport();
        report.setTitle("CSP confirm " + UUID.randomUUID());
        report.setReportType("CUSTOM");
        report = analysisReportRepository.save(report);
        UUID id = report.getId();

        loginAsAdmin();
        page.navigate(baseUrl() + "/analysis-reports/" + id);
        waitForPageLoad();
        violations.clear();

        assertThat(clickAndAnswer(page.locator("[data-testid='btn-delete']"), false))
                .isEqualTo("Hapus laporan analisis ini?");
        assertThat(page).hasURL(baseUrl() + "/analysis-reports/" + id);
        assertThat(analysisReportRepository.findById(id)).isPresent();

        assertThat(clickAndAnswer(page.locator("[data-testid='btn-delete']"), true))
                .isEqualTo("Hapus laporan analisis ini?");
        page.waitForURL(baseUrl() + "/analysis-reports");
        assertThat(analysisReportRepository.findById(id)).isEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Deleting a parser config asks for confirmation; dismissing keeps it, accepting deletes it")
    void parserConfigDeleteAsksForConfirmation() {
        BankStatementParserConfig config = new BankStatementParserConfig();
        config.setBankType(BankStatementParserType.CUSTOM);
        config.setConfigName("CSP confirm " + UUID.randomUUID());
        config.setDateColumn(0);
        config.setDescriptionColumn(1);
        config.setDebitColumn(2);
        config.setCreditColumn(3);
        config.setDateFormat("dd/MM/yyyy");
        config = parserConfigRepository.save(config);
        UUID id = config.getId();

        loginAsAdmin();
        page.navigate(baseUrl() + "/bank-reconciliation/parser-configs");
        waitForPageLoad();
        violations.clear();

        Locator deleteButton = page.locator("form[action$='/parser-configs/" + id + "/delete'] button");
        assertThat(clickAndAnswer(deleteButton, false)).isEqualTo("Yakin hapus konfigurasi ini?");
        assertThat(parserConfigRepository.findById(id)).isPresent();

        assertThat(clickAndAnswer(deleteButton, true)).isEqualTo("Yakin hapus konfigurasi ini?");
        assertThat(deleteButton).hasCount(0);
        assertThat(parserConfigRepository.findById(id)).isEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Deleting a fiscal adjustment asks for confirmation; dismissing keeps it, accepting deletes it")
    void fiscalAdjustmentDeleteAsksForConfirmation() {
        FiscalAdjustment adjustment = new FiscalAdjustment();
        adjustment.setYear(FISCAL_ADJUSTMENT_YEAR);
        adjustment.setDescription("CSP confirm " + UUID.randomUUID());
        adjustment.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
        adjustment.setAdjustmentDirection(FiscalAdjustmentDirection.POSITIVE);
        adjustment.setAmount(new BigDecimal("125000"));
        adjustment = fiscalAdjustmentRepository.save(adjustment);
        UUID id = adjustment.getId();

        loginAsAdmin();
        page.navigate(baseUrl() + "/reports/rekonsiliasi-fiskal?year=" + FISCAL_ADJUSTMENT_YEAR);
        waitForPageLoad();
        violations.clear();

        Locator deleteButton = page.locator("form[action$='/adjustments/" + id + "/delete'] button");
        assertThat(clickAndAnswer(deleteButton, false)).isEqualTo("Hapus koreksi fiskal ini?");
        assertThat(fiscalAdjustmentRepository.findById(id)).isPresent();

        assertThat(clickAndAnswer(deleteButton, true)).isEqualTo("Hapus koreksi fiskal ini?");
        assertThat(deleteButton).hasCount(0);
        assertThat(fiscalAdjustmentRepository.findById(id)).isEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Completing a reconciliation asks for confirmation; dismissing leaves it in draft")
    void reconciliationCompleteAsksForConfirmation() throws IOException {
        BankReconciliation reconciliation = createReconciliation();

        loginAsAdmin();
        page.navigate(baseUrl() + "/bank-reconciliation/reconciliations/" + reconciliation.getId());
        waitForPageLoad();
        violations.clear();

        assertThat(clickAndAnswer(page.locator("[data-testid='btn-complete']"), false))
                .isEqualTo("Yakin menyelesaikan rekonsiliasi ini?");
        assertThat(page).hasURL(baseUrl() + "/bank-reconciliation/reconciliations/" + reconciliation.getId());
        assertThat(reconciliationRepository.findById(reconciliation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReconciliationStatus.DRAFT);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Reconciliation print page applies its stylesheet and opens the print dialog")
    void reconciliationPrintIsStyledAndPrints() throws IOException {
        BankReconciliation reconciliation = createReconciliation();
        context.addInitScript("window.print = () => { window.printCalls = (window.printCalls || 0) + 1; };");

        loginAsAdmin();
        violations.clear();
        page.navigate(baseUrl() + "/bank-reconciliation/reconciliations/" + reconciliation.getId() + "/report/print");
        waitForPageLoad();

        assertThat(page.locator("h1")).hasText("Laporan Rekonsiliasi Bank");
        assertThat(page.evaluate("getComputedStyle(document.body).fontSize")).isEqualTo("12px");
        assertThat(page.evaluate("getComputedStyle(document.querySelector('td.cell-emphasis')).fontWeight")).isEqualTo("700");
        assertThat(page.evaluate("getComputedStyle(document.querySelector('tr.row-difference')).borderTopStyle")).isEqualTo("double");
        assertThat(page.evaluate("window.printCalls")).isEqualTo(1);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Invoice print page renders its line table column widths without CSP violations")
    void invoicePrintRendersWithoutViolations() {
        Invoice invoice = new Invoice();
        invoice.setClient(clientRepository.findByCode("MANDIRI")
                .orElseThrow(() -> new IllegalStateException("Seeded client MANDIRI missing")));
        invoice.setInvoiceDate(LocalDate.of(2024, 3, 1));
        invoice.setDueDate(LocalDate.of(2024, 3, 31));
        invoice.setAmount(new BigDecimal("2000000"));
        InvoiceLine line = new InvoiceLine();
        line.setDescription("CSP print line");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("1000000"));
        line.calculateAmounts();
        invoice = invoiceService.create(invoice, List.of(line));

        loginAsAdmin();
        violations.clear();
        page.navigate(baseUrl() + "/invoices/" + invoice.getInvoiceNumber() + "/print");
        waitForPageLoad();

        assertThat(page.locator(".line-items-table")).containsText("CSP print line");
        assertThat(page.evaluate("document.querySelector('.line-items-table th.col-w-8').style.length")).isEqualTo(0);
        assertThat(page.evaluate("getComputedStyle(document.querySelector('.line-items-table th.col-w-8')).width"))
                .isNotEqualTo("auto");
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Device success page close button reveals the manual-close note")
    void deviceSuccessCloseButtonWorks() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/device/success");
        waitForPageLoad();
        violations.clear();

        assertThat(page.locator("#close-note")).isHidden();
        page.click("#btn-close-tab");
        assertThat(page.locator("#close-note")).isVisible();
        assertThat(violations).isEmpty();
    }

    private String clickAndAnswer(Locator button, boolean accept) {
        AtomicReference<String> message = new AtomicReference<>();
        page.onceDialog(dialog -> {
            message.set(dialog.message());
            if (accept) {
                dialog.accept();
            } else {
                dialog.dismiss();
            }
        });
        button.click();
        waitForPageLoad();
        return message.get();
    }

    private BankReconciliation createReconciliation() throws IOException {
        ChartOfAccount bankGl = chartOfAccountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new IllegalStateException("Seeded account 1.1.02 missing"));
        BankStatementParserConfig bca = parserConfigRepository.findAll().stream()
                .filter(c -> c.getBankType() == BankStatementParserType.BCA && c.isSystemConfig())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("System BCA parser config missing"));

        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName("BCA CSP");
        account.setAccountNumber("CSP" + UUID.randomUUID().toString().substring(0, 8));
        account.setAccountName("CSP Test");
        account.setGlAccount(bankGl);
        account = bankAccountRepository.save(account);

        BankStatement statement = statementImportService.importStatement(new BankStatementImportService.BankStatementImportParams(
                account.getId(),
                bca.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                new BigDecimal("500000000"),
                new BigDecimal("687305000"),
                new MockMultipartFile("file", "bca-statement-202401.csv", "text/csv",
                        Files.readAllBytes(Paths.get(BCA_STATEMENT_CSV))),
                "admin"));
        return reconciliationService.create(statement.getId(), "CSP test", "admin");
    }

    private String assertCspAndInlineBlocks(String path, boolean expectInlineScript) {
        APIResponse response = page.request().get(baseUrl() + path);
        assertThat(response.status()).as("HTTP status of " + path).isEqualTo(200);
        String policy = assertSinglePolicy(response);

        Map<String, String> directives = new LinkedHashMap<>();
        for (String directive : policy.split(";")) {
            String trimmed = directive.trim();
            int space = trimmed.indexOf(' ');
            directives.put(trimmed.substring(0, space), trimmed.substring(space + 1));
        }
        Matcher scriptNonce = NONCE_SOURCE.matcher(directives.get("script-src"));
        assertThat(scriptNonce.find()).as("script-src nonce on " + path).isTrue();
        String nonce = scriptNonce.group(1);
        assertThat(directives.get("style-src")).contains("'nonce-" + nonce + "'");
        assertThat(directives.get("default-src")).isEqualTo("'self'");
        assertThat(directives.get("frame-ancestors")).isEqualTo("'none'");

        String html = response.text();
        Matcher tag = TAG_ATTRIBUTES.matcher(html);
        int inlineScripts = 0;
        while (tag.find()) {
            String attributes = tag.group(2);
            if (tag.group(1).equalsIgnoreCase("script") && attributes.contains(" src=")) {
                continue;
            }
            if (tag.group(1).equalsIgnoreCase("script")) {
                inlineScripts++;
            }
            Matcher nonceAttribute = NONCE_ATTRIBUTE.matcher(attributes);
            assertThat(nonceAttribute.find()).as("nonce on <" + tag.group(1) + attributes + "> of " + path).isTrue();
            assertThat(nonceAttribute.group(1)).as("nonce on <" + tag.group(1) + "> of " + path).isEqualTo(nonce);
        }
        if (expectInlineScript) {
            assertThat(inlineScripts).as("inline scripts on " + path).isPositive();
        }
        return nonce;
    }

    private String assertSinglePolicy(APIResponse response) {
        List<String> policies = response.headersArray().stream()
                .filter(h -> h.name.equalsIgnoreCase("Content-Security-Policy"))
                .map(h -> h.value)
                .toList();
        assertThat(policies).hasSize(1);
        String policy = policies.getFirst();
        assertThat(policy).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        return policy;
    }

    private UUID seededTemplateId() {
        return journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Pendapatan Jasa Training")
                .orElseThrow(() -> new IllegalStateException("Seeded template 'Pendapatan Jasa Training' missing"))
                .getId();
    }

    private static List<Path> templateFiles() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES_DIR)) {
            return files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
    }

    private static void collect(List<String> offenders, Path template, String source, Pattern pattern, String kind) {
        Matcher m = pattern.matcher(source);
        while (m.find()) {
            offenders.add(location(template, source, m.start()) + " " + kind);
        }
    }

    private static String location(Path template, String source, int offset) {
        long line = source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
        return TEMPLATES_DIR.relativize(template) + ":" + line;
    }

    private static String decodeEntities(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
    }
}
