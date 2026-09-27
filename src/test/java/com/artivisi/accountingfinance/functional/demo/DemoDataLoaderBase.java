package com.artivisi.accountingfinance.functional.demo;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.page.AccountPicker;
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
 * 7. Verifying trial balance
 */
@Slf4j
public abstract class DemoDataLoaderBase extends PlaywrightTestBase {

    /**
     * Postings this loader failed to create.
     *
     * <p>
     * Every step below used to log its failure and carry on. A single dropped
     * transaction does not fail where it happens — it surfaces much later as a
     * balance that is short by exactly that amount, with the real cause buried
     * in the middle of a ten-minute log. That is what "Beban Keamanan 33.000.000
     * instead of 36.000.000" (eleven months instead of twelve) actually was.
     *
     * <p>
     * Failures are collected rather than thrown at the first occurrence so one
     * run reports every gap at once, then {@link #failIfPostingsWereDropped()}
     * fails the load. This follows the project rule: throw on missing data
     * rather than silently continuing.
     */
    private final List<String> droppedPostings = new java.util.ArrayList<>();

    /** Records a posting that could not be created, for the end-of-load check. */
    protected void recordDroppedPosting(String what, String detail) {
        String entry = what + " — " + detail;
        droppedPostings.add(entry);
        log.error("DROPPED POSTING: {}", entry);
    }

    /**
     * Fails the load if any posting was dropped, listing all of them. Called
     * once transactions are loaded, so the failure names the real cause instead
     * of leaving a later balance assertion to report the symptom.
     */
    private void failIfPostingsWereDropped() {
        if (droppedPostings.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(droppedPostings.size())
          .append(" posting(s) were dropped while loading demo data. Expected balances")
          .append(" cannot match, so failing here rather than in a later assertion:\n");
        for (String d : droppedPostings) {
            sb.append("  - ").append(d).append('\n');
        }
        throw new IllegalStateException(sb.toString());
    }

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
     */
    protected void executeDemoTransactions(String csvResourcePath) {
        // Populate template cache lazily (after both seed + master data imports)
        if (templateIdCache.isEmpty()) {
            populateTemplateCache();
        }

        List<DemoAction> actions = loadDemoActions(csvResourcePath);
        if (actions.isEmpty()) {
            throw new IllegalStateException("No demo actions in " + csvResourcePath);
        }
        log.info("Loaded {} demo actions from {}", actions.size(), csvResourcePath);

        loginAsAdmin();

        // Register a single persistent dialog handler (accept all confirms)
        page.onDialog(dialog -> dialog.accept());

        YearMonth currentMonth = null;
        int txCount = 0;

        for (DemoAction action : actions) {
            YearMonth actionMonth = YearMonth.from(action.date);

            // Month boundary: run payroll + depreciation for the completed month
            if (currentMonth != null && !actionMonth.equals(currentMonth)) {
                completeMonth(currentMonth);
                currentMonth = actionMonth;
            }
            if (currentMonth == null) {
                currentMonth = actionMonth;
            }

            switch (action.templateName) {
                case "__ASSET__" -> createFixedAsset(action);
                default -> {
                    createTransaction(action);
                    txCount++;
                }
            }
        }

        completeMonth(currentMonth);

        // Create fiscal adjustments for the year (typical koreksi fiskal)
        createFiscalAdjustments(2025);

        log.info("Executed {} transactions via Playwright", txCount);

        // Fail now, naming the dropped postings, rather than letting a later
        // expected-balance assertion report the shortfall without the cause.
        failIfPostingsWereDropped();

        // Log transaction status summary
        var allTx = transactionRepository.findAll();
        long posted = allTx.stream().filter(t -> t.getStatus() == com.artivisi.accountingfinance.enums.TransactionStatus.POSTED).count();
        long draft = allTx.stream().filter(t -> t.getStatus() == com.artivisi.accountingfinance.enums.TransactionStatus.DRAFT).count();
        log.info("Transaction status summary: {} total, {} POSTED, {} DRAFT", allTx.size(), posted, draft);
    }

    /**
     * Complete a month: run payroll → pay salary → pay BPJS → deposit PPh 21/PPN → post depreciation.
     */
    private void completeMonth(YearMonth month) {
        // 1. Run payroll (create + calculate + approve + post)
        PayrollRun payrollResult = runPayroll(month);

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

        // 5. Setor PPN — deposit PPN for previous month (on 15th of current month)
        depositPpn(month);

        // 6. Generate and post depreciation entries
        generateDepreciation(month);

        log.info("Completed month: {}", month);
    }

    @Autowired
    private com.artivisi.accountingfinance.repository.ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private com.artivisi.accountingfinance.repository.DepreciationEntryRepository depreciationEntryRepository;

    @Autowired
    private com.artivisi.accountingfinance.repository.FixedAssetRepository fixedAssetRepository;

    @Autowired
    private com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository fiscalAdjustmentRepository;

    @Autowired
    private com.artivisi.accountingfinance.service.DashboardService dashboardService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

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

        var ppnAccount = chartOfAccountRepository.findByAccountCode("2.1.03")
                .orElseThrow(() -> new IllegalStateException("PPN account 2.1.03 missing from " + industryName() + " COA"));

        // Calculate PPN credit (keluaran) for the PPN month
        LocalDate start = ppnMonth.atDay(1);
        LocalDate end = ppnMonth.atEndOfMonth();
        BigDecimal ppnCredit = journalEntryRepository.sumCreditByAccountAndDateRange(
                ppnAccount.getId(), start, end);
        BigDecimal ppnDebit = journalEntryRepository.sumDebitByAccountAndDateRange(
                ppnAccount.getId(), start, end);

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
        createTransaction(new DemoAction(date, templateName, amount, "", description, reference, "", "POST"));
    }

    /**
     * Enters one row through the transaction form, posts it unless the row is DRAFT, and
     * checks the stored transaction: status, amount, project, the account chosen for every
     * template-line picker, and balanced journal lines on those accounts. Any failure is
     * recorded against the row and fails the load in {@link #failIfPostingsWereDropped()}.
     */
    private void createTransaction(DemoAction action) {
        try {
            enterTransaction(action);
            log.info("Transaction created: {} | {} | {} | {}", action.date, action.templateName,
                    action.amount > 0 ? action.amount : action.inputs, action.description);
        } catch (RuntimeException | AssertionError e) {
            recordDroppedPosting("transaction",
                    action.date + " | " + action.templateName + " | " + action.reference + " | " + action.description
                            + " — " + e.getClass().getSimpleName() + ": "
                            + String.valueOf(e.getMessage()).lines().limit(4).collect(java.util.stream.Collectors.joining(" / "))
                            + " — URL: " + page.url());
        }
    }

    private void enterTransaction(DemoAction action) {
        UUID templateId = templateIdCache.get(action.templateName);
        if (templateId == null) {
            throw new IllegalStateException("template '" + action.templateName + "' not found");
        }

        navigateTo("/transactions/new?templateId=" + templateId);
        waitForPageLoad();
        page.waitForTimeout(500); // Wait for Alpine.js init

        page.locator("#transactionDate").fill(action.date.toString());

        // Accounts chosen per template-line picker (key: template line id).
        Map<String, String> pickedAccounts = new LinkedHashMap<>();
        for (var entry : parseInputs(action.inputs).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equals("amount")) {
                page.locator("#amount").fill(value);
            } else if (key.startsWith("var_")) {
                page.locator("#" + key).waitFor(
                        new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
                page.locator("#" + key).fill(value);
            } else if (isAccountHint(key)) {
                var input = pickerInputByHint(key);
                AccountPicker.select(page, input, value);
                pickedAccounts.put(lineIdOf(input), value);
            } else {
                throw new IllegalArgumentException("unknown input '" + key + "' in: " + action.inputs);
            }
        }

        if (action.amount > 0) {
            page.locator("#amount").fill(String.valueOf(action.amount));
            // Trigger input event for Alpine.js to update the hidden amount field
            page.locator("#amount").dispatchEvent("input");
        }

        // Pickers not set by an explicit hint get an account by hint rule: the first account
        // the server lists for the rule's code query that this transaction has not used yet.
        Set<String> usedCodes = new java.util.HashSet<>(pickedAccounts.values());
        for (var input : page.locator("input[id^='accountMapping_']").all()) {
            String lineId = lineIdOf(input);
            if (pickedAccounts.containsKey(lineId)) {
                continue;
            }
            String hint = page.locator("label[for='" + input.getAttribute("id") + "']").textContent()
                    .replaceAll("[*?\\s]+$", "").trim();
            String query = accountQueryForHint(hint, usedCodes);
            List<String> candidates = AccountPicker.searchCodes(page, baseUrl(), query);
            String code = candidates.stream().filter(c -> !usedCodes.contains(c)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("no unused account for picker '" + hint
                            + "' (query '" + query + "', candidates " + candidates + ", used " + usedCodes + ")"));
            AccountPicker.select(page, input, code);
            pickedAccounts.put(lineId, code);
            usedCodes.add(code);
        }

        page.locator("#description").fill(action.description);

        if (!action.reference.isEmpty()) {
            page.locator("#referenceNumber").fill(action.reference);
        }

        if (!action.project.isEmpty()) {
            var option = page.locator("#project option")
                    .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(action.project));
            assertThat(option).hasCount(1);
            page.locator("#project").selectOption(option.getAttribute("value"));
        }

        tutorialScreenshot("tx-form-" + action.templateName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-"));

        page.waitForTimeout(500);
        page.locator("#btn-simpan-draft").click();
        page.waitForURL(url -> url.contains("/transactions/") && !url.contains("/new"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));
        waitForPageLoad();

        String txUrl = page.url();
        UUID txId = UUID.fromString(txUrl.substring(txUrl.lastIndexOf('/') + 1));

        boolean post = action.status.equals("POST");
        if (post) {
            String postResult = (String) page.evaluate(
                    "async (txId) => { " +
                    "  const r = await fetch('/transactions/api/' + txId + '/post', " +
                    "    { method: 'POST', headers: { 'Content-Type': 'application/json' } }); " +
                    "  if (r.ok) return 'OK'; " +
                    "  const body = await r.text(); " +
                    "  return 'FAIL:' + r.status + ':' + body; " +
                    "}", txId.toString());
            if (!"OK".equals(postResult)) {
                throw new IllegalStateException("posting " + txId + " failed: " + postResult);
            }
        }

        verifyStoredTransaction(action, txId, post, pickedAccounts);

        tutorialScreenshot("tx-detail-" + action.templateName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-"));
    }

    private void verifyStoredTransaction(DemoAction action, UUID txId, boolean posted,
                                         Map<String, String> pickedAccounts) {
        var tx = transactionRepository.findByIdWithMappingsAndVariables(txId)
                .orElseThrow(() -> new IllegalStateException("transaction " + txId + " not stored"));
        var expectedStatus = posted
                ? com.artivisi.accountingfinance.enums.TransactionStatus.POSTED
                : com.artivisi.accountingfinance.enums.TransactionStatus.DRAFT;
        if (tx.getStatus() != expectedStatus) {
            throw new IllegalStateException("transaction " + tx.getTransactionNumber() + " is " + tx.getStatus()
                    + ", expected " + expectedStatus);
        }
        if (action.amount > 0 && tx.getAmount().compareTo(BigDecimal.valueOf(action.amount)) != 0) {
            throw new IllegalStateException("transaction " + tx.getTransactionNumber() + " amount " + tx.getAmount()
                    + ", expected " + action.amount);
        }
        List<String> projectCodes = entityManager.createQuery(
                        "SELECT p.code FROM Transaction t JOIN t.project p WHERE t.id = :id", String.class)
                .setParameter("id", txId)
                .getResultList();
        List<String> expectedProject = action.project.isEmpty() ? List.of() : List.of(action.project);
        if (!projectCodes.equals(expectedProject)) {
            throw new IllegalStateException("transaction " + tx.getTransactionNumber() + " project "
                    + projectCodes + ", expected " + expectedProject);
        }

        Map<String, String> storedAccounts = new HashMap<>();
        for (var mapping : tx.getAccountMappings()) {
            storedAccounts.put(mapping.getTemplateLine().getId().toString(), mapping.getAccount().getAccountCode());
        }
        if (!storedAccounts.equals(pickedAccounts)) {
            throw new IllegalStateException("transaction " + tx.getTransactionNumber() + " account mappings "
                    + storedAccounts + ", picked " + pickedAccounts);
        }

        if (!posted) {
            return;
        }
        var entries = journalEntryRepository.findByTransactionIdWithAccount(txId);
        Set<String> entryCodes = new HashSet<>();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (var e : entries) {
            entryCodes.add(e.getAccount().getAccountCode());
            debit = debit.add(e.getDebitAmount());
            credit = credit.add(e.getCreditAmount());
        }
        if (entries.isEmpty() || debit.signum() == 0 || debit.compareTo(credit) != 0
                || !entryCodes.containsAll(pickedAccounts.values())) {
            throw new IllegalStateException("transaction " + tx.getTransactionNumber() + " journal: accounts "
                    + entryCodes + " (picked " + pickedAccounts.values() + "), debit " + debit + ", credit " + credit);
        }
    }

    private com.microsoft.playwright.Locator pickerInputByHint(String hint) {
        var matches = new ArrayList<com.microsoft.playwright.Locator>();
        for (var input : page.locator("input[id^='accountMapping_']").all()) {
            if (page.locator("label[for='" + input.getAttribute("id") + "']").textContent().contains(hint)) {
                matches.add(input);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException(matches.size() + " account pickers labelled with hint '" + hint + "'");
        }
        return matches.get(0);
    }

    private static String lineIdOf(com.microsoft.playwright.Locator pickerInput) {
        return pickerInput.getAttribute("id").substring("accountMapping_".length());
    }

    /**
     * Code query for an auto-selected picker, by its label: the first BANK line takes Bank BCA
     * (1.1.02), a second one the next unused 1.1.x account.
     */
    private static String accountQueryForHint(String hint, Set<String> usedCodes) {
        String upper = hint.toUpperCase();
        if (upper.contains("BANK")) {
            return usedCodes.contains("1.1.02") ? "1.1." : "1.1.02";
        } else if (upper.contains("PENDAPATAN")) {
            return "4.1.";
        } else if (upper.contains("BEBAN")) {
            return "5.";
        } else if (upper.contains("FIXED") || upper.contains("ASET")) {
            return "1.2.";
        } else if (upper.contains("DEBIT") || upper.contains("CREDIT") || upper.contains("KREDIT")) {
            throw new IllegalStateException("picker '" + hint + "' needs an explicit account in the CSV inputs");
        }
        return "1.";
    }

    private PayrollRun runPayroll(YearMonth period) {
        navigateTo("/payroll/new");
        waitForPageLoad();

        page.locator("input[name='period']").fill(period.toString());
        page.locator("input[name='baseSalary']").fill(String.valueOf(baseSalary()));
        page.locator("select[name='jkkRiskClass']").selectOption(String.valueOf(jkkRiskClass()));

        tutorialScreenshot("payroll-form");

        page.locator("#btn-submit").click();
        waitForPageLoad();

        tutorialScreenshot("payroll-calculated");

        // The create action auto-calculates, so btn-approve is shown on the detail page.
        page.locator("#btn-approve").waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
        page.locator("#btn-approve").click();
        waitForPageLoad();
        log.info("Payroll approved for period: {}", period);

        page.locator("#btn-post").waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
        page.locator("#btn-post").click();
        waitForPageLoad();
        tutorialScreenshot("payroll-posted");

        PayrollRun run = payrollRunRepository.findByPayrollPeriod(period.toString())
                .orElseThrow(() -> new IllegalStateException("Payroll run " + period + " not stored"));
        if (run.getStatus() != com.artivisi.accountingfinance.entity.PayrollStatus.POSTED) {
            throw new IllegalStateException("Payroll run " + period + " is " + run.getStatus() + ", expected POSTED");
        }
        log.info("Payroll posted for period: {}", period);
        return run;
    }

    /**
     * Generates depreciation for the month, posts every entry generated for it from the
     * depreciation page, and checks that all of them are POSTED.
     */
    private void generateDepreciation(YearMonth month) {
        navigateTo("/assets/depreciation");
        waitForPageLoad();
        tutorialScreenshot("depreciation-list");
        page.locator("#period").fill(month.toString());
        page.locator("form[action*='/depreciation/generate'] button[type='submit']").click();
        waitForPageLoad();

        LocalDate periodEnd = month.atEndOfMonth();
        var pending = depreciationEntryRepository.findByPeriodEndAndOptionalStatus(
                periodEnd, com.artivisi.accountingfinance.entity.DepreciationEntryStatus.PENDING);
        for (var entry : pending) {
            navigateTo("/assets/depreciation");
            waitForPageLoad();
            page.locator("form[action$='/assets/depreciation/" + entry.getId() + "/post'] button[type='submit']").click();
            waitForPageLoad();
        }

        var entries = depreciationEntryRepository.findByPeriodEndAndOptionalStatus(periodEnd, null);
        var notPosted = entries.stream()
                .filter(e -> e.getStatus() != com.artivisi.accountingfinance.entity.DepreciationEntryStatus.POSTED)
                .map(e -> e.getFixedAsset().getAssetCode() + "=" + e.getStatus())
                .toList();
        if (!notPosted.isEmpty()) {
            throw new IllegalStateException("Depreciation " + month + " not posted: " + notPosted);
        }
        long depreciable = fixedAssetRepository.findAll().stream()
                .filter(a -> !a.getDepreciationStartDate().isAfter(periodEnd))
                .count();
        if (entries.size() != depreciable) {
            throw new IllegalStateException("Depreciation " + month + ": " + entries.size()
                    + " entries for " + depreciable + " assets in service");
        }
        if (!entries.isEmpty()) {
            log.info("Depreciation: generated and posted {} entries for {}", entries.size(), month);
        }
    }

    private void createFixedAsset(DemoAction action) {
        // inputs format: code|name|category|cost|usefulLife|residualValue|depMethod
        String[] parts = action.inputs.split("\\|");
        if (parts.length != 7) {
            throw new IllegalArgumentException("__ASSET__ inputs need code|name|category|cost|usefulLife|residualValue|depMethod: "
                    + action.inputs);
        }

        navigateTo("/assets/new");
        waitForPageLoad();

        page.locator("#assetCode").fill(parts[0]);
        page.locator("#name").fill(parts[1]);

        List<Object[]> categoryRows = entityManager.createQuery(
                        "SELECT c.id, c.assetAccount.accountCode FROM AssetCategory c WHERE c.code = :code", Object[].class)
                .setParameter("code", parts[2])
                .getResultList();
        if (categoryRows.size() != 1) {
            throw new IllegalStateException("Asset category " + parts[2] + " missing for " + parts[0]);
        }
        String categoryId = categoryRows.get(0)[0].toString();
        String assetAccountCode = (String) categoryRows.get(0)[1];
        page.locator("#category").selectOption(categoryId);

        page.locator("#purchaseDate").fill(action.date.toString());
        page.locator("#purchaseCost").fill(parts[3]);
        page.locator("#depreciationStartDate").fill(action.date.toString());
        page.locator("#depreciationMethod").selectOption(parts[6]);
        page.locator("#usefulLifeMonths").fill(parts[4]);
        page.locator("#residualValue").fill(parts[5]);

        // Funding account: the first account the picker lists. The acquisition journal it
        // funds stays DRAFT; the purchase is posted by the Pembelian Aset Tetap row below.
        List<String> accounts = AccountPicker.searchCodes(page, baseUrl(), "");
        if (accounts.isEmpty()) {
            throw new IllegalStateException("Account picker lists no accounts for funding " + parts[0]);
        }
        AccountPicker.select(page, page.locator("#fundingAccount"), accounts.get(0));

        // Demo assets auto-post their depreciation so balances reflect the periods.
        page.locator("#autoPost").check();

        tutorialScreenshot("asset-form");

        page.locator("#btn-simpan").click();
        waitForPageLoad();

        List<Object[]> stored = entityManager.createQuery(
                        "SELECT a.purchaseCost, a.category.code FROM FixedAsset a WHERE a.assetCode = :code", Object[].class)
                .setParameter("code", parts[0])
                .getResultList();
        if (stored.size() != 1) {
            throw new IllegalStateException("Fixed asset " + parts[0] + " not stored — URL: " + page.url());
        }
        if (((BigDecimal) stored.get(0)[0]).compareTo(new BigDecimal(parts[3])) != 0 || !parts[2].equals(stored.get(0)[1])) {
            throw new IllegalStateException("Fixed asset " + parts[0] + " stored with cost " + stored.get(0)[0]
                    + " and category " + stored.get(0)[1] + ", expected " + parts[3] + " and " + parts[2]);
        }

        tutorialScreenshot("asset-detail");

        log.info("Fixed asset created: {} - {}", parts[0], parts[1]);

        // Purchase journal via the DETAILED "Pembelian Aset Tetap" template: debit the
        // category's asset account, credit bank.
        DemoAction purchaseAction = new DemoAction(
                action.date, "Pembelian Aset Tetap", 0,
                "var_assetCost:" + parts[3] + "|ASET_TETAP:" + assetAccountCode,
                "Pembelian " + parts[1], action.reference, "", "POST");
        createTransaction(purchaseAction);
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
        assertThat(page.locator("#page-title")).hasText("Dashboard");
        var cashBank = dashboardService.calculateCashBankBreakdown(YearMonth.now().atEndOfMonth());
        if (cashBank.isEmpty()) {
            throw new IllegalStateException("No cash/bank accounts for the dashboard breakdown");
        }
        for (var item : cashBank) {
            assertThat(page.locator("#section-saldo-kas")).containsText(item.accountName().replace("Bank ", ""));
        }
        log.info("Dashboard shows {} cash/bank accounts", cashBank.size());
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
                if (parts.length != 8) {
                    throw new IllegalStateException(resourcePath + ": expected 8 columns, got "
                            + parts.length + ": " + line);
                }
                String status = parts[7].trim();
                if (!status.equals("POST") && !status.equals("DRAFT")) {
                    throw new IllegalStateException(resourcePath + ": status must be POST or DRAFT: " + line);
                }
                String amount = parts[2].trim();
                actions.add(new DemoAction(
                        LocalDate.parse(parts[0].trim()),
                        parts[1].trim(),
                        amount.isEmpty() ? 0 : Long.parseLong(amount),
                        parts[3].trim(),
                        parts[4].trim(),
                        parts[5].trim(),
                        parts[6].trim(),
                        status
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load demo actions: " + resourcePath, e);
        }
        return actions;
    }

    private boolean isAccountHint(String key) {
        return key.equals("BANK") || key.equals("PENDAPATAN") || key.equals("BEBAN") ||
               key.equals("PIUTANG") || key.equals("HUTANG") || key.equals("FIXED_ASSET") || key.equals("ASET_TETAP") ||
               key.equals("DEBIT_ACCOUNT") || key.equals("CREDIT_ACCOUNT");
    }

    private Map<String, String> parseInputs(String inputs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty()) return result;
        for (String pair : inputs.split("\\|")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                throw new IllegalArgumentException("Malformed input '" + pair + "' in: " + inputs);
            }
            result.put(kv[0].trim(), kv[1].trim());
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
    protected record FiscalAdj(String description, String category, String direction, long amount) {}

    protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
                // Beda Tetap - Positif: entertainment/jamuan tidak ada daftar nominatif
                new FiscalAdj("Beban entertainment tanpa daftar nominatif", "PERMANENT", "POSITIVE", 5000000),
                // Beda Tetap - Positif: sumbangan/donasi non-deductible
                new FiscalAdj("Sumbangan non-deductible", "PERMANENT", "POSITIVE", 2000000),
                // Beda Waktu - Positif: penyusutan komersial > fiskal
                new FiscalAdj("Selisih penyusutan komersial vs fiskal", "TEMPORARY", "POSITIVE", 3000000)
        );
    }

    private void createFiscalAdjustments(int year) {
        var adjustments = fiscalAdjustments();

        for (var adj : adjustments) {
            navigateTo("/reports/rekonsiliasi-fiskal?year=" + year);
            waitForPageLoad();

            page.locator("#adj-description").fill(adj.description());
            page.locator("#adj-category").selectOption(adj.category());
            page.locator("#adj-direction").selectOption(adj.direction());
            page.locator("#adj-amount").fill(String.valueOf(adj.amount()));

            page.locator("#btn-add-adjustment").click();
            waitForPageLoad();

            long stored = fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(year).stream()
                    .filter(a -> a.getDescription().equals(adj.description())
                            && a.getAdjustmentCategory().name().equals(adj.category())
                            && a.getAdjustmentDirection().name().equals(adj.direction())
                            && a.getAmount().compareTo(BigDecimal.valueOf(adj.amount())) == 0)
                    .count();
            if (stored != 1) {
                throw new IllegalStateException(stored + " stored fiscal adjustments match " + adj + " for " + year);
            }
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
        String truncated = s.length() > width ? s.substring(0, width) : s;
        return truncated + " ".repeat(width - truncated.length());
    }

    private String padLeft(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
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
