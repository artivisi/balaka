package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.*;
import com.artivisi.accountingfinance.enums.*;
import com.artivisi.accountingfinance.repository.*;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for importing data from a ZIP archive exported by DataExportService.
 * Only truncates and replaces tables that have actual data in the CSV files.
 * Tables with empty CSV (header only) are left untouched, preserving existing data.
 * Uses Map pre-load strategy for O(1) reference lookups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("java:S1192") // Table names and CSV filenames must match database schema exactly
public class DataImportService {

    private final EntityManager entityManager;
    private final DocumentStorageService documentStorageService;
    private final PasswordEncoder passwordEncoder;

    // Core repositories
    private final ChartOfAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;
    private final CompanyConfigRepository companyConfigRepository;

    // Additional repositories
    private final JournalTemplateRepository templateRepository;
    private final JournalTemplateLineRepository templateLineRepository;
    private final JournalTemplateTagRepository templateTagRepository;
    private final SalaryComponentRepository salaryComponentRepository;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final TaxDeadlineRepository taxDeadlineRepository;
    private final TaxDeadlineCompletionRepository taxDeadlineCompletionRepository;
    private final CompanyBankAccountRepository bankAccountRepository;
    private final MerchantMappingRepository merchantMappingRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectPaymentTermRepository paymentTermRepository;
    private final AmortizationScheduleRepository amortizationScheduleRepository;
    private final AmortizationEntryRepository amortizationEntryRepository;
    private final TaxTransactionDetailRepository taxTransactionDetailRepository;
    private final DraftTransactionRepository draftTransactionRepository;
    private final UserRepository userRepository;
    private final UserTemplatePreferenceRepository userTemplatePreferenceRepository;
    private final TelegramUserLinkRepository telegramUserLinkRepository;
    private final TransactionSequenceRepository transactionSequenceRepository;
    private final AssetCategoryRepository assetCategoryRepository;

    // Manufacturing/Inventory repositories
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final BillOfMaterialRepository billOfMaterialRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Import function registry (initialized lazily to allow instance method references)
    private Map<String, ToIntFunction<String>> importFunctions;

    // Reference maps for O(1) lookups (populated during import)
    private Map<String, ChartOfAccount> accountMap;
    private Map<String, JournalTemplate> templateMap;
    private Map<String, Client> clientMap;
    private Map<String, Project> projectMap;
    private Map<String, Employee> employeeMap;
    private Map<String, SalaryComponent> salaryComponentMap;
    private Map<String, User> userMap;
    private Map<String, PayrollRun> payrollRunMap;
    private Map<UUID, Transaction> transactionMap;  // Keyed by UUID to handle null transaction_number
    private Map<String, AmortizationSchedule> amortizationScheduleMap;
    private Map<TaxDeadlineType, TaxDeadline> taxDeadlineMap;
    private Map<String, ProjectMilestone> milestoneMap;
    private Map<String, ProductCategory> productCategoryMap;
    private Map<String, Product> productMap;
    private Map<String, BillOfMaterial> billOfMaterialMap;

    /**
     * Import data from a ZIP archive.
     * Only truncates and replaces tables that have actual data in the CSV files.
     * Tables with empty CSV (header only) are left untouched.
     */
    @Transactional
    public ImportResult importAllData(byte[] zipData) throws IOException {
        log.info("Starting data import");
        long startTime = System.currentTimeMillis();

        // Extract ZIP contents
        Map<String, String> csvFiles = new HashMap<>();
        Map<String, byte[]> documentFiles = new HashMap<>();
        extractZip(zipData, csvFiles, documentFiles);

        // Determine which files have actual data (more than just header)
        Set<String> filesWithData = csvFiles.entrySet().stream()
                .filter(e -> hasData(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        log.info("Files with data: {}", LogSanitizer.sanitize(filesWithData.toString()));

        // Truncate only tables that will be imported
        truncateTablesForFiles(filesWithData);

        // Initialize reference maps with existing data
        initializeMapsFromDatabase();

        // Import in filename order (dependency order)
        int totalRecords = 0;
        List<String> sortedFiles = filesWithData.stream().sorted().toList();

        for (String filename : sortedFiles) {
            String content = csvFiles.get(filename);
            int count = importCsvFile(filename, content);
            totalRecords += count;
            log.info("Imported {} records from {}", count, LogSanitizer.filename(filename));
        }

        // Import document files
        int documentCount = importDocumentFiles(documentFiles);
        log.info("Imported {} document files", documentCount);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Data import completed in {}ms, {} total records", duration, totalRecords);

        return new ImportResult(totalRecords, documentCount, duration);
    }

    /**
     * Check if CSV content has actual data rows (not just header).
     */
    private boolean hasData(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) return false;
        // Count non-empty lines after header
        String[] lines = csvContent.split("\n");
        int dataLines = 0;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                dataLines++;
            }
        }
        return dataLines > 0;
    }

    private void extractZip(byte[] zipData, Map<String, String> csvFiles, Map<String, byte[]> documentFiles) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                processZipEntry(entry, zis, csvFiles, documentFiles);
                zis.closeEntry();
            }
        }
    }

    private void processZipEntry(ZipEntry entry, ZipInputStream zis, Map<String, String> csvFiles, Map<String, byte[]> documentFiles) throws IOException {
        if (entry.isDirectory()) {
            return;
        }

        String name = entry.getName();

        // Zip slip protection: reject entries with path traversal
        if (isPathTraversal(name)) {
            log.warn("Rejected potentially malicious zip entry: {}", LogSanitizer.filename(name));
            return;
        }

        byte[] content = zis.readAllBytes();
        classifyAndStoreEntry(name, content, csvFiles, documentFiles);
    }

    private boolean isPathTraversal(String name) {
        return name.contains("..") || name.startsWith("/") || name.startsWith("\\");
    }

    private void classifyAndStoreEntry(String name, byte[] content, Map<String, String> csvFiles, Map<String, byte[]> documentFiles) {
        if (name.endsWith(".csv") && !name.startsWith("documents/") && !name.startsWith("company_logo/")) {
            csvFiles.put(name, new String(content, StandardCharsets.UTF_8));
        } else if (name.startsWith("documents/") && !name.equals("documents/index.csv")) {
            documentFiles.put(name.substring("documents/".length()), content);
        } else if (name.equals("documents/index.csv")) {
            csvFiles.put(name, new String(content, StandardCharsets.UTF_8));
        } else if (name.startsWith("company_logo/")) {
            documentFiles.put("company_logo:" + name.substring("company_logo/".length()), content);
        }
    }

    // Whitelist of allowed table names for TRUNCATE operations (SQL injection prevention)
    // Only these table names are allowed in native SQL queries
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "company_config", "chart_of_accounts", "salary_components", "employee_salary_components",
            "journal_templates", "journal_template_lines", "journal_template_tags",
            "clients", "projects", "project_milestones", "project_payment_terms",
            "fiscal_periods", "tax_deadlines", "tax_deadline_completions",
            "company_bank_accounts", "merchant_mappings", "employees", "invoices",
            "transactions", "transaction_account_mappings", "transaction_variables", "tax_transaction_details", "documents",
            "journal_entries", "payroll_runs", "payroll_details",
            "amortization_schedules", "amortization_entries", "draft_transactions",
            "users", "user_roles", "user_template_preferences", "telegram_user_links", "audit_logs",
            "transaction_sequences", "asset_categories",
            // Manufacturing tables
            "product_categories", "products", "bill_of_materials", "bill_of_material_lines",
            "production_orders", "inventory_transactions", "inventory_fifo_layers", "inventory_balances",
            // Recurring transaction tables
            "recurring_transactions", "recurring_transaction_account_mappings", "recurring_transaction_logs"
    );

    // Mapping from CSV filename to table name(s) that should be truncated
    // Includes dependent tables that would have broken references
    private static final Map<String, List<String>> FILE_TO_TABLES = Map.ofEntries(
            Map.entry("01_company_config.csv", List.of("company_config")),
            // COA change invalidates all journal entries and transactions
            Map.entry("02_chart_of_accounts.csv", List.of(
                    "journal_entries", "transaction_account_mappings", "transaction_variables", "tax_transaction_details",
                    "transactions", "amortization_entries", "amortization_schedules",
                    "recurring_transaction_account_mappings",
                    "documents", "chart_of_accounts")),
            Map.entry("03_salary_components.csv", List.of("employee_salary_components", "salary_components")),
            // Template change invalidates transactions, merchant mappings, payment terms
            Map.entry("04_journal_templates.csv", List.of(
                    "journal_entries", "transaction_account_mappings", "transaction_variables", "tax_transaction_details",
                    "transactions", "merchant_mappings", "project_payment_terms",
                    "recurring_transaction_logs", "recurring_transaction_account_mappings", "recurring_transactions",
                    "user_template_preferences", "journal_template_tags", "journal_template_lines", "journal_templates")),
            Map.entry("07_clients.csv", List.of("invoices", "projects", "clients")),
            Map.entry("08_projects.csv", List.of("project_payment_terms", "project_milestones", "projects")),
            Map.entry("11_fiscal_periods.csv", List.of("fiscal_periods")),
            Map.entry("12_tax_deadlines.csv", List.of("tax_deadline_completions", "tax_deadlines")),
            Map.entry("13_company_bank_accounts.csv", List.of("company_bank_accounts")),
            Map.entry("14_merchant_mappings.csv", List.of("merchant_mappings")),
            Map.entry("15_employees.csv", List.of("payroll_details", "employee_salary_components", "employees")),
            Map.entry("17_invoices.csv", List.of("invoices")),
            Map.entry("18_transactions.csv", List.of("journal_entries", "transaction_account_mappings", "transaction_variables", "tax_transaction_details", "recurring_transaction_logs", "documents", "transactions")),
            Map.entry("21_payroll_runs.csv", List.of("payroll_details", "payroll_runs")),
            Map.entry("23_amortization_schedules.csv", List.of("amortization_entries", "amortization_schedules")),
            Map.entry("27_draft_transactions.csv", List.of("draft_transactions")),
            Map.entry("28_users.csv", List.of("telegram_user_links", "user_template_preferences", "user_roles", "audit_logs", "users")),
            Map.entry("33_transaction_sequences.csv", List.of("transaction_sequences")),
            Map.entry("34_asset_categories.csv", List.of("asset_categories")),
            // Manufacturing tables - with dependency order
            Map.entry("35_product_categories.csv", List.of(
                    "inventory_balances", "inventory_fifo_layers", "inventory_transactions",
                    "production_orders", "bill_of_material_lines", "bill_of_materials",
                    "products", "product_categories")),
            Map.entry("36_products.csv", List.of(
                    "inventory_balances", "inventory_fifo_layers", "inventory_transactions",
                    "production_orders", "bill_of_material_lines", "bill_of_materials", "products")),
            Map.entry("37_bill_of_materials.csv", List.of(
                    "production_orders", "bill_of_material_lines", "bill_of_materials")),
            Map.entry("39_production_orders.csv", List.of("production_orders")),
            Map.entry("40_inventory_transactions.csv", List.of(
                    "inventory_balances", "inventory_fifo_layers", "inventory_transactions")),
            Map.entry("41_inventory_balances.csv", List.of("inventory_balances"))
    );

    private void truncateTablesForFiles(Set<String> filesWithData) {
        Set<String> tablesToTruncate = new LinkedHashSet<>();

        // Collect tables to truncate based on files with data
        for (String file : filesWithData) {
            List<String> tables = FILE_TO_TABLES.get(file);
            if (tables != null) {
                tablesToTruncate.addAll(tables);
            }
        }

        log.info("Truncating tables: {}", tablesToTruncate);

        for (String table : tablesToTruncate) {
            // Security: Validate table name against whitelist to prevent SQL injection
            if (!ALLOWED_TABLES.contains(table)) {
                log.error("Attempted to truncate non-whitelisted table: {}", table);
                throw new IllegalArgumentException("Table not in allowed list: " + table);
            }

            try {
                if ("journal_templates".equals(table)) {
                    // Preserve system templates - only delete non-system templates
                    entityManager.createNativeQuery(
                        "DELETE FROM journal_templates WHERE is_system = false"
                    ).executeUpdate();
                } else if ("journal_template_lines".equals(table)) {
                    // Delete lines for non-system templates only
                    entityManager.createNativeQuery("""
                        DELETE FROM journal_template_lines WHERE id_journal_template IN \
                        (SELECT id FROM journal_templates WHERE is_system = false)"""
                    ).executeUpdate();
                } else if ("journal_template_tags".equals(table)) {
                    // Delete tags for non-system templates only
                    entityManager.createNativeQuery("""
                        DELETE FROM journal_template_tags WHERE id_journal_template IN \
                        (SELECT id FROM journal_templates WHERE is_system = false)"""
                    ).executeUpdate();
                } else {
                    // Table name validated against ALLOWED_TABLES whitelist above
                    truncateTable(table);
                }
            } catch (Exception e) {
                log.warn("Could not truncate {}: {}", LogSanitizer.sanitize(table), e.getMessage());
            }
        }

        entityManager.flush();
    }

    // Static map of table name to TRUNCATE SQL - avoids switch with too many cases
    // and prevents SQL injection by only allowing whitelisted tables
    private static final Map<String, String> TABLE_TRUNCATE_SQL;
    static {
        Map<String, String> map = new java.util.HashMap<>();
        for (String t : ALLOWED_TABLES) {
            map.put(t, "TRUNCATE TABLE " + t + " CASCADE");
        }
        TABLE_TRUNCATE_SQL = Map.copyOf(map);
    }

    /**
     * Truncate a table using pre-defined SQL from the whitelist.
     * Uses static map to avoid string concatenation (SQL injection safe).
     */
    private void truncateTable(String table) {
        String sql = TABLE_TRUNCATE_SQL.get(table);
        if (sql == null) {
            throw new IllegalArgumentException("Unknown table: " + table);
        }
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    private void initializeMapsFromDatabase() {
        // Initialize maps with existing data from database
        accountMap = new HashMap<>();
        for (ChartOfAccount a : accountRepository.findAll()) {
            accountMap.put(a.getAccountCode(), a);
        }

        templateMap = new HashMap<>();
        for (JournalTemplate t : templateRepository.findAll()) {
            templateMap.put(t.getTemplateName(), t);
        }

        clientMap = new HashMap<>();
        for (Client c : clientRepository.findAll()) {
            clientMap.put(c.getCode(), c);
        }

        projectMap = new HashMap<>();
        for (Project p : projectRepository.findAll()) {
            projectMap.put(p.getCode(), p);
        }

        employeeMap = new HashMap<>();
        for (Employee e : employeeRepository.findAll()) {
            employeeMap.put(e.getEmployeeId(), e);
        }

        salaryComponentMap = new HashMap<>();
        for (SalaryComponent sc : salaryComponentRepository.findAll()) {
            salaryComponentMap.put(sc.getCode(), sc);
        }

        userMap = new HashMap<>();
        for (User u : userRepository.findAll()) {
            userMap.put(u.getUsername(), u);
        }

        payrollRunMap = new HashMap<>();
        for (PayrollRun pr : payrollRunRepository.findAll()) {
            payrollRunMap.put(pr.getPayrollPeriod(), pr);
        }

        transactionMap = new HashMap<>();
        for (Transaction t : transactionRepository.findAll()) {
            transactionMap.put(t.getId(), t);
        }

        amortizationScheduleMap = new HashMap<>();
        for (AmortizationSchedule as : amortizationScheduleRepository.findAll()) {
            amortizationScheduleMap.put(as.getCode(), as);
        }

        taxDeadlineMap = new EnumMap<>(TaxDeadlineType.class);
        for (TaxDeadline td : taxDeadlineRepository.findAll()) {
            taxDeadlineMap.put(td.getDeadlineType(), td);
        }

        milestoneMap = new HashMap<>();
        for (ProjectMilestone m : milestoneRepository.findAll()) {
            milestoneMap.put(m.getProject().getCode() + "_" + m.getSequence(), m);
        }

        // Initialize manufacturing maps
        productCategoryMap = new HashMap<>();
        for (ProductCategory pc : productCategoryRepository.findAll()) {
            productCategoryMap.put(pc.getCode(), pc);
        }

        productMap = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            productMap.put(p.getCode(), p);
        }

        billOfMaterialMap = new HashMap<>();
        for (BillOfMaterial bom : billOfMaterialRepository.findAll()) {
            billOfMaterialMap.put(bom.getCode(), bom);
        }

        // Initialize import functions registry
        initializeImportFunctions();
    }

    private void initializeImportFunctions() {
        importFunctions = new HashMap<>();
        // Core data (01-10)
        registerCoreDataImports();
        // Financial data (11-20)
        registerFinancialDataImports();
        // Payroll and admin (21-35)
        registerPayrollAndAdminImports();
        // Manufacturing (35-41)
        registerManufacturingImports();
    }

    private void registerCoreDataImports() {
        importFunctions.put("01_company_config.csv", this::importCompanyConfig);
        importFunctions.put("02_chart_of_accounts.csv", this::importChartOfAccounts);
        importFunctions.put("03_salary_components.csv", this::importSalaryComponents);
        importFunctions.put("04_journal_templates.csv", this::importJournalTemplates);
        importFunctions.put("05_journal_template_lines.csv", this::importJournalTemplateLines);
        importFunctions.put("06_journal_template_tags.csv", this::importJournalTemplateTags);
        importFunctions.put("07_clients.csv", this::importClients);
        importFunctions.put("08_projects.csv", this::importProjects);
        importFunctions.put("09_project_milestones.csv", this::importProjectMilestones);
        importFunctions.put("10_project_payment_terms.csv", this::importProjectPaymentTerms);
    }

    private void registerFinancialDataImports() {
        importFunctions.put("11_fiscal_periods.csv", this::importFiscalPeriods);
        importFunctions.put("12_tax_deadlines.csv", this::importTaxDeadlines);
        importFunctions.put("13_company_bank_accounts.csv", this::importCompanyBankAccounts);
        importFunctions.put("14_merchant_mappings.csv", this::importMerchantMappings);
        importFunctions.put("15_employees.csv", this::importEmployees);
        importFunctions.put("16_employee_salary_components.csv", this::importEmployeeSalaryComponents);
        importFunctions.put("17_invoices.csv", this::importInvoices);
        importFunctions.put("18_transactions.csv", this::importTransactions);
        importFunctions.put("19_transaction_account_mappings.csv", this::importTransactionAccountMappings);
        importFunctions.put("19a_transaction_variables.csv", this::importTransactionVariables);
        importFunctions.put("20_journal_entries.csv", this::importJournalEntries);
    }

    private void registerPayrollAndAdminImports() {
        importFunctions.put("21_payroll_runs.csv", this::importPayrollRuns);
        importFunctions.put("22_payroll_details.csv", this::importPayrollDetails);
        importFunctions.put("23_amortization_schedules.csv", this::importAmortizationSchedules);
        importFunctions.put("24_amortization_entries.csv", this::importAmortizationEntries);
        importFunctions.put("25_tax_transaction_details.csv", this::importTaxTransactionDetails);
        importFunctions.put("26_tax_deadline_completions.csv", this::importTaxDeadlineCompletions);
        importFunctions.put("27_draft_transactions.csv", this::importDraftTransactions);
        importFunctions.put("28_users.csv", this::importUsers);
        importFunctions.put("29_user_roles.csv", this::importUserRoles);
        importFunctions.put("30_user_template_preferences.csv", this::importUserTemplatePreferences);
        importFunctions.put("31_telegram_user_links.csv", this::importTelegramUserLinks);
        importFunctions.put("32_audit_logs.csv", this::importAuditLogs);
        importFunctions.put("33_transaction_sequences.csv", this::importTransactionSequences);
        importFunctions.put("34_asset_categories.csv", this::importAssetCategories);
    }

    private void registerManufacturingImports() {
        importFunctions.put("35_product_categories.csv", this::importProductCategories);
        importFunctions.put("36_products.csv", this::importProducts);
        importFunctions.put("37_bill_of_materials.csv", this::importBillOfMaterials);
        importFunctions.put("38_bom_lines.csv", this::importBomLines);
        importFunctions.put("39_production_orders.csv", this::importProductionOrders);
        importFunctions.put("40_inventory_transactions.csv", this::importInventoryTransactions);
        importFunctions.put("41_inventory_balances.csv", this::importInventoryBalances);
    }

    private int importCsvFile(String filename, String content) {
        try {
            // Skip document index (handled separately) and manifest
            if ("documents/index.csv".equals(filename)) {
                return 0;
            }

            ToIntFunction<String> importFunction = importFunctions.get(filename);
            if (importFunction != null) {
                return importFunction.applyAsInt(content);
            }

            if (!"MANIFEST.md".equals(filename)) {
                log.warn("Unknown file in import: {}", LogSanitizer.filename(filename));
            }
            return 0;
        } catch (Exception e) {
            log.warn("Error importing file {}: {}", LogSanitizer.filename(filename), e.getMessage(), e);
            throw new IllegalStateException("Failed to import " + filename + ": " + e.getMessage(), e);
        }
    }

    // ============================================
    // CSV Parsing Utilities
    // ============================================
    private List<String[]> parseCsv(String content) {
        CsvParseState state = new CsvParseState();

        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            int nextIndex = state.inQuotes
                    ? processQuotedChar(state, content, i, c)
                    : processUnquotedChar(state, content, i, c);
            i = nextIndex + 1;
        }

        finalizeLastRow(state);
        return state.rows;
    }

    private static class CsvParseState {
        List<String[]> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean isFirstRow = true;
    }

    private int processQuotedChar(CsvParseState state, String content, int i, char c) {
        if (c == '"') {
            if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                state.field.append('"');
                return i + 1; // Skip escaped quote
            }
            state.inQuotes = false;
        } else {
            state.field.append(c);
        }
        return i;
    }

    private int processUnquotedChar(CsvParseState state, String content, int i, char c) {
        if (c == '"') {
            state.inQuotes = true;
        } else if (c == ',') {
            state.currentRow.add(state.field.toString());
            state.field = new StringBuilder();
        } else if (c == '\n' || c == '\r') {
            i = handleNewline(state, content, i, c);
        } else {
            state.field.append(c);
        }
        return i;
    }

    private int handleNewline(CsvParseState state, String content, int i, char c) {
        if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
            i++; // Skip \n in \r\n
        }
        state.currentRow.add(state.field.toString());
        state.field = new StringBuilder();
        addRowIfValid(state);
        state.currentRow = new ArrayList<>();
        return i;
    }

    private void addRowIfValid(CsvParseState state) {
        if (state.isFirstRow) {
            state.isFirstRow = false;
        } else if (!state.currentRow.isEmpty() && !state.currentRow.stream().allMatch(String::isEmpty)) {
            state.rows.add(state.currentRow.toArray(new String[0]));
        }
    }

    private void finalizeLastRow(CsvParseState state) {
        if (!state.currentRow.isEmpty() || !state.field.isEmpty()) {
            state.currentRow.add(state.field.toString());
            addRowIfValid(state);
        }
    }

    private String getField(String[] row, int index) {
        if (index >= row.length) return "";
        return row[index].trim();
    }

    private String getFieldOrNull(String[] row, int index) {
        String value = getField(row, index);
        return value.isEmpty() ? null : value;
    }

    private String[] parseStringArray(String value) {
        if (value == null || value.isEmpty()) return null;
        return java.util.Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal value: " + LogSanitizer.sanitize(value), e);
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value: " + LogSanitizer.sanitize(value), e);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long value: " + LogSanitizer.sanitize(value), e);
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.isEmpty()) return false;
        return Boolean.parseBoolean(value);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date value: " + LogSanitizer.sanitize(value) + " (expected format: yyyy-MM-dd)", e);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value, DATETIME_FORMATTER);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid datetime value: " + LogSanitizer.sanitize(value) + " (expected format: yyyy-MM-dd HH:mm:ss)", e);
        }
    }

    // ============================================
    // Field Setting Helpers (reduce cognitive complexity)
    // ============================================

    private void setBigDecimalField(String[] row, int index, Consumer<BigDecimal> setter) {
        String value = getField(row, index);
        if (!value.isEmpty()) {
            setter.accept(new BigDecimal(value));
        }
    }

    private void setDateField(String[] row, int index, Consumer<LocalDate> setter) {
        String value = getField(row, index);
        if (!value.isEmpty()) {
            setter.accept(LocalDate.parse(value, DATE_FORMATTER));
        }
    }

    private <E extends Enum<E>> void setEnumField(String[] row, int index, Class<E> enumClass, Consumer<E> setter) {
        String value = getField(row, index);
        if (!value.isEmpty()) {
            setter.accept(Enum.valueOf(enumClass, value));
        }
    }

    private <T> void setFromMap(String[] row, int index, Map<String, T> map, Consumer<T> setter) {
        String key = getField(row, index);
        if (!key.isEmpty() && map != null) {
            setter.accept(map.get(key));
        }
    }

    // ============================================
    // Import Methods
    // ============================================

    private int importCompanyConfig(String content) {
        List<String[]> rows = parseCsv(content);
        if (rows.isEmpty()) return 0;

        String[] row = rows.get(0);
        CompanyConfig config = new CompanyConfig();
        config.setCompanyName(getField(row, 0));
        config.setCompanyAddress(getField(row, 1));
        config.setCompanyPhone(getField(row, 2));
        config.setCompanyEmail(getField(row, 3));
        config.setTaxId(getField(row, 4));
        config.setNpwp(getField(row, 5));
        config.setNitku(getField(row, 6));
        config.setFiscalYearStartMonth(parseInteger(getField(row, 7)));
        config.setCurrencyCode(getField(row, 8));
        config.setSigningOfficerName(getField(row, 9));
        config.setSigningOfficerTitle(getField(row, 10));
        // column 11 = company_logo_path (set after logo file is imported)
        String logoPath = getField(row, 11);
        if (!logoPath.isEmpty()) {
            config.setCompanyLogoPath(logoPath);
        }
        // Tax profile fields (columns 12-14)
        String establishedDateStr = getField(row, 12);
        if (!establishedDateStr.isEmpty()) {
            config.setEstablishedDate(parseDate(establishedDateStr));
        }
        String isPkpStr = getField(row, 13);
        if (!isPkpStr.isEmpty()) {
            config.setIsPkp(parseBoolean(isPkpStr));
        }
        String pkpSinceStr = getField(row, 14);
        if (!pkpSinceStr.isEmpty()) {
            config.setPkpSince(parseDate(pkpSinceStr));
        }
        // column 15 = industry
        String industryStr = getField(row, 15);
        if (!industryStr.isEmpty()) {
            config.setIndustry(industryStr);
        }

        companyConfigRepository.save(config);
        return 1;
    }

    private int importChartOfAccounts(String content) {
        List<String[]> rows = parseCsv(content);
        List<ChartOfAccount> accounts = new ArrayList<>();
        Map<String, String> parentCodes = new HashMap<>();

        // First pass: create all accounts without parents
        // CSV columns: account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent
        for (String[] row : rows) {
            ChartOfAccount account = new ChartOfAccount();
            account.setAccountCode(getField(row, 0));
            account.setAccountName(getField(row, 1));
            account.setAccountType(AccountType.valueOf(getField(row, 2)));
            parentCodes.put(getField(row, 0), getField(row, 3));
            account.setNormalBalance(NormalBalance.valueOf(getField(row, 4)));
            account.setActive(parseBoolean(getField(row, 5)));
            // column 6 = is_permanent (optional, defaults to entity default if empty)
            String permanentField = getField(row, 6);
            if (permanentField != null && !permanentField.isBlank()) {
                account.setPermanent(parseBoolean(permanentField));
            }
            accounts.add(account);
        }

        // Save all accounts first
        accountRepository.saveAll(accounts);
        accountRepository.flush();

        // Reload into map
        for (ChartOfAccount a : accountRepository.findAll()) {
            accountMap.put(a.getAccountCode(), a);
        }

        // Second pass: set parents
        for (ChartOfAccount account : accounts) {
            String parentCode = parentCodes.get(account.getAccountCode());
            if (parentCode != null && !parentCode.isEmpty()) {
                ChartOfAccount parent = accountMap.get(parentCode);
                if (parent != null) {
                    account.setParent(parent);
                }
            }
        }
        accountRepository.saveAll(accounts);

        return accounts.size();
    }

    private int importSalaryComponents(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            SalaryComponent sc = new SalaryComponent();
            sc.setCode(getField(row, 0));
            sc.setName(getField(row, 1));
            sc.setDescription(getField(row, 2));
            sc.setComponentType(SalaryComponentType.valueOf(getField(row, 3)));
            sc.setIsPercentage(parseBoolean(getField(row, 4)));
            sc.setDefaultRate(parseBigDecimal(getField(row, 5)));
            sc.setDefaultAmount(parseBigDecimal(getField(row, 6)));
            sc.setIsSystem(parseBoolean(getField(row, 7)));
            sc.setDisplayOrder(parseInteger(getField(row, 8)));
            sc.setActive(parseBoolean(getField(row, 9)));
            sc.setIsTaxable(parseBoolean(getField(row, 10)));
            sc.setBpjsCategory(getField(row, 11));

            salaryComponentRepository.save(sc);
            salaryComponentMap.put(sc.getCode(), sc);
        }
        return rows.size();
    }

    private int importJournalTemplates(String content) {
        List<String[]> rows = parseCsv(content);
        int imported = 0;

        for (String[] row : rows) {
            if (importSingleTemplate(row)) {
                imported++;
            }
        }
        return imported;
    }

    private boolean importSingleTemplate(String[] row) {
        String templateName = getField(row, 0);

        // Check if system template already exists (preserved from seed data)
        JournalTemplate existing = templateMap.get(templateName);
        if (existing != null && existing.getIsSystem()) {
            log.debug("Skipping existing system template: {}", templateName);
            return false;
        }

        JournalTemplate t = new JournalTemplate();
        t.setTemplateName(templateName);
        t.setCategory(TemplateCategory.valueOf(getField(row, 1)));
        t.setCashFlowCategory(CashFlowCategory.valueOf(getField(row, 2)));
        t.setTemplateType(TemplateType.valueOf(getField(row, 3)));
        t.setDescription(getField(row, 4));
        t.setIsSystem(parseBoolean(getField(row, 5)));
        t.setActive(parseBoolean(getField(row, 6)));
        t.setVersion(parseInteger(getField(row, 7)));
        t.setUsageCount(parseInteger(getField(row, 8)));
        t.setLastUsedAt(parseDateTime(getField(row, 9)));

        // AI-Friendly Semantic Metadata (fields 10-15)
        t.setSemanticDescription(getFieldOrNull(row, 10));
        t.setKeywords(parseStringArray(getField(row, 11)));
        t.setExampleMerchants(parseStringArray(getField(row, 12)));
        t.setTypicalAmountMin(parseBigDecimal(getField(row, 13)));
        t.setTypicalAmountMax(parseBigDecimal(getField(row, 14)));
        t.setMerchantPatterns(parseStringArray(getField(row, 15)));

        templateRepository.save(t);
        templateMap.put(t.getTemplateName(), t);
        return true;
    }

    private int importJournalTemplateLines(String content) {
        List<String[]> rows = parseCsv(content);
        int imported = 0;

        for (String[] row : rows) {
            if (importSingleTemplateLine(row)) {
                imported++;
            }
        }
        return imported;
    }

    private boolean importSingleTemplateLine(String[] row) {
        String templateName = getField(row, 0);
        JournalTemplate template = templateMap.get(templateName);

        if (template == null) {
            log.warn("Template not found for line: {}", templateName);
            return false;
        }

        // Skip lines for system templates (they already have lines from seed data)
        if (template.getIsSystem()) {
            return false;
        }

        JournalTemplateLine line = new JournalTemplateLine();
        line.setJournalTemplate(template);
        line.setLineOrder(parseInteger(getField(row, 1)));

        String accountCode = getField(row, 2);
        if (!accountCode.isEmpty()) {
            line.setAccount(accountMap.get(accountCode));
        }
        line.setAccountHint(getField(row, 3));
        line.setPosition(JournalPosition.valueOf(getField(row, 4)));
        line.setFormula(getField(row, 5));
        line.setDescription(getField(row, 6));

        templateLineRepository.save(line);
        return true;
    }

    private int importJournalTemplateTags(String content) {
        List<String[]> rows = parseCsv(content);
        int imported = 0;

        for (String[] row : rows) {
            if (importSingleTemplateTag(row)) {
                imported++;
            }
        }
        return imported;
    }

    private boolean importSingleTemplateTag(String[] row) {
        String templateName = getField(row, 0);
        JournalTemplate template = templateMap.get(templateName);

        if (template == null || template.getIsSystem()) {
            return false;
        }

        JournalTemplateTag tag = new JournalTemplateTag(template, getField(row, 1));
        templateTagRepository.save(tag);
        return true;
    }

    private int importClients(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at
        for (String[] row : rows) {
            Client c = new Client();
            c.setCode(getField(row, 0));
            c.setName(getField(row, 1));
            c.setContactPerson(getField(row, 2));
            c.setEmail(getField(row, 3));
            c.setPhone(getField(row, 4));
            c.setAddress(getField(row, 5));
            c.setNpwp(getField(row, 6));
            c.setNik(getField(row, 7));
            c.setNitku(getField(row, 8));
            c.setActive(parseBoolean(getField(row, 9)));
            // column 10 = created_at (ignored, auto-generated)

            clientRepository.save(c);
            clientMap.put(c.getCode(), c);
        }
        return rows.size();
    }

    private int importProjects(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: code,name,client_code,status,start_date,end_date,budget_amount,contract_value,description,created_at
        for (String[] row : rows) {
            Project p = new Project();
            p.setCode(getField(row, 0));
            p.setName(getField(row, 1));

            String clientCode = getField(row, 2);
            if (!clientCode.isEmpty()) {
                p.setClient(clientMap.get(clientCode));
            }
            p.setStatus(ProjectStatus.valueOf(getField(row, 3)));
            p.setStartDate(parseDate(getField(row, 4)));
            p.setEndDate(parseDate(getField(row, 5)));
            p.setBudgetAmount(parseBigDecimal(getField(row, 6)));
            p.setContractValue(parseBigDecimal(getField(row, 7)));
            p.setDescription(getField(row, 8));
            // column 9 = created_at (ignored, auto-generated)

            projectRepository.save(p);
            projectMap.put(p.getCode(), p);
        }
        return rows.size();
    }

    private int importProjectMilestones(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            String projectCode = getField(row, 0);
            Project project = projectMap.get(projectCode);
            if (project == null) continue;

            ProjectMilestone m = new ProjectMilestone();
            m.setProject(project);
            m.setSequence(parseInteger(getField(row, 1)));
            m.setName(getField(row, 2));
            m.setDescription(getField(row, 3));
            m.setStatus(MilestoneStatus.valueOf(getField(row, 4)));
            m.setWeightPercent(parseInteger(getField(row, 5)));
            m.setTargetDate(parseDate(getField(row, 6)));
            m.setActualDate(parseDate(getField(row, 7)));

            milestoneRepository.save(m);
            milestoneMap.put(projectCode + "_" + m.getSequence(), m);
        }
        return rows.size();
    }

    private int importProjectPaymentTerms(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            String projectCode = getField(row, 0);
            Project project = projectMap.get(projectCode);
            if (project == null) continue;

            ProjectPaymentTerm pt = new ProjectPaymentTerm();
            pt.setProject(project);
            pt.setSequence(parseInteger(getField(row, 1)));

            String milestoneSeq = getField(row, 2);
            if (!milestoneSeq.isEmpty()) {
                pt.setMilestone(milestoneMap.get(projectCode + "_" + milestoneSeq));
            }

            String templateName = getField(row, 3);
            if (!templateName.isEmpty()) {
                pt.setTemplate(templateMap.get(templateName));
            }

            pt.setName(getField(row, 4));
            // is_percentage is derived - skip field 5
            pt.setPercentage(parseBigDecimal(getField(row, 6)));
            pt.setAmount(parseBigDecimal(getField(row, 7)));
            pt.setDueTrigger(PaymentTrigger.valueOf(getField(row, 8)));
            pt.setAutoPost(parseBoolean(getField(row, 9)));

            paymentTermRepository.save(pt);
        }
        return rows.size();
    }

    private int importFiscalPeriods(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            FiscalPeriod fp = new FiscalPeriod();
            fp.setYear(parseInteger(getField(row, 0)));
            fp.setMonth(parseInteger(getField(row, 1)));
            fp.setStatus(FiscalPeriodStatus.valueOf(getField(row, 2)));
            fp.setMonthClosedAt(parseDateTime(getField(row, 3)));
            fp.setMonthClosedBy(getField(row, 4));
            fp.setTaxFiledAt(parseDateTime(getField(row, 5)));
            fp.setTaxFiledBy(getField(row, 6));

            fiscalPeriodRepository.save(fp);
        }
        return rows.size();
    }

    private int importTaxDeadlines(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: deadline_type,name,description,due_day,use_last_day_of_month,reminder_days_before,active
        for (String[] row : rows) {
            TaxDeadline td = new TaxDeadline();
            TaxDeadlineType type = TaxDeadlineType.valueOf(getField(row, 0));
            td.setDeadlineType(type);
            td.setName(getField(row, 1));
            td.setDescription(getField(row, 2));
            td.setDueDay(parseInteger(getField(row, 3)));
            td.setUseLastDayOfMonth(parseBoolean(getField(row, 4)));
            td.setReminderDaysBefore(parseInteger(getField(row, 5)));
            td.setActive(parseBoolean(getField(row, 6)));

            taxDeadlineRepository.save(td);
            taxDeadlineMap.put(type, td);
        }
        return rows.size();
    }

    private int importCompanyBankAccounts(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            CompanyBankAccount ba = new CompanyBankAccount();
            ba.setBankName(getField(row, 0));
            ba.setAccountNumber(getField(row, 1));
            ba.setAccountName(getField(row, 2));
            ba.setBankBranch(getField(row, 3));
            ba.setIsDefault(parseBoolean(getField(row, 4)));
            ba.setActive(parseBoolean(getField(row, 5)));

            bankAccountRepository.save(ba);
        }
        return rows.size();
    }

    private int importMerchantMappings(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            MerchantMapping mm = new MerchantMapping();
            mm.setMerchantPattern(getField(row, 0));
            mm.setMatchType(MerchantMapping.MatchType.valueOf(getField(row, 1)));

            String templateName = getField(row, 2);
            if (!templateName.isEmpty()) {
                mm.setTemplate(templateMap.get(templateName));
            }
            mm.setDefaultDescription(getField(row, 3));
            mm.setMatchCount(parseInteger(getField(row, 4)));
            mm.setLastUsedAt(parseDateTime(getField(row, 5)));

            merchantMappingRepository.save(mm);
        }
        return rows.size();
    }

    private int importEmployees(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            Employee e = new Employee();
            e.setEmployeeId(getField(row, 0));
            e.setName(getField(row, 1));
            e.setEmail(getField(row, 2));
            e.setNikKtp(getField(row, 3));
            e.setNpwp(getField(row, 4));
            e.setPtkpStatus(PtkpStatus.valueOf(getField(row, 5)));
            e.setJobTitle(getField(row, 6));
            e.setDepartment(getField(row, 7));
            e.setEmploymentType(EmploymentType.valueOf(getField(row, 8)));
            e.setHireDate(parseDate(getField(row, 9)));
            e.setResignDate(parseDate(getField(row, 10)));
            e.setBankName(getField(row, 11));
            e.setBankAccountNumber(getField(row, 12));
            e.setBpjsKesehatanNumber(getField(row, 13));
            e.setBpjsKetenagakerjaanNumber(getField(row, 14));
            e.setEmploymentStatus(EmploymentStatus.valueOf(getField(row, 15)));

            String username = getField(row, 16);
            if (!username.isEmpty()) {
                e.setUser(userMap.get(username));
            }

            employeeRepository.save(e);
            employeeMap.put(e.getEmployeeId(), e);
        }
        return rows.size();
    }

    private int importEmployeeSalaryComponents(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            String employeeId = getField(row, 0);
            String componentCode = getField(row, 1);

            Employee emp = employeeMap.get(employeeId);
            SalaryComponent comp = salaryComponentMap.get(componentCode);
            if (emp == null || comp == null) continue;

            EmployeeSalaryComponent esc = new EmployeeSalaryComponent();
            esc.setEmployee(emp);
            esc.setSalaryComponent(comp);
            esc.setRate(parseBigDecimal(getField(row, 2)));
            esc.setAmount(parseBigDecimal(getField(row, 3)));
            esc.setEffectiveDate(parseDate(getField(row, 4)));
            esc.setEndDate(parseDate(getField(row, 5)));

            employeeSalaryComponentRepository.save(esc);
        }
        return rows.size();
    }

    private int importInvoices(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: invoice_number,invoice_date,due_date,client_code,project_code,status,amount,notes,created_at
        for (String[] row : rows) {
            Invoice inv = new Invoice();
            inv.setInvoiceNumber(getField(row, 0));
            inv.setInvoiceDate(parseDate(getField(row, 1)));
            inv.setDueDate(parseDate(getField(row, 2)));

            String clientCode = getField(row, 3);
            if (!clientCode.isEmpty()) {
                inv.setClient(clientMap.get(clientCode));
            }
            String projectCode = getField(row, 4);
            if (!projectCode.isEmpty()) {
                inv.setProject(projectMap.get(projectCode));
            }
            String statusStr = getField(row, 5);
            if (!statusStr.isEmpty()) {
                inv.setStatus(InvoiceStatus.valueOf(statusStr));
            }
            inv.setAmount(parseBigDecimal(getField(row, 6)));
            inv.setNotes(getField(row, 7));
            // column 8 = created_at (ignored, auto-generated)

            invoiceRepository.save(inv);
        }
        return rows.size();
    }

    private int importTransactions(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: transaction_id,transaction_number,transaction_date,template_name,project_code,amount,description,
        //   reference_number,notes,status,void_reason,void_notes,voided_at,voided_by,posted_at,posted_by,created_at
        for (String[] row : rows) {
            Transaction t = new Transaction();
            // Column 0 is transaction_id (UUID) - used for linking, not set on entity (auto-generated)
            String txId = getField(row, 0);
            String txNumber = getField(row, 1);
            if (!txNumber.isEmpty()) {
                t.setTransactionNumber(txNumber);
            }
            t.setTransactionDate(parseDate(getField(row, 2)));

            String templateName = getField(row, 3);
            if (!templateName.isEmpty()) {
                t.setJournalTemplate(templateMap.get(templateName));
            }
            String projectCode = getField(row, 4);
            if (!projectCode.isEmpty()) {
                t.setProject(projectMap.get(projectCode));
            }
            t.setAmount(parseBigDecimal(getField(row, 5)));
            t.setDescription(getField(row, 6));
            t.setReferenceNumber(getField(row, 7));
            t.setNotes(getField(row, 8));
            t.setStatus(TransactionStatus.valueOf(getField(row, 9)));

            String voidReason = getField(row, 10);
            if (!voidReason.isEmpty()) {
                t.setVoidReason(VoidReason.valueOf(voidReason));
            }
            t.setVoidNotes(getField(row, 11));
            t.setVoidedAt(parseDateTime(getField(row, 12)));
            t.setVoidedBy(getField(row, 13));
            t.setPostedAt(parseDateTime(getField(row, 14)));
            t.setPostedBy(getField(row, 15));
            // column 16 = created_at (ignored, auto-generated)

            transactionRepository.save(t);
            // Map by original UUID from export for linking related records
            transactionMap.put(UUID.fromString(txId), t);
        }
        return rows.size();
    }

    private int importTransactionAccountMappings(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: transaction_id,transaction_number,template_name,line_order,account_code,amount
        // Need to get template lines by template + line_order
        Map<String, JournalTemplateLine> lineMap = new HashMap<>();
        for (JournalTemplateLine line : templateLineRepository.findAll()) {
            String key = line.getJournalTemplate().getTemplateName() + "_" + line.getLineOrder();
            lineMap.put(key, line);
        }

        for (String[] row : rows) {
            String txId = getField(row, 0);
            Transaction tx = transactionMap.get(UUID.fromString(txId));
            if (tx == null) continue;

            String templateName = getField(row, 2);
            Integer lineOrder = parseInteger(getField(row, 3));
            String lineKey = templateName + "_" + lineOrder;
            JournalTemplateLine line = lineMap.get(lineKey);

            String accountCode = getField(row, 4);
            ChartOfAccount account = accountMap.get(accountCode);

            if (line != null && account != null) {
                TransactionAccountMapping tam = new TransactionAccountMapping();
                tam.setTransaction(tx);
                tam.setTemplateLine(line);
                tam.setAccount(account);
                tam.setAmount(parseBigDecimal(getField(row, 5)));
                entityManager.persist(tam);
            }
        }
        entityManager.flush();
        return rows.size();
    }

    private int importTransactionVariables(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: transaction_id,transaction_number,variable_name,variable_value

        for (String[] row : rows) {
            String txId = getField(row, 0);
            Transaction tx = transactionMap.get(UUID.fromString(txId));
            if (tx == null) continue;

            TransactionVariable tv = new TransactionVariable();
            tv.setTransaction(tx);
            tv.setVariableName(getField(row, 2));
            tv.setVariableValue(parseBigDecimal(getField(row, 3)));
            entityManager.persist(tv);
        }
        entityManager.flush();
        return rows.size();
    }

    private int importJournalEntries(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: journal_number,journal_date,transaction_id,transaction_number,description,status,
        //   account_code,debit_amount,credit_amount,posted_at,voided_at,void_reason

        for (String[] row : rows) {
            String txId = getField(row, 2);
            Transaction transaction = transactionMap.get(UUID.fromString(txId));
            if (transaction == null) {
                log.warn("Transaction not found for journal entry, txId: {}", txId);
                continue;
            }

            JournalEntry je = new JournalEntry();
            je.setJournalNumber(getField(row, 0));
            je.setTransaction(transaction);

            String accountCode = getField(row, 6);
            if (!accountCode.isEmpty()) {
                je.setAccount(accountMap.get(accountCode));
            }
            je.setDebitAmount(parseBigDecimal(getField(row, 7)));
            je.setCreditAmount(parseBigDecimal(getField(row, 8)));
            je.setPostedAt(parseDateTime(getField(row, 9)));
            je.setVoidedAt(parseDateTime(getField(row, 10)));
            je.setVoidReason(getField(row, 11));

            journalEntryRepository.save(je);
        }
        return rows.size();
    }

    private int importPayrollRuns(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: payroll_period,period_start,period_end,status,total_gross,total_deductions,total_net_pay,
        //   total_company_bpjs,total_pph21,employee_count,notes,posted_at,cancelled_at,cancel_reason,created_at
        for (String[] row : rows) {
            PayrollRun pr = new PayrollRun();
            pr.setPayrollPeriod(getField(row, 0));
            pr.setPeriodStart(parseDate(getField(row, 1)));
            pr.setPeriodEnd(parseDate(getField(row, 2)));
            pr.setStatus(PayrollStatus.valueOf(getField(row, 3)));
            pr.setTotalGross(parseBigDecimal(getField(row, 4)));
            pr.setTotalDeductions(parseBigDecimal(getField(row, 5)));
            pr.setTotalNetPay(parseBigDecimal(getField(row, 6)));
            pr.setTotalCompanyBpjs(parseBigDecimal(getField(row, 7)));
            pr.setTotalPph21(parseBigDecimal(getField(row, 8)));
            pr.setEmployeeCount(parseInteger(getField(row, 9)));
            pr.setNotes(getField(row, 10));
            pr.setPostedAt(parseDateTime(getField(row, 11)));
            pr.setCancelledAt(parseDateTime(getField(row, 12)));
            pr.setCancelReason(getField(row, 13));
            // column 14 = created_at (ignored, auto-generated)

            payrollRunRepository.save(pr);
            payrollRunMap.put(pr.getPayrollPeriod(), pr);
        }
        return rows.size();
    }

    private int importPayrollDetails(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            String period = getField(row, 0);
            PayrollRun run = payrollRunMap.get(period);
            String employeeId = getField(row, 1);
            Employee emp = employeeMap.get(employeeId);
            if (run == null || emp == null) continue;

            PayrollDetail pd = new PayrollDetail();
            pd.setPayrollRun(run);
            pd.setEmployee(emp);
            pd.setGrossSalary(parseBigDecimal(getField(row, 2)));
            pd.setTotalDeductions(parseBigDecimal(getField(row, 3)));
            pd.setNetPay(parseBigDecimal(getField(row, 4)));
            pd.setBpjsKesEmployee(parseBigDecimal(getField(row, 5)));
            pd.setBpjsKesCompany(parseBigDecimal(getField(row, 6)));
            pd.setBpjsJhtEmployee(parseBigDecimal(getField(row, 7)));
            pd.setBpjsJhtCompany(parseBigDecimal(getField(row, 8)));
            pd.setBpjsJpEmployee(parseBigDecimal(getField(row, 9)));
            pd.setBpjsJpCompany(parseBigDecimal(getField(row, 10)));
            pd.setBpjsJkk(parseBigDecimal(getField(row, 11)));
            pd.setBpjsJkm(parseBigDecimal(getField(row, 12)));
            pd.setPph21(parseBigDecimal(getField(row, 13)));

            payrollDetailRepository.save(pd);
        }
        return rows.size();
    }

    private int importAmortizationSchedules(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            AmortizationSchedule as = new AmortizationSchedule();
            as.setCode(getField(row, 0));
            as.setName(getField(row, 1));
            as.setScheduleType(ScheduleType.valueOf(getField(row, 2)));

            String sourceCode = getField(row, 3);
            if (!sourceCode.isEmpty()) {
                as.setSourceAccount(accountMap.get(sourceCode));
            }
            String targetCode = getField(row, 4);
            if (!targetCode.isEmpty()) {
                as.setTargetAccount(accountMap.get(targetCode));
            }
            as.setTotalAmount(parseBigDecimal(getField(row, 5)));
            as.setTotalPeriods(parseInteger(getField(row, 6)));
            as.setPeriodAmount(parseBigDecimal(getField(row, 7)));
            as.setStartDate(parseDate(getField(row, 8)));
            as.setStatus(ScheduleStatus.valueOf(getField(row, 9)));
            as.setAutoPost(parseBoolean(getField(row, 10)));
            as.setCompletedPeriods(parseInteger(getField(row, 11)));
            as.setAmortizedAmount(parseBigDecimal(getField(row, 12)));

            amortizationScheduleRepository.save(as);
            amortizationScheduleMap.put(as.getCode(), as);
        }
        return rows.size();
    }

    private int importAmortizationEntries(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            String scheduleCode = getField(row, 0);
            AmortizationSchedule schedule = amortizationScheduleMap.get(scheduleCode);
            if (schedule == null) continue;

            AmortizationEntry ae = new AmortizationEntry();
            ae.setSchedule(schedule);
            ae.setPeriodNumber(parseInteger(getField(row, 1)));
            ae.setPeriodStart(parseDate(getField(row, 2)));
            ae.setPeriodEnd(parseDate(getField(row, 3)));
            ae.setAmount(parseBigDecimal(getField(row, 4)));
            ae.setStatus(AmortizationEntryStatus.valueOf(getField(row, 5)));
            ae.setJournalNumber(getField(row, 6));
            ae.setPostedAt(parseDateTime(getField(row, 7)));

            amortizationEntryRepository.save(ae);
        }
        return rows.size();
    }

    private int importTaxTransactionDetails(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: transaction_id,transaction_number,tax_type,counterparty_name,counterparty_npwp,counterparty_nik,
        //   counterparty_nitku,tax_object_code,dpp,tax_amount,faktur_number,faktur_date

        for (String[] row : rows) {
            String txId = getField(row, 0);
            Transaction tx = transactionMap.get(UUID.fromString(txId));
            if (tx == null) continue;

            TaxTransactionDetail ttd = new TaxTransactionDetail();
            ttd.setTransaction(tx);
            ttd.setTaxType(TaxType.valueOf(getField(row, 2)));
            ttd.setCounterpartyName(getField(row, 3));
            ttd.setCounterpartyNpwp(getField(row, 4));
            ttd.setCounterpartyNik(getField(row, 5));
            ttd.setCounterpartyNitku(getField(row, 6));
            ttd.setTaxObjectCode(getField(row, 7));
            ttd.setDpp(parseBigDecimal(getField(row, 8)));
            ttd.setTaxAmount(parseBigDecimal(getField(row, 9)));
            ttd.setFakturNumber(getField(row, 10));
            ttd.setFakturDate(parseDate(getField(row, 11)));

            taxTransactionDetailRepository.save(ttd);
        }
        return rows.size();
    }

    private int importTaxDeadlineCompletions(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            TaxDeadlineType type = TaxDeadlineType.valueOf(getField(row, 0));
            TaxDeadline deadline = taxDeadlineMap.get(type);
            if (deadline == null) continue;

            TaxDeadlineCompletion tdc = new TaxDeadlineCompletion();
            tdc.setTaxDeadline(deadline);
            tdc.setYear(parseInteger(getField(row, 1)));
            tdc.setMonth(parseInteger(getField(row, 2)));
            tdc.setCompletedDate(parseDate(getField(row, 3)));
            tdc.setCompletedBy(getField(row, 4));
            tdc.setReferenceNumber(getField(row, 5));
            tdc.setNotes(getField(row, 6));

            taxDeadlineCompletionRepository.save(tdc);
        }
        return rows.size();
    }

    private int importDraftTransactions(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            DraftTransaction dt = new DraftTransaction();
            dt.setSource(DraftTransaction.Source.valueOf(getField(row, 0)));
            dt.setStatus(DraftTransaction.Status.valueOf(getField(row, 1)));
            dt.setMerchantName(getField(row, 2));
            dt.setTransactionDate(parseDate(getField(row, 3)));
            dt.setAmount(parseBigDecimal(getField(row, 4)));

            String templateName = getField(row, 5);
            if (!templateName.isEmpty()) {
                dt.setSuggestedTemplate(templateMap.get(templateName));
            }
            dt.setMerchantConfidence(parseBigDecimal(getField(row, 6)));
            dt.setDateConfidence(parseBigDecimal(getField(row, 7)));
            dt.setAmountConfidence(parseBigDecimal(getField(row, 8)));
            dt.setRawOcrText(getField(row, 9));
            dt.setProcessedAt(parseDateTime(getField(row, 10)));
            dt.setProcessedBy(getField(row, 11));
            dt.setRejectionReason(getField(row, 12));

            draftTransactionRepository.save(dt);
        }
        return rows.size();
    }

    private int importUsers(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: username,full_name,email,active,created_at
        // Note: password is NOT exported for security reasons
        // Users will need to reset their password after import
        for (String[] row : rows) {
            User u = new User();
            u.setUsername(getField(row, 0));
            // Generate a random password that requires reset
            // Uses a secure random UUID as temporary password (user must reset)
            String tempPassword = UUID.randomUUID().toString();
            u.setPassword(passwordEncoder.encode(tempPassword));
            u.setFullName(getField(row, 1));
            u.setEmail(getField(row, 2));
            u.setActive(parseBoolean(getField(row, 3)));
            // column 4 = created_at (ignored, auto-generated)

            userRepository.save(u);
            userMap.put(u.getUsername(), u);
            log.info("Imported user '{}' - password reset required", LogSanitizer.username(u.getUsername()));
        }
        return rows.size();
    }

    private int importUserRoles(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: username,role,created_by,created_at
        for (String[] row : rows) {
            String username = getField(row, 0);
            User user = userMap.get(username);
            if (user == null) continue;

            Role role = Role.valueOf(getField(row, 1));
            String createdBy = getField(row, 2);
            // column 3 = created_at (ignored, auto-generated)
            user.addRole(role, createdBy);
        }
        userRepository.saveAll(userMap.values());
        return rows.size();
    }

    private int importUserTemplatePreferences(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            String username = getField(row, 0);
            String templateName = getField(row, 1);
            User user = userMap.get(username);
            JournalTemplate template = templateMap.get(templateName);
            if (user == null || template == null) continue;

            UserTemplatePreference utp = new UserTemplatePreference();
            utp.setUser(user);
            utp.setJournalTemplate(template);
            utp.setIsFavorite(parseBoolean(getField(row, 2)));
            utp.setUseCount(parseInteger(getField(row, 3)));
            utp.setLastUsedAt(parseDateTime(getField(row, 4)));

            userTemplatePreferenceRepository.save(utp);
        }
        return rows.size();
    }

    private int importTelegramUserLinks(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            TelegramUserLink tul = new TelegramUserLink();
            tul.setTelegramUserId(parseLong(getField(row, 0)));
            tul.setTelegramUsername(getField(row, 1));

            String username = getField(row, 2);
            if (!username.isEmpty()) {
                tul.setUser(userMap.get(username));
            }
            tul.setIsActive(parseBoolean(getField(row, 3)));
            tul.setLinkedAt(parseDateTime(getField(row, 4)));

            telegramUserLinkRepository.save(tul);
        }
        return rows.size();
    }

    private int importAuditLogs(String content) {
        List<String[]> rows = parseCsv(content);

        for (String[] row : rows) {
            AuditLog auditLog = new AuditLog();
            String username = getField(row, 1);
            if (!username.isEmpty()) {
                auditLog.setUser(userMap.get(username));
            }
            auditLog.setAction(getField(row, 2));
            auditLog.setEntityType(getField(row, 3));
            String entityId = getField(row, 4);
            if (!entityId.isEmpty()) {
                auditLog.setEntityId(UUID.fromString(entityId));
            }
            auditLog.setIpAddress(getField(row, 5));

            auditLogRepository.save(auditLog);
        }
        return rows.size();
    }

    private int importTransactionSequences(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: sequence_type,prefix,year,last_number
        for (String[] row : rows) {
            TransactionSequence ts = new TransactionSequence();
            ts.setSequenceType(getField(row, 0));
            ts.setPrefix(getField(row, 1));
            ts.setYear(parseInteger(getField(row, 2)));
            ts.setLastNumber(parseInteger(getField(row, 3)));

            transactionSequenceRepository.save(ts);
        }
        return rows.size();
    }

    private int importAssetCategories(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: code,name,description,depreciation_method,useful_life_months,depreciation_rate,
        //              asset_account_code,accumulated_depreciation_account_code,depreciation_expense_account_code,active
        for (String[] row : rows) {
            AssetCategory ac = new AssetCategory();
            ac.setCode(getField(row, 0));
            ac.setName(getField(row, 1));
            ac.setDescription(getField(row, 2));

            String method = getField(row, 3);
            if (!method.isEmpty()) {
                ac.setDepreciationMethod(DepreciationMethod.valueOf(method));
            }

            String usefulLife = getField(row, 4);
            if (!usefulLife.isEmpty()) {
                ac.setUsefulLifeMonths(parseInteger(usefulLife));
            }

            String rate = getField(row, 5);
            if (!rate.isEmpty()) {
                ac.setDepreciationRate(new BigDecimal(rate));
            }

            String assetAccountCode = getField(row, 6);
            if (!assetAccountCode.isEmpty()) {
                ac.setAssetAccount(accountMap.get(assetAccountCode));
            }

            String accumAccountCode = getField(row, 7);
            if (!accumAccountCode.isEmpty()) {
                ac.setAccumulatedDepreciationAccount(accountMap.get(accumAccountCode));
            }

            String expenseAccountCode = getField(row, 8);
            if (!expenseAccountCode.isEmpty()) {
                ac.setDepreciationExpenseAccount(accountMap.get(expenseAccountCode));
            }

            ac.setActive(parseBoolean(getField(row, 9)));

            assetCategoryRepository.save(ac);
        }
        return rows.size();
    }

    private int importProductCategories(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: code,name,description,parent_code,active
        for (String[] row : rows) {
            ProductCategory pc = new ProductCategory();
            pc.setCode(getField(row, 0));
            pc.setName(getField(row, 1));
            pc.setDescription(getField(row, 2));

            String parentCode = getField(row, 3);
            if (!parentCode.isEmpty() && productCategoryMap != null) {
                pc.setParent(productCategoryMap.get(parentCode));
            }

            pc.setActive(parseBoolean(getField(row, 4)));

            ProductCategory saved = productCategoryRepository.save(pc);

            // Build map for parent references
            if (productCategoryMap == null) {
                productCategoryMap = new HashMap<>();
            }
            productCategoryMap.put(saved.getCode(), saved);
        }
        return rows.size();
    }

    private int importProducts(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: code,name,description,unit,category_code,costing_method,track_inventory,minimum_stock,
        //              selling_price,inventory_account_code,cogs_account_code,sales_account_code,active
        for (String[] row : rows) {
            Product p = new Product();
            p.setCode(getField(row, 0));
            p.setName(getField(row, 1));
            p.setDescription(getField(row, 2));
            p.setUnit(getField(row, 3));
            p.setTrackInventory(parseBoolean(getField(row, 6)));
            p.setActive(parseBoolean(getField(row, 12)));

            setFromMap(row, 4, productCategoryMap, p::setCategory);
            setEnumField(row, 5, CostingMethod.class, p::setCostingMethod);
            setBigDecimalField(row, 7, p::setMinimumStock);
            setBigDecimalField(row, 8, p::setSellingPrice);
            setFromMap(row, 9, accountMap, p::setInventoryAccount);
            setFromMap(row, 10, accountMap, p::setCogsAccount);
            setFromMap(row, 11, accountMap, p::setSalesAccount);

            Product saved = productRepository.save(p);

            // Build map for BOM references
            if (productMap == null) {
                productMap = new HashMap<>();
            }
            productMap.put(saved.getCode(), saved);
        }
        return rows.size();
    }

    private int importBillOfMaterials(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: code,name,description,product_code,output_quantity,active
        for (String[] row : rows) {
            BillOfMaterial bom = new BillOfMaterial();
            bom.setCode(getField(row, 0));
            bom.setName(getField(row, 1));
            bom.setDescription(getField(row, 2));

            String productCode = getField(row, 3);
            if (!productCode.isEmpty() && productMap != null) {
                bom.setProduct(productMap.get(productCode));
            }

            String outputQty = getField(row, 4);
            if (!outputQty.isEmpty()) {
                bom.setOutputQuantity(new BigDecimal(outputQty));
            }

            bom.setActive(parseBoolean(getField(row, 5)));

            BillOfMaterial saved = billOfMaterialRepository.save(bom);

            // Build map for production order references
            if (billOfMaterialMap == null) {
                billOfMaterialMap = new HashMap<>();
            }
            billOfMaterialMap.put(saved.getCode(), saved);
        }
        return rows.size();
    }

    private int importBomLines(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: bom_code,component_product_code,quantity,line_order
        for (String[] row : rows) {
            String bomCode = getField(row, 0);
            BillOfMaterial bom = billOfMaterialMap != null ? billOfMaterialMap.get(bomCode) : null;

            if (bom != null) {
                BillOfMaterialLine line = new BillOfMaterialLine();
                line.setBillOfMaterial(bom);

                String componentCode = getField(row, 1);
                if (!componentCode.isEmpty() && productMap != null) {
                    line.setComponent(productMap.get(componentCode));
                }

                String qty = getField(row, 2);
                if (!qty.isEmpty()) {
                    line.setQuantity(new BigDecimal(qty));
                }

                String lineOrder = getField(row, 3);
                if (!lineOrder.isEmpty()) {
                    line.setLineOrder(parseInteger(lineOrder));
                }

                bom.addLine(line);
            }
        }
        // Lines are saved via cascade from BOM
        return rows.size();
    }

    private int importProductionOrders(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: order_number,bom_code,quantity,order_date,planned_completion_date,actual_completion_date,
        //              status,total_cost,unit_cost,notes
        for (String[] row : rows) {
            ProductionOrder po = new ProductionOrder();
            po.setOrderNumber(getField(row, 0));
            po.setNotes(getField(row, 9));

            setFromMap(row, 1, billOfMaterialMap, po::setBillOfMaterial);
            setBigDecimalField(row, 2, po::setQuantity);
            setDateField(row, 3, po::setOrderDate);
            setDateField(row, 4, po::setPlannedCompletionDate);
            setDateField(row, 5, po::setActualCompletionDate);
            setEnumField(row, 6, ProductionOrderStatus.class, po::setStatus);
            setBigDecimalField(row, 7, po::setTotalComponentCost);
            setBigDecimalField(row, 8, po::setUnitCost);

            productionOrderRepository.save(po);
        }
        return rows.size();
    }

    private int importInventoryTransactions(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: product_code,transaction_date,transaction_type,quantity,unit_cost,total_cost,
        //              balance_after,total_cost_after,unit_price,reference_number,notes
        for (String[] row : rows) {
            InventoryTransaction it = new InventoryTransaction();
            it.setReferenceNumber(getField(row, 9));
            it.setNotes(getField(row, 10));

            setFromMap(row, 0, productMap, it::setProduct);
            setDateField(row, 1, it::setTransactionDate);
            setEnumField(row, 2, InventoryTransactionType.class, it::setTransactionType);
            setBigDecimalField(row, 3, it::setQuantity);
            setBigDecimalField(row, 4, it::setUnitCost);
            setBigDecimalField(row, 5, it::setTotalCost);
            setBigDecimalField(row, 6, it::setBalanceAfter);
            setBigDecimalField(row, 7, it::setTotalCostAfter);
            setBigDecimalField(row, 8, it::setUnitPrice);

            inventoryTransactionRepository.save(it);
        }
        return rows.size();
    }

    private int importInventoryBalances(String content) {
        List<String[]> rows = parseCsv(content);
        // CSV columns: product_code,quantity,total_cost,average_cost
        for (String[] row : rows) {
            InventoryBalance ib = new InventoryBalance();

            String productCode = getField(row, 0);
            if (!productCode.isEmpty() && productMap != null) {
                ib.setProduct(productMap.get(productCode));
            }

            String qty = getField(row, 1);
            if (!qty.isEmpty()) {
                ib.setQuantity(new BigDecimal(qty));
            }

            String totalCost = getField(row, 2);
            if (!totalCost.isEmpty()) {
                ib.setTotalCost(new BigDecimal(totalCost));
            }

            String avgCost = getField(row, 3);
            if (!avgCost.isEmpty()) {
                ib.setAverageCost(new BigDecimal(avgCost));
            }

            inventoryBalanceRepository.save(ib);
        }
        return rows.size();
    }

    private int importDocumentFiles(Map<String, byte[]> documentFiles) throws IOException {
        int count = 0;
        Path rootLocation = documentStorageService.getRootLocation();

        if (rootLocation == null) {
            throw new IllegalStateException("Document storage not initialized");
        }

        for (Map.Entry<String, byte[]> entry : documentFiles.entrySet()) {
            String key = entry.getKey();
            byte[] content = entry.getValue();

            String storagePath;
            if (key.startsWith("company_logo:")) {
                // Company logo file - strip the prefix
                storagePath = key.substring("company_logo:".length());
                log.info("Importing company logo: {}", LogSanitizer.filename(storagePath));
            } else {
                // Regular document file
                storagePath = key;
            }

            Path targetPath = rootLocation.resolve(storagePath);

            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(targetPath, content);
            count++;
        }
        return count;
    }

    // Result record
    public record ImportResult(int totalRecords, int documentCount, long durationMs) {}
}
