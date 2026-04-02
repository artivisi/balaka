package com.artivisi.accountingfinance.functional.demo;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DataImportService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.ReportService.TrialBalanceReport;
import com.artivisi.accountingfinance.service.ReportService.TrialBalanceItem;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Base class for demo data loaders.
 *
 * Orchestrates demo instance setup by:
 * 1. Importing seed data (COA, templates, salary components, products, BOM)
 * 2. Importing master data (company, employees, clients, fiscal periods)
 * 3. Creating users
 * 4. Executing transactions month by month via Playwright UI
 * 5. Running payroll each month via Playwright UI
 * 6. Creating fixed assets and posting depreciation via Playwright UI
 * 7. Closing fiscal periods via Playwright UI
 * 8. Verifying trial balance
 */
@Slf4j
public abstract class DemoDataLoaderBase extends PlaywrightTestBase {

    @Autowired
    protected DataImportService dataImportService;

    @Autowired
    protected ReportService reportService;

    @Autowired
    protected com.artivisi.accountingfinance.repository.TransactionRepository transactionRepository;

    @Autowired
    protected JournalTemplateRepository journalTemplateRepository;

    @Autowired
    protected PayrollRunRepository payrollRunRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    private static final NumberFormat IDR = NumberFormat.getNumberInstance(new Locale("id", "ID"));

    // Template UUID cache (populated after seed import)
    private final Map<String, UUID> templateIdCache = new HashMap<>();

    // Screenshot tracker — capture first occurrence of each template/action
    private final Set<String> capturedScreenshots = new HashSet<>();

    /**
     * Take a tutorial screenshot if not already captured for this key.
     * Saves to target/user-manual/screenshots/tutorials/{industry}/{name}.png
     */
    protected void tutorialScreenshot(String name) {
        String key = industryName() + "/" + name;
        if (capturedScreenshots.contains(key)) return;
        capturedScreenshots.add(key);

        String industry = industryName().toLowerCase().replace(" ", "-");
        java.nio.file.Path dir = java.nio.file.Paths.get("target/user-manual/screenshots/tutorials/" + industry);
        dir.toFile().mkdirs();
        page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                .setPath(dir.resolve(name + ".png"))
                .setFullPage(false));
        log.debug("Tutorial screenshot: {}/{}", industry, name);
    }

    protected abstract String industryName();
    protected abstract String seedDataPath();
    protected abstract String demoDataPath();

    /**
     * JKK risk class for payroll (varies by industry).
     * Override in subclass if different from class 1.
     */
    protected int jkkRiskClass() { return 1; }

    /**
     * Base salary for payroll UMR reference.
     * Override in subclass if different.
     */
    protected long baseSalary() { return 5000000; }

    // ========== IMPORT OPERATIONS ==========

    protected DataImportService.ImportResult importSeedData() throws IOException {
        log.info("Importing {} industry seed data...", industryName());
        byte[] seedZip = createZipFromDirectory(seedDataPath());
        DataImportService.ImportResult result = dataImportService.importAllData(seedZip);
        log.info("{} seed imported: {} records in {}ms",
                industryName(), result.totalRecords(), result.durationMs());
        populateTemplateCache();
        return result;
    }

    protected DataImportService.ImportResult importMasterData() throws IOException {
        log.info("Importing {} master data...", industryName());
        byte[] demoZip = createZipFromDirectory(demoDataPath());
        DataImportService.ImportResult result = dataImportService.importAllData(demoZip);
        log.info("{} master data imported: {} records in {}ms",
                industryName(), result.totalRecords(), result.durationMs());
        return result;
    }

    protected void createDemoUsers() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setFullName("Administrator");
            admin.setEmail("admin@demo.balaka.id");
            admin.setActive(true);
            admin.addRole(Role.ADMIN, "system");
            userRepository.save(admin);
            log.info("Created demo admin user");
        }

        if (userRepository.findByUsername("staff").isEmpty()) {
            User staff = new User();
            staff.setUsername("staff");
            staff.setPassword(passwordEncoder.encode("password"));
            staff.setFullName("Staff User");
            staff.setEmail("staff@demo.balaka.id");
            staff.setActive(true);
            staff.addRole(Role.STAFF, "system");
            userRepository.save(staff);
            log.info("Created demo staff user");
        }
    }

    // ========== TRANSACTION EXECUTION VIA PLAYWRIGHT ==========

    /**
     * Execute all demo transactions from CSV, with monthly payroll + depreciation + period close.
     * CSV format: date,template_name,amount,inputs,description,reference,project,status
     *
     * For DETAILED templates, amount=0 and inputs contains pipe-separated variables.
     * For SIMPLE templates, amount is the transaction amount and inputs may contain account hints.
     *
     * Special rows:
     * - template_name = __ASSET__: inputs = code|name|category|cost|usefulLife|residualValue|depMethod
     * - template_name = __INVOICE__: inputs = clientCode|dueDate|itemDesc|qty|unitPrice|taxRate
     */
    protected void executeDemoTransactions(String csvResourcePath) {
        // Populate template cache lazily (after both seed + master data imports)
        if (templateIdCache.isEmpty()) {
            populateTemplateCache();
        }

        List<DemoAction> actions = loadDemoActions(csvResourcePath);
        log.info("Loaded {} demo actions from {}", actions.size(), csvResourcePath);

        loginAsAdmin();

        // Register a single persistent dialog handler (accept all confirms)
        page.onDialog(dialog -> dialog.accept());

        YearMonth currentMonth = null;
        int txCount = 0;

        for (DemoAction action : actions) {
            YearMonth actionMonth = YearMonth.from(action.date);

            // Month boundary: run payroll + depreciation + close for completed month
            if (currentMonth != null && !actionMonth.equals(currentMonth)) {
                completeMonth(currentMonth, action.status);
                currentMonth = actionMonth;
            }
            if (currentMonth == null) {
                currentMonth = actionMonth;
            }

            switch (action.templateName) {
                case "__ASSET__" -> createFixedAsset(action);
                case "__INVOICE__" -> createInvoice(action);
                default -> {
                    createTransaction(action);
                    txCount++;
                }
            }
        }

        // Complete the last month
        if (currentMonth != null) {
            completeMonth(currentMonth, "POST");
        }

        // Create fiscal adjustments for the year (typical koreksi fiskal)
        createFiscalAdjustments(2025);

        log.info("Executed {} transactions via Playwright", txCount);

        // Log transaction status summary
        var allTx = transactionRepository.findAll();
        long posted = allTx.stream().filter(t -> t.getStatus() == com.artivisi.accountingfinance.enums.TransactionStatus.POSTED).count();
        long draft = allTx.stream().filter(t -> t.getStatus() == com.artivisi.accountingfinance.enums.TransactionStatus.DRAFT).count();
        log.info("Transaction status summary: {} total, {} POSTED, {} DRAFT", allTx.size(), posted, draft);
    }

    /**
     * Complete a month: run payroll → pay salary → pay BPJS → post depreciation → close period.
     */
    private void completeMonth(YearMonth month, String lastStatus) {
        // 1. Run payroll (create + calculate + approve + post)
        PayrollRun payrollResult = runPayroll(month);

        if (payrollResult != null) {
            // 2. Bayar Hutang Gaji — pay the net salary from payroll
            LocalDate lastDay = month.atEndOfMonth();
            createTransactionViaForm("Bayar Hutang Gaji", lastDay,
                    payrollResult.getTotalNetPay().longValue(),
                    "Transfer gaji karyawan " + getIndonesianMonthName(month.getMonthValue()),
                    "GAJI-" + month);

            // 3. Bayar Hutang BPJS — pay total BPJS (employee + company)
            BigDecimal totalBpjs = payrollResult.getTotalCompanyBpjs()
                    .add(payrollResult.getTotalDeductions().subtract(payrollResult.getTotalPph21()));
            createTransactionViaForm("Bayar Hutang BPJS", lastDay,
                    totalBpjs.longValue(),
                    "Setor BPJS " + getIndonesianMonthName(month.getMonthValue()),
                    "BPJS-" + month);

            // 4. Setor PPh 21 — deposit the actual PPh 21 from payroll (next month's 10th)
            if (payrollResult.getTotalPph21().signum() > 0) {
                LocalDate depositDate = month.plusMonths(1).atDay(Math.min(10, month.plusMonths(1).atEndOfMonth().getDayOfMonth()));
                createTransactionViaForm("Setor PPh 21", depositDate,
                        payrollResult.getTotalPph21().longValue(),
                        "Setor PPh 21 " + getIndonesianMonthName(month.getMonthValue()),
                        "PPH21-" + month);
            }
        }

        // 5. Setor PPN — deposit PPN for previous month (on 15th of current month)
        depositPpn(month);

        // 6. Generate and post depreciation entries
        generateDepreciation(month);

        // 7. Close fiscal period (only for months before March 2026)
        if (month.isBefore(YearMonth.of(2026, 3))) {
            closeFiscalPeriod(month);
        }

        log.info("Completed month: {}", month);
    }

    @Autowired
    private com.artivisi.accountingfinance.repository.ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    /**
     * Deposit PPN for the previous month. Only for PKP companies.
     * Override ppnEnabled() to return false for non-PKP industries.
     */
    protected boolean ppnEnabled() { return true; }

    private void depositPpn(YearMonth currentMonth) {
        if (!ppnEnabled()) return;

        // PPN is deposited on the 15th of the following month for the previous month's PPN
        YearMonth ppnMonth = currentMonth.minusMonths(1);
        if (ppnMonth.isBefore(YearMonth.of(2025, 1))) return;

        var ppnAccount = chartOfAccountRepository.findByAccountCode("2.1.03");
        if (ppnAccount.isEmpty()) return;

        // Calculate PPN credit (keluaran) for the PPN month
        LocalDate start = ppnMonth.atDay(1);
        LocalDate end = ppnMonth.atEndOfMonth();
        BigDecimal ppnCredit = journalEntryRepository.sumCreditByAccountAndDateRange(
                ppnAccount.get().getId(), start, end);
        BigDecimal ppnDebit = journalEntryRepository.sumDebitByAccountAndDateRange(
                ppnAccount.get().getId(), start, end);

        BigDecimal ppnPayable = ppnCredit.subtract(ppnDebit);
        if (ppnPayable.signum() > 0) {
            LocalDate depositDate = currentMonth.atDay(Math.min(15, currentMonth.atEndOfMonth().getDayOfMonth()));
            createTransactionViaForm("Setor PPN", depositDate,
                    ppnPayable.longValue(),
                    "Setor PPN " + getIndonesianMonthName(ppnMonth.getMonthValue()) + " " + ppnMonth.getYear(),
                    "PPN-" + ppnMonth);
        }
    }

    /**
     * Create a single transaction via the UI form (used for payroll-derived transactions).
     */
    private void createTransactionViaForm(String templateName, LocalDate date, long amount,
                                           String description, String reference) {
        UUID templateId = templateIdCache.get(templateName);
        if (templateId == null) {
            log.error("Template not found for post-payroll transaction: {}", templateName);
            return;
        }

        DemoAction action = new DemoAction(date, templateName, amount, "", description, reference, "", "POST");
        createTransaction(action);
    }

    private void createTransaction(DemoAction action) {
        UUID templateId = templateIdCache.get(action.templateName);
        if (templateId == null) {
            log.error("Template not found: '{}' — skipping transaction: {}", action.templateName, action.description);
            return;
        }

        navigateTo("/transactions/new?templateId=" + templateId);
        waitForPageLoad();
        page.waitForTimeout(500); // Wait for Alpine.js init

        // Fill date
        page.locator("#transactionDate").fill(action.date.toString());

        // Fill amount or variables
        if (action.inputs != null && !action.inputs.isEmpty()) {
            Map<String, String> inputMap = parseInputs(action.inputs);

            // Handle account hints (BANK, PENDAPATAN, etc.)
            for (var entry : inputMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key.equals("amount")) {
                    page.locator("#amount").fill(value);
                } else if (key.startsWith("var_")) {
                    // Wait for DETAILED template variable inputs to render
                    page.locator("#" + key).waitFor(
                            new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
                    page.locator("#" + key).fill(value);
                } else if (isAccountHint(key)) {
                    selectAccountByHint(key, value);
                }
            }
        }

        if (action.amount > 0) {
            page.locator("#amount").fill(String.valueOf(action.amount));
            // Trigger input event for Alpine.js to update the hidden amount field
            page.locator("#amount").dispatchEvent("input");
        }

        // Auto-select dynamic account mappings based on hint type
        var accountSelects = page.locator("select[id^='accountMapping_']").all();
        Set<String> usedValues = new java.util.HashSet<>();
        for (var select : accountSelects) {
            String selectId = select.getAttribute("id");
            var label = page.locator("label[for='" + selectId + "']");
            String hint = label.count() > 0 ? label.textContent().trim() : "";
            // Strip trailing asterisk and whitespace from label
            hint = hint.replaceAll("[*?\\s]+$", "").trim();

            var options = select.locator("option").all();
            String selectedValue = null;

            for (var option : options) {
                String val = option.getAttribute("value");
                String text = option.textContent();
                if (val == null || val.isEmpty() || text == null) continue;

                // Match by hint type, skip already-used values
                // Prefer Bank BCA (1.1.02) first, then Kas/Mandiri
                if (hint.toUpperCase().contains("BANK") && !usedValues.contains(val)) {
                    if (text.startsWith("1.1.02")) { selectedValue = val; break; }
                    else if (text.startsWith("1.1.0") && selectedValue == null) { selectedValue = val; }
                } else if (hint.toUpperCase().contains("PENDAPATAN") && text.startsWith("4.1.")) {
                    selectedValue = val; break;
                } else if (hint.toUpperCase().contains("BEBAN") && text.startsWith("5.")) {
                    selectedValue = val; break;
                } else if ((hint.toUpperCase().contains("FIXED") || hint.toUpperCase().contains("ASET")) && text.startsWith("1.2.")) {
                    selectedValue = val; break;
                } else if (hint.toUpperCase().contains("DEBIT") || hint.toUpperCase().contains("CREDIT")) {
                    // Manual journal — skip auto-select
                    break;
                }
            }

            // Fallback: if no match found, pick first non-empty option
            if (selectedValue == null) {
                for (var option : options) {
                    String val = option.getAttribute("value");
                    if (val != null && !val.isEmpty()) {
                        selectedValue = val; break;
                    }
                }
            }

            if (selectedValue != null) {
                select.selectOption(selectedValue);
                usedValues.add(selectedValue);
            }
        }

        // Fill description
        page.locator("#description").fill(action.description);

        // Fill reference
        if (action.reference != null && !action.reference.isEmpty()) {
            page.locator("#referenceNumber").fill(action.reference);
        }

        // Select project
        if (action.project != null && !action.project.isEmpty()) {
            var projectSelect = page.locator("#project");
            // Find option containing the project code
            var options = projectSelect.locator("option").all();
            for (var option : options) {
                String text = option.textContent();
                if (text != null && text.contains(action.project)) {
                    projectSelect.selectOption(option.getAttribute("value"));
                    break;
                }
            }
        }

        // Screenshot: transaction form filled (first occurrence per template)
        tutorialScreenshot("tx-form-" + action.templateName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-"));

        // Submit: save as draft first, then post via API
        page.waitForTimeout(500);
        page.locator("#btn-simpan-draft").click();

        // Wait for redirect to detail page
        try {
            page.waitForURL(url -> url.contains("/transactions/") && !url.contains("/new"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));
            waitForPageLoad();

            if (!"DRAFT".equals(action.status)) {
                // Post the transaction via API call from the page context
                String txUrl = page.url();
                // Extract transaction ID from URL like /transactions/{uuid}
                String txId = txUrl.substring(txUrl.lastIndexOf('/') + 1);
                String postResult = (String) page.evaluate(
                        "async (txId) => { " +
                        "  const r = await fetch('/transactions/api/' + txId + '/post', " +
                        "    { method: 'POST', headers: { 'Content-Type': 'application/json' } }); " +
                        "  if (r.ok) return 'OK'; " +
                        "  const body = await r.text(); " +
                        "  return 'FAIL:' + r.status + ':' + body; " +
                        "}", txId);
                if (!"OK".equals(postResult)) {
                    log.error("Failed to post transaction {}: {}", txId, postResult);
                }
            }

            // Screenshot: transaction detail after posting
            tutorialScreenshot("tx-detail-" + action.templateName.toLowerCase()
                    .replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-"));

            log.info("Transaction created: {} | {} | {} | {}", action.date, action.templateName,
                    action.amount > 0 ? action.amount : action.inputs, action.description);
        } catch (Exception e) {
            String currentUrl = page.url();
            log.error("Transaction FAILED: {} | {} | {} — URL: {}", action.date, action.templateName,
                    action.description, currentUrl);
        }
    }

    private PayrollRun runPayroll(YearMonth period) {
        navigateTo("/payroll/new");
        waitForPageLoad();

        page.locator("input[name='period']").fill(period.toString());
        page.locator("input[name='baseSalary']").fill(String.valueOf(baseSalary()));
        page.locator("select[name='jkkRiskClass']").selectOption(String.valueOf(jkkRiskClass()));

        // Screenshot: payroll form filled
        tutorialScreenshot("payroll-form");

        page.locator("#btn-submit").click();
        waitForPageLoad();

        // Screenshot: payroll detail after calculation
        tutorialScreenshot("payroll-calculated");

        // Should be on detail page now with status CALCULATED
        // Approve — the create action auto-calculates, so btn-approve should be visible
        try {
            page.locator("#btn-approve").waitFor(
                    new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
            page.locator("#btn-approve").click();
            waitForPageLoad();
            log.info("Payroll approved for period: {}", period);

            // Post to journal — after approve redirect, btn-post should appear
            page.locator("#btn-post").waitFor(
                    new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
            page.locator("#btn-post").click();
            waitForPageLoad();
            // Screenshot: payroll posted
            tutorialScreenshot("payroll-posted");
            log.info("Payroll posted for period: {}", period);
        } catch (com.microsoft.playwright.TimeoutError e) {
            log.warn("Payroll button not found for period {} — current URL: {}", period, page.url());
        }

        // Return the payroll run so caller can use the amounts
        return payrollRunRepository.findByPayrollPeriod(period.toString()).orElse(null);
    }

    private void generateDepreciation(YearMonth month) {
        // Step 1: Generate depreciation entries
        navigateTo("/assets/depreciation");
        waitForPageLoad();
        tutorialScreenshot("depreciation-list");
        page.locator("#period").fill(month.toString());
        page.locator("form[action*='/depreciation/generate'] button[type='submit']").click();
        waitForPageLoad();

        // Step 2: Re-navigate to get a clean page with pending entries
        navigateTo("/assets/depreciation");
        waitForPageLoad();

        // Step 3: Post entries for this month only (max 5 attempts to avoid infinite loop)
        int posted = 0;
        for (int attempt = 0; attempt < 5; attempt++) {
            navigateTo("/assets/depreciation");
            waitForPageLoad();

            var postForms = page.locator("form[method='post']").all().stream()
                    .filter(f -> {
                        String action = f.getAttribute("action");
                        return action != null && action.matches(".*/assets/depreciation/[0-9a-f-]+/post");
                    })
                    .toList();

            if (postForms.isEmpty()) break;

            // Only post the first entry (for this month's asset)
            postForms.get(0).locator("button[type='submit']").click();
            waitForPageLoad();
            posted++;

            // If we've posted enough for the expected number of assets, stop
            if (posted >= 2) break;
        }

        if (posted > 0) {
            log.info("Depreciation: generated and posted {} entries for {}", posted, month);
        }
    }

    private void closeFiscalPeriod(YearMonth month) {
        navigateTo("/fiscal-periods");
        waitForPageLoad();

        // Find and click the period row to go to detail
        var periodLink = page.locator("a[href*='/fiscal-periods/']")
                .filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasText(getIndonesianMonthName(month.getMonthValue()) + " " + month.getYear()));

        if (periodLink.count() > 0) {
            periodLink.first().click();
            waitForPageLoad();

            // Screenshot: fiscal period detail before closing
            tutorialScreenshot("fiscal-period-detail");

            // Click close month button
            var closeBtn = page.locator("[data-testid='btn-close-month']");
            if (closeBtn.isVisible()) {
                closeBtn.click();
                waitForPageLoad();
                log.debug("Closed fiscal period: {}", month);
            }
        }
    }

    private void createFixedAsset(DemoAction action) {
        // inputs format: code|name|category|cost|usefulLife|residualValue|depMethod
        String[] parts = action.inputs.split("\\|");

        navigateTo("/assets/new");
        waitForPageLoad();

        page.locator("#assetCode").fill(parts[0]);
        page.locator("#name").fill(parts[1]);

        // Select category by text match
        var categorySelect = page.locator("#category");
        var options = categorySelect.locator("option").all();
        for (var option : options) {
            String text = option.textContent();
            if (text != null && text.toUpperCase().contains(parts[2].toUpperCase())) {
                categorySelect.selectOption(option.getAttribute("value"));
                break;
            }
        }

        page.locator("#purchaseDate").fill(action.date.toString());
        page.locator("#purchaseCost").fill(parts[3]);
        page.locator("#depreciationStartDate").fill(action.date.toString());

        if (parts.length > 6) {
            page.locator("#depreciationMethod").selectOption(parts[6]);
        } else {
            page.locator("#depreciationMethod").selectOption("STRAIGHT_LINE");
        }

        page.locator("#usefulLifeMonths").fill(parts[4]);
        page.locator("#residualValue").fill(parts.length > 5 ? parts[5] : "0");

        tutorialScreenshot("asset-form");

        page.locator("#btn-simpan").click();
        waitForPageLoad();

        tutorialScreenshot("asset-detail");

        log.info("Fixed asset created: {} - {}", parts[0], parts[1]);

        // Also create a purchase transaction via "Pembelian Aset Tetap" template
        // This records the journal entry: debit fixed asset account, credit bank
        // Template is DETAILED with variable "assetCost"
        DemoAction purchaseAction = new DemoAction(
                action.date, "Pembelian Aset Tetap", 0,
                "var_assetCost:" + parts[3],
                "Pembelian " + parts[1], action.reference, "", "POST");
        createTransaction(purchaseAction);
    }

    private void createInvoice(DemoAction action) {
        // inputs format: clientCode|dueDate|itemDesc|qty|unitPrice|taxRate
        String[] parts = action.inputs.split("\\|");

        navigateTo("/invoices/new");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Select client
        var clientSelect = page.locator("#client");
        var options = clientSelect.locator("option").all();
        for (var option : options) {
            String text = option.textContent();
            if (text != null && text.contains(parts[0])) {
                clientSelect.selectOption(option.getAttribute("value"));
                break;
            }
        }

        page.locator("#invoiceDate").fill(action.date.toString());
        page.locator("#dueDate").fill(parts[1]);

        // Add line item
        page.locator("button:has-text('Tambah')").click();
        page.waitForTimeout(300);

        page.locator("#inv-lineDescription-0").fill(parts[2]);
        page.locator("#inv-lineQuantity-0").fill(parts[3]);
        page.locator("#inv-lineUnitPrice-0").fill(parts[4]);
        if (parts.length > 5 && !parts[5].isEmpty()) {
            page.locator("#inv-lineTaxRate-0").fill(parts[5]);
        }

        page.locator("#btn-simpan").click();
        waitForPageLoad();

        log.debug("Invoice created for client: {}", parts[0]);
    }

    // ========== VALIDATION ==========

    protected void validateTrialBalance(LocalDate asOfDate) {
        TrialBalanceReport tb = reportService.generateTrialBalance(asOfDate);

        log.info("========== TRIAL BALANCE as of {} ({}) ==========", asOfDate, industryName());
        log.info("{} | {} | {}", padRight("Account", 50), padLeft("Debit", 20), padLeft("Credit", 20));
        log.info("{}", "-".repeat(92));

        for (TrialBalanceItem item : tb.items()) {
            String acctLabel = item.account().getAccountCode() + " " + item.account().getAccountName();
            log.info("{} | {} | {}",
                    padRight(acctLabel, 50),
                    padLeft(formatAmount(item.debitBalance()), 20),
                    padLeft(formatAmount(item.creditBalance()), 20));

            org.assertj.core.api.Assertions.assertThat(
                    item.debitBalance().signum() == 0 || item.creditBalance().signum() == 0)
                    .as("Account %s has both debit (%s) and credit (%s)",
                            acctLabel, item.debitBalance(), item.creditBalance())
                    .isTrue();
        }

        log.info("{}", "-".repeat(92));
        log.info("{} | {} | {}",
                padRight("TOTAL", 50),
                padLeft(formatAmount(tb.totalDebit()), 20),
                padLeft(formatAmount(tb.totalCredit()), 20));

        org.assertj.core.api.Assertions.assertThat(tb.totalDebit())
                .as("Trial balance: total debit must equal total credit")
                .isEqualByComparingTo(tb.totalCredit());

        org.assertj.core.api.Assertions.assertThat(tb.totalDebit())
                .as("Trial balance must have non-zero totals")
                .isGreaterThan(BigDecimal.ZERO);

        org.assertj.core.api.Assertions.assertThat(tb.items().size())
                .as("Trial balance must have multiple accounts")
                .isGreaterThanOrEqualTo(5);

        log.info("Trial balance VERIFIED: {} accounts, total {} (debit = credit)",
                tb.items().size(), formatAmount(tb.totalDebit()));
    }

    protected void validateDashboard() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page.locator("body")).isVisible();
        log.info("Dashboard loaded successfully");
    }

    // ========== HELPERS ==========

    private void populateTemplateCache() {
        List<JournalTemplate> templates = journalTemplateRepository
                .findByActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(true);
        for (JournalTemplate t : templates) {
            templateIdCache.put(t.getTemplateName(), t.getId());
        }
        log.info("Cached {} template IDs", templateIdCache.size());
    }

    private List<DemoAction> loadDemoActions(String resourcePath) {
        List<DemoAction> actions = new ArrayList<>();
        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath);
             var reader = new BufferedReader(new InputStreamReader(
                     Objects.requireNonNull(is, "Resource not found: " + resourcePath),
                     StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }
                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",", -1);
                if (parts.length >= 6) {
                    actions.add(new DemoAction(
                            LocalDate.parse(parts[0].trim()),
                            parts[1].trim(),
                            parseLong(parts[2].trim()),
                            parts[3].trim(),
                            parts[4].trim(),
                            parts[5].trim(),
                            parts.length > 6 ? parts[6].trim() : "",
                            parts.length > 7 ? parts[7].trim() : "POST"
                    ));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load demo actions: " + resourcePath, e);
        }
        return actions;
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private boolean isAccountHint(String key) {
        return key.equals("BANK") || key.equals("PENDAPATAN") || key.equals("BEBAN") ||
               key.equals("PIUTANG") || key.equals("HUTANG") || key.equals("FIXED_ASSET") ||
               key.equals("DEBIT_ACCOUNT") || key.equals("CREDIT_ACCOUNT");
    }

    private void selectAccountByHint(String hint, String accountCode) {
        var selects = page.locator("select[id^='accountMapping_']").all();
        for (var select : selects) {
            String selectId = select.getAttribute("id");
            var label = page.locator("label[for='" + selectId + "']");
            String labelText = label.textContent();
            if (labelText != null && labelText.contains(hint)) {
                var options = select.locator("option").all();
                for (var option : options) {
                    String optionText = option.textContent();
                    if (optionText != null && optionText.startsWith(accountCode)) {
                        select.selectOption(option.getAttribute("value"));
                        return;
                    }
                }
            }
        }
    }

    private Map<String, String> parseInputs(String inputs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty()) return result;
        for (String pair : inputs.split("\\|")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) result.put(kv[0].trim(), kv[1].trim());
        }
        return result;
    }

    protected byte[] createZipFromDirectory(String dirPath) throws IOException {
        Path dir = Paths.get(dirPath).toAbsolutePath();
        if (!Files.exists(dir)) {
            throw new IOException("Directory not found: " + dir);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walk(dir)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.getFileName().toString().equals("README.md"))
                    .filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .forEach(path -> {
                        try {
                            Path relativePath = dir.relativize(path);
                            ZipEntry entry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to zip file: " + path, e);
                        }
                    });
        }
        return baos.toByteArray();
    }

    /**
     * Create typical fiscal adjustments for the year via the Rekonsiliasi Fiskal page.
     * Override fiscalAdjustments() in subclass to customize.
     */
    protected record FiscalAdj(String description, String category, String direction, long amount, String accountCode, String notes) {}

    protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
                // Beda Tetap - Positif: entertainment/jamuan tidak ada daftar nominatif
                new FiscalAdj("Beban entertainment tanpa daftar nominatif", "PERMANENT", "POSITIVE",
                        5000000, "5.1.99", "Pasal 6 ayat 1 huruf a — jamuan tanpa daftar nominatif"),
                // Beda Tetap - Positif: sumbangan/donasi non-deductible
                new FiscalAdj("Sumbangan non-deductible", "PERMANENT", "POSITIVE",
                        2000000, "5.1.99", "Pasal 9 ayat 1 huruf g — sumbangan"),
                // Beda Waktu - Positif: penyusutan komersial > fiskal
                new FiscalAdj("Selisih penyusutan komersial vs fiskal", "TEMPORARY", "POSITIVE",
                        3000000, "5.1.12", "Penyusutan komersial 48 bulan, fiskal sesuai kelompok")
        );
    }

    private void createFiscalAdjustments(int year) {
        var adjustments = fiscalAdjustments();
        if (adjustments.isEmpty()) return;

        for (var adj : adjustments) {
            navigateTo("/reports/rekonsiliasi-fiskal?year=" + year);
            waitForPageLoad();

            page.locator("#adj-description").fill(adj.description());
            page.locator("#adj-category").selectOption(adj.category());
            page.locator("#adj-direction").selectOption(adj.direction());
            page.locator("#adj-amount").fill(String.valueOf(adj.amount()));

            page.locator("#btn-add-adjustment").click();
            waitForPageLoad();
        }

        log.info("Created {} fiscal adjustments for year {}", adjustments.size(), year);
    }

    private String getIndonesianMonthName(int month) {
        return switch (month) {
            case 1 -> "Januari"; case 2 -> "Februari"; case 3 -> "Maret";
            case 4 -> "April"; case 5 -> "Mei"; case 6 -> "Juni";
            case 7 -> "Juli"; case 8 -> "Agustus"; case 9 -> "September";
            case 10 -> "Oktober"; case 11 -> "November"; case 12 -> "Desember";
            default -> "";
        };
    }

    private String formatAmount(BigDecimal amount) {
        if (amount.signum() == 0) return "";
        return IDR.format(amount);
    }

    private String padRight(String s, int width) {
        return String.format("%-" + width + "s", s.length() > width ? s.substring(0, width) : s);
    }

    private String padLeft(String s, int width) {
        return String.format("%" + width + "s", s);
    }

    // ========== DATA RECORDS ==========

    record DemoAction(
            LocalDate date,
            String templateName,
            long amount,
            String inputs,
            String description,
            String reference,
            String project,
            String status
    ) {}
}
