package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.*;
import com.artivisi.accountingfinance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for exporting all company data to a ZIP archive.
 * Used for regulatory compliance and data portability.
 *
 * Export files are numbered (01_, 02_, etc.) to define import order.
 * References use natural keys (codes) instead of UUIDs for portability.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DataExportService {

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
    private final DocumentStorageService documentStorageService;

    // Additional repositories for full export
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

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Export all company data to a ZIP archive.
     * Files are numbered to define import order.
     */
    public byte[] exportAllData() throws IOException {
        log.info("Starting full data export");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Export metadata
            addManifest(zos);

            // Export in dependency order (numbered for import sequence)
            // 01-06: Configuration and master data (no dependencies)
            addTextEntry(zos, "01_company_config.csv", exportCompanyConfig());
            addTextEntry(zos, "02_chart_of_accounts.csv", exportChartOfAccounts());
            addTextEntry(zos, "03_salary_components.csv", exportSalaryComponents());
            addTextEntry(zos, "04_journal_templates.csv", exportJournalTemplates());
            addTextEntry(zos, "05_journal_template_lines.csv", exportJournalTemplateLines());
            addTextEntry(zos, "06_journal_template_tags.csv", exportJournalTemplateTags());

            // 07-10: Reference data (depends on master data)
            addTextEntry(zos, "07_clients.csv", exportClients());
            addTextEntry(zos, "08_projects.csv", exportProjects());
            addTextEntry(zos, "09_project_milestones.csv", exportProjectMilestones());
            addTextEntry(zos, "10_project_payment_terms.csv", exportProjectPaymentTerms());

            // 11-14: System configuration
            addTextEntry(zos, "11_fiscal_periods.csv", exportFiscalPeriods());
            addTextEntry(zos, "12_tax_deadlines.csv", exportTaxDeadlines());
            addTextEntry(zos, "13_company_bank_accounts.csv", exportCompanyBankAccounts());
            addTextEntry(zos, "14_merchant_mappings.csv", exportMerchantMappings());

            // 15-16: Employee data
            addTextEntry(zos, "15_employees.csv", exportEmployees());
            addTextEntry(zos, "16_employee_salary_components.csv", exportEmployeeSalaryComponents());

            // 17-20: Transactional data
            addTextEntry(zos, "17_invoices.csv", exportInvoices());
            addTextEntry(zos, "18_transactions.csv", exportTransactions());
            addTextEntry(zos, "19_transaction_account_mappings.csv", exportTransactionAccountMappings());
            addTextEntry(zos, "20_journal_entries.csv", exportJournalEntries());

            // 21-24: Payroll and amortization
            addTextEntry(zos, "21_payroll_runs.csv", exportPayrollRuns());
            addTextEntry(zos, "22_payroll_details.csv", exportPayrollDetails());
            addTextEntry(zos, "23_amortization_schedules.csv", exportAmortizationSchedules());
            addTextEntry(zos, "24_amortization_entries.csv", exportAmortizationEntries());

            // 25-27: Tax and draft data
            addTextEntry(zos, "25_tax_transaction_details.csv", exportTaxTransactionDetails());
            addTextEntry(zos, "26_tax_deadline_completions.csv", exportTaxDeadlineCompletions());
            addTextEntry(zos, "27_draft_transactions.csv", exportDraftTransactions());

            // 28-31: User data
            addTextEntry(zos, "28_users.csv", exportUsers());
            addTextEntry(zos, "29_user_roles.csv", exportUserRoles());
            addTextEntry(zos, "30_user_template_preferences.csv", exportUserTemplatePreferences());
            addTextEntry(zos, "31_telegram_user_links.csv", exportTelegramUserLinks());

            // 32-33: System state
            addTextEntry(zos, "32_audit_logs.csv", exportAuditLogs());
            addTextEntry(zos, "33_transaction_sequences.csv", exportTransactionSequences());

            // 34: Asset categories (depends on COA)
            addTextEntry(zos, "34_asset_categories.csv", exportAssetCategories());

            // Export documents (files + index)
            exportDocuments(zos);
        }

        log.info("Full data export completed, size: {} bytes", baos.size());
        return baos.toByteArray();
    }

    /**
     * Get export statistics without generating the actual export.
     */
    public ExportStatistics getExportStatistics() {
        return new ExportStatistics(
                accountRepository.count(),
                journalEntryRepository.count(),
                transactionRepository.count(),
                clientRepository.count(),
                projectRepository.count(),
                invoiceRepository.count(),
                employeeRepository.count(),
                payrollRunRepository.count(),
                documentRepository.count(),
                auditLogRepository.count(),
                templateRepository.count(),
                userRepository.count()
        );
    }

    private void addManifest(ZipOutputStream zos) throws IOException {
        CompanyConfig config = companyConfigRepository.findFirst().orElse(null);

        StringBuilder manifest = new StringBuilder();
        manifest.append("# Data Export Manifest\n\n");
        manifest.append("Export Date: ").append(LocalDateTime.now().format(DATETIME_FORMATTER)).append("\n");
        manifest.append("Application: Aplikasi Akunting\n");
        manifest.append("Format Version: 2.0\n\n");

        if (config != null) {
            manifest.append("## Company Information\n");
            manifest.append("Name: ").append(config.getCompanyName()).append("\n");
            manifest.append("NPWP: ").append(config.getNpwp() != null ? config.getNpwp() : "-").append("\n");
            manifest.append("\n");
        }

        ExportStatistics stats = getExportStatistics();
        manifest.append("## Export Contents\n");
        manifest.append("- Chart of Accounts: ").append(stats.accountCount()).append(" records\n");
        manifest.append("- Journal Templates: ").append(stats.templateCount()).append(" records\n");
        manifest.append("- Journal Entries: ").append(stats.journalEntryCount()).append(" records\n");
        manifest.append("- Transactions: ").append(stats.transactionCount()).append(" records\n");
        manifest.append("- Clients: ").append(stats.clientCount()).append(" records\n");
        manifest.append("- Projects: ").append(stats.projectCount()).append(" records\n");
        manifest.append("- Invoices: ").append(stats.invoiceCount()).append(" records\n");
        manifest.append("- Employees: ").append(stats.employeeCount()).append(" records\n");
        manifest.append("- Payroll Runs: ").append(stats.payrollRunCount()).append(" records\n");
        manifest.append("- Users: ").append(stats.userCount()).append(" records\n");
        manifest.append("- Documents: ").append(stats.documentCount()).append(" files\n");
        manifest.append("- Audit Logs: ").append(stats.auditLogCount()).append(" records\n");

        addTextEntry(zos, "MANIFEST.md", manifest.toString());
    }

    // ============================================
    // 01: Company Config
    // ============================================
    private String exportCompanyConfig() {
        StringBuilder csv = new StringBuilder();
        csv.append("company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,");
        csv.append("fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title\n");

        companyConfigRepository.findFirst().ifPresent(c -> {
            csv.append(escapeCsv(c.getCompanyName())).append(",");
            csv.append(escapeCsv(c.getCompanyAddress())).append(",");
            csv.append(escapeCsv(c.getCompanyPhone())).append(",");
            csv.append(escapeCsv(c.getCompanyEmail())).append(",");
            csv.append(escapeCsv(c.getTaxId())).append(",");
            csv.append(escapeCsv(c.getNpwp())).append(",");
            csv.append(escapeCsv(c.getNitku())).append(",");
            csv.append(c.getFiscalYearStartMonth()).append(",");
            csv.append(escapeCsv(c.getCurrencyCode())).append(",");
            csv.append(escapeCsv(c.getSigningOfficerName())).append(",");
            csv.append(escapeCsv(c.getSigningOfficerTitle())).append("\n");
        });
        return csv.toString();
    }

    // ============================================
    // 02: Chart of Accounts
    // ============================================
    private String exportChartOfAccounts() {
        StringBuilder csv = new StringBuilder();
        csv.append("account_code,account_name,account_type,parent_code,normal_balance,active,created_at\n");

        List<ChartOfAccount> accounts = accountRepository.findAll(Sort.by("accountCode"));
        for (ChartOfAccount a : accounts) {
            csv.append(escapeCsv(a.getAccountCode())).append(",");
            csv.append(escapeCsv(a.getAccountName())).append(",");
            csv.append(a.getAccountType()).append(",");
            csv.append(a.getParent() != null ? escapeCsv(a.getParent().getAccountCode()) : "").append(",");
            csv.append(a.getNormalBalance()).append(",");
            csv.append(a.getActive()).append(",");
            csv.append(a.getCreatedAt() != null ? a.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 03: Salary Components
    // ============================================
    private String exportSalaryComponents() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,description,component_type,is_percentage,default_rate,default_amount,");
        csv.append("is_system,display_order,active,is_taxable,bpjs_category\n");

        List<SalaryComponent> components = salaryComponentRepository.findAll(Sort.by("displayOrder", "code"));
        for (SalaryComponent sc : components) {
            csv.append(escapeCsv(sc.getCode())).append(",");
            csv.append(escapeCsv(sc.getName())).append(",");
            csv.append(escapeCsv(sc.getDescription())).append(",");
            csv.append(sc.getComponentType()).append(",");
            csv.append(sc.getIsPercentage()).append(",");
            csv.append(sc.getDefaultRate() != null ? sc.getDefaultRate() : "").append(",");
            csv.append(sc.getDefaultAmount() != null ? sc.getDefaultAmount() : "").append(",");
            csv.append(sc.getIsSystem()).append(",");
            csv.append(sc.getDisplayOrder()).append(",");
            csv.append(sc.getActive()).append(",");
            csv.append(sc.getIsTaxable()).append(",");
            csv.append(escapeCsv(sc.getBpjsCategory())).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 04: Journal Templates
    // ============================================
    private String exportJournalTemplates() {
        StringBuilder csv = new StringBuilder();
        csv.append("template_name,category,cash_flow_category,template_type,description,");
        csv.append("is_favorite,is_system,active,version,usage_count,last_used_at\n");

        List<JournalTemplate> templates = templateRepository.findAll(Sort.by("templateName"));
        for (JournalTemplate t : templates) {
            csv.append(escapeCsv(t.getTemplateName())).append(",");
            csv.append(t.getCategory()).append(",");
            csv.append(t.getCashFlowCategory()).append(",");
            csv.append(t.getTemplateType()).append(",");
            csv.append(escapeCsv(t.getDescription())).append(",");
            csv.append(t.getIsFavorite()).append(",");
            csv.append(t.getIsSystem()).append(",");
            csv.append(t.getActive()).append(",");
            csv.append(t.getVersion()).append(",");
            csv.append(t.getUsageCount()).append(",");
            csv.append(t.getLastUsedAt() != null ? t.getLastUsedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 05: Journal Template Lines
    // ============================================
    private String exportJournalTemplateLines() {
        StringBuilder csv = new StringBuilder();
        csv.append("template_name,line_order,account_code,account_hint,position,formula,description\n");

        List<JournalTemplate> templates = templateRepository.findAll(Sort.by("templateName"));
        for (JournalTemplate t : templates) {
            for (JournalTemplateLine line : t.getLines()) {
                csv.append(escapeCsv(t.getTemplateName())).append(",");
                csv.append(line.getLineOrder()).append(",");
                csv.append(line.getAccount() != null ? escapeCsv(line.getAccount().getAccountCode()) : "").append(",");
                csv.append(escapeCsv(line.getAccountHint())).append(",");
                csv.append(line.getPosition()).append(",");
                csv.append(escapeCsv(line.getFormula())).append(",");
                csv.append(escapeCsv(line.getDescription())).append("\n");
            }
        }
        return csv.toString();
    }

    // ============================================
    // 06: Journal Template Tags
    // ============================================
    private String exportJournalTemplateTags() {
        StringBuilder csv = new StringBuilder();
        csv.append("template_name,tag\n");

        List<JournalTemplate> templates = templateRepository.findAll(Sort.by("templateName"));
        for (JournalTemplate t : templates) {
            for (JournalTemplateTag tag : t.getTags()) {
                csv.append(escapeCsv(t.getTemplateName())).append(",");
                csv.append(escapeCsv(tag.getTag())).append("\n");
            }
        }
        return csv.toString();
    }

    // ============================================
    // 07: Clients
    // ============================================
    private String exportClients() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at\n");

        List<Client> clients = clientRepository.findAll(Sort.by("code"));
        for (Client c : clients) {
            csv.append(escapeCsv(c.getCode())).append(",");
            csv.append(escapeCsv(c.getName())).append(",");
            csv.append(escapeCsv(c.getContactPerson())).append(",");
            csv.append(escapeCsv(c.getEmail())).append(",");
            csv.append(escapeCsv(c.getPhone())).append(",");
            csv.append(escapeCsv(c.getAddress())).append(",");
            csv.append(escapeCsv(c.getNpwp())).append(",");
            csv.append(escapeCsv(c.getNik())).append(",");
            csv.append(escapeCsv(c.getNitku())).append(",");
            csv.append(c.getActive()).append(",");
            csv.append(c.getCreatedAt() != null ? c.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 08: Projects
    // ============================================
    private String exportProjects() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,client_code,status,start_date,end_date,budget_amount,contract_value,description,created_at\n");

        List<Project> projects = projectRepository.findAll(Sort.by("code"));
        for (Project p : projects) {
            csv.append(escapeCsv(p.getCode())).append(",");
            csv.append(escapeCsv(p.getName())).append(",");
            csv.append(p.getClient() != null ? escapeCsv(p.getClient().getCode()) : "").append(",");
            csv.append(p.getStatus()).append(",");
            csv.append(p.getStartDate() != null ? p.getStartDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(p.getEndDate() != null ? p.getEndDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(p.getBudgetAmount() != null ? p.getBudgetAmount() : "").append(",");
            csv.append(p.getContractValue() != null ? p.getContractValue() : "").append(",");
            csv.append(escapeCsv(p.getDescription())).append(",");
            csv.append(p.getCreatedAt() != null ? p.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 09: Project Milestones
    // ============================================
    private String exportProjectMilestones() {
        StringBuilder csv = new StringBuilder();
        csv.append("project_code,sequence,name,description,status,weight_percent,target_date,actual_date\n");

        List<ProjectMilestone> milestones = milestoneRepository.findAll(Sort.by("project.code", "sequence"));
        for (ProjectMilestone m : milestones) {
            csv.append(escapeCsv(m.getProject().getCode())).append(",");
            csv.append(m.getSequence()).append(",");
            csv.append(escapeCsv(m.getName())).append(",");
            csv.append(escapeCsv(m.getDescription())).append(",");
            csv.append(m.getStatus()).append(",");
            csv.append(m.getWeightPercent() != null ? m.getWeightPercent() : "").append(",");
            csv.append(m.getTargetDate() != null ? m.getTargetDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(m.getActualDate() != null ? m.getActualDate().format(DATE_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 10: Project Payment Terms
    // ============================================
    private String exportProjectPaymentTerms() {
        StringBuilder csv = new StringBuilder();
        csv.append("project_code,sequence,milestone_sequence,template_name,name,");
        csv.append("is_percentage,percentage,amount,due_trigger,auto_post\n");

        List<ProjectPaymentTerm> terms = paymentTermRepository.findAll(Sort.by("project.code", "sequence"));
        for (ProjectPaymentTerm pt : terms) {
            csv.append(escapeCsv(pt.getProject().getCode())).append(",");
            csv.append(pt.getSequence()).append(",");
            csv.append(pt.getMilestone() != null ? pt.getMilestone().getSequence() : "").append(",");
            csv.append(pt.getTemplate() != null ? escapeCsv(pt.getTemplate().getTemplateName()) : "").append(",");
            csv.append(escapeCsv(pt.getName())).append(",");
            csv.append(pt.getPercentage() != null).append(",");
            csv.append(pt.getPercentage() != null ? pt.getPercentage() : "").append(",");
            csv.append(pt.getAmount() != null ? pt.getAmount() : "").append(",");
            csv.append(pt.getDueTrigger()).append(",");
            csv.append(pt.getAutoPost()).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 11: Fiscal Periods
    // ============================================
    private String exportFiscalPeriods() {
        StringBuilder csv = new StringBuilder();
        csv.append("year,month,status,month_closed_at,month_closed_by,tax_filed_at,tax_filed_by\n");

        List<FiscalPeriod> periods = fiscalPeriodRepository.findAll(Sort.by("year", "month"));
        for (FiscalPeriod fp : periods) {
            csv.append(fp.getYear()).append(",");
            csv.append(fp.getMonth()).append(",");
            csv.append(fp.getStatus()).append(",");
            csv.append(fp.getMonthClosedAt() != null ? fp.getMonthClosedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(fp.getMonthClosedBy())).append(",");
            csv.append(fp.getTaxFiledAt() != null ? fp.getTaxFiledAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(fp.getTaxFiledBy())).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 12: Tax Deadlines
    // ============================================
    private String exportTaxDeadlines() {
        StringBuilder csv = new StringBuilder();
        csv.append("deadline_type,name,description,due_day,use_last_day_of_month,reminder_days_before,active\n");

        List<TaxDeadline> deadlines = taxDeadlineRepository.findAll(Sort.by("deadlineType"));
        for (TaxDeadline td : deadlines) {
            csv.append(td.getDeadlineType()).append(",");
            csv.append(escapeCsv(td.getName())).append(",");
            csv.append(escapeCsv(td.getDescription())).append(",");
            csv.append(td.getDueDay()).append(",");
            csv.append(td.getUseLastDayOfMonth()).append(",");
            csv.append(td.getReminderDaysBefore()).append(",");
            csv.append(td.getActive()).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 13: Company Bank Accounts
    // ============================================
    private String exportCompanyBankAccounts() {
        StringBuilder csv = new StringBuilder();
        csv.append("bank_name,account_number,account_name,bank_branch,is_default,active\n");

        List<CompanyBankAccount> accounts = bankAccountRepository.findAll(Sort.by("bankName", "accountNumber"));
        for (CompanyBankAccount ba : accounts) {
            csv.append(escapeCsv(ba.getBankName())).append(",");
            csv.append(escapeCsv(ba.getAccountNumber())).append(",");
            csv.append(escapeCsv(ba.getAccountName())).append(",");
            csv.append(escapeCsv(ba.getBankBranch())).append(",");
            csv.append(ba.getIsDefault()).append(",");
            csv.append(ba.getActive()).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 14: Merchant Mappings
    // ============================================
    private String exportMerchantMappings() {
        StringBuilder csv = new StringBuilder();
        csv.append("merchant_pattern,match_type,template_name,default_description,match_count,last_used_at\n");

        List<MerchantMapping> mappings = merchantMappingRepository.findAll(Sort.by("merchantPattern"));
        for (MerchantMapping mm : mappings) {
            csv.append(escapeCsv(mm.getMerchantPattern())).append(",");
            csv.append(mm.getMatchType()).append(",");
            csv.append(mm.getTemplate() != null ? escapeCsv(mm.getTemplate().getTemplateName()) : "").append(",");
            csv.append(escapeCsv(mm.getDefaultDescription())).append(",");
            csv.append(mm.getMatchCount()).append(",");
            csv.append(mm.getLastUsedAt() != null ? mm.getLastUsedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 15: Employees
    // ============================================
    private String exportEmployees() {
        StringBuilder csv = new StringBuilder();
        csv.append("employee_id,name,email,nik_ktp,npwp,ptkp_status,job_title,department,");
        csv.append("employment_type,hire_date,resign_date,bank_name,bank_account,");
        csv.append("bpjs_kesehatan_number,bpjs_ketenagakerjaan_number,employment_status,username\n");

        List<Employee> employees = employeeRepository.findAll(Sort.by("employeeId"));
        for (Employee e : employees) {
            csv.append(escapeCsv(e.getEmployeeId())).append(",");
            csv.append(escapeCsv(e.getName())).append(",");
            csv.append(escapeCsv(e.getEmail())).append(",");
            csv.append(escapeCsv(e.getNikKtp())).append(",");
            csv.append(escapeCsv(e.getNpwp())).append(",");
            csv.append(e.getPtkpStatus()).append(",");
            csv.append(escapeCsv(e.getJobTitle())).append(",");
            csv.append(escapeCsv(e.getDepartment())).append(",");
            csv.append(e.getEmploymentType()).append(",");
            csv.append(e.getHireDate() != null ? e.getHireDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(e.getResignDate() != null ? e.getResignDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(escapeCsv(e.getBankName())).append(",");
            csv.append(escapeCsv(e.getBankAccountNumber())).append(",");
            csv.append(escapeCsv(e.getBpjsKesehatanNumber())).append(",");
            csv.append(escapeCsv(e.getBpjsKetenagakerjaanNumber())).append(",");
            csv.append(e.getEmploymentStatus()).append(",");
            csv.append(e.getUser() != null ? escapeCsv(e.getUser().getUsername()) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 16: Employee Salary Components
    // ============================================
    private String exportEmployeeSalaryComponents() {
        StringBuilder csv = new StringBuilder();
        csv.append("employee_id,component_code,rate,amount,effective_date,end_date\n");

        List<EmployeeSalaryComponent> escs = employeeSalaryComponentRepository.findAll();
        for (EmployeeSalaryComponent esc : escs) {
            csv.append(escapeCsv(esc.getEmployee().getEmployeeId())).append(",");
            csv.append(escapeCsv(esc.getSalaryComponent().getCode())).append(",");
            csv.append(esc.getRate() != null ? esc.getRate() : "").append(",");
            csv.append(esc.getAmount() != null ? esc.getAmount() : "").append(",");
            csv.append(esc.getEffectiveDate() != null ? esc.getEffectiveDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(esc.getEndDate() != null ? esc.getEndDate().format(DATE_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 17: Invoices
    // ============================================
    private String exportInvoices() {
        StringBuilder csv = new StringBuilder();
        csv.append("invoice_number,invoice_date,due_date,client_code,project_code,status,amount,notes,created_at\n");

        List<Invoice> invoices = invoiceRepository.findAll(Sort.by("invoiceNumber"));
        for (Invoice inv : invoices) {
            csv.append(escapeCsv(inv.getInvoiceNumber())).append(",");
            csv.append(inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(inv.getDueDate() != null ? inv.getDueDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(inv.getClient() != null ? escapeCsv(inv.getClient().getCode()) : "").append(",");
            csv.append(inv.getProject() != null ? escapeCsv(inv.getProject().getCode()) : "").append(",");
            csv.append(inv.getStatus()).append(",");
            csv.append(inv.getAmount()).append(",");
            csv.append(escapeCsv(inv.getNotes())).append(",");
            csv.append(inv.getCreatedAt() != null ? inv.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 18: Transactions
    // ============================================
    private String exportTransactions() {
        StringBuilder csv = new StringBuilder();
        csv.append("transaction_number,transaction_date,template_name,project_code,amount,description,");
        csv.append("reference_number,notes,status,void_reason,void_notes,voided_at,voided_by,posted_at,posted_by,created_at\n");

        List<Transaction> transactions = transactionRepository.findAll(Sort.by("transactionNumber"));
        for (Transaction t : transactions) {
            csv.append(escapeCsv(t.getTransactionNumber())).append(",");
            csv.append(t.getTransactionDate() != null ? t.getTransactionDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(t.getJournalTemplate() != null ? escapeCsv(t.getJournalTemplate().getTemplateName()) : "").append(",");
            csv.append(t.getProject() != null ? escapeCsv(t.getProject().getCode()) : "").append(",");
            csv.append(t.getAmount()).append(",");
            csv.append(escapeCsv(t.getDescription())).append(",");
            csv.append(escapeCsv(t.getReferenceNumber())).append(",");
            csv.append(escapeCsv(t.getNotes())).append(",");
            csv.append(t.getStatus()).append(",");
            csv.append(t.getVoidReason() != null ? t.getVoidReason() : "").append(",");
            csv.append(escapeCsv(t.getVoidNotes())).append(",");
            csv.append(t.getVoidedAt() != null ? t.getVoidedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(t.getVoidedBy())).append(",");
            csv.append(t.getPostedAt() != null ? t.getPostedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(t.getPostedBy())).append(",");
            csv.append(t.getCreatedAt() != null ? t.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 19: Transaction Account Mappings
    // ============================================
    private String exportTransactionAccountMappings() {
        StringBuilder csv = new StringBuilder();
        csv.append("transaction_number,template_name,line_order,account_code,amount\n");

        List<Transaction> transactions = transactionRepository.findAll(Sort.by("transactionNumber"));
        for (Transaction t : transactions) {
            for (TransactionAccountMapping tam : t.getAccountMappings()) {
                csv.append(escapeCsv(t.getTransactionNumber())).append(",");
                csv.append(t.getJournalTemplate() != null ? escapeCsv(t.getJournalTemplate().getTemplateName()) : "").append(",");
                csv.append(tam.getTemplateLine() != null ? tam.getTemplateLine().getLineOrder() : "").append(",");
                csv.append(tam.getAccount() != null ? escapeCsv(tam.getAccount().getAccountCode()) : "").append(",");
                csv.append(tam.getAmount() != null ? tam.getAmount() : "").append("\n");
            }
        }
        return csv.toString();
    }

    // ============================================
    // 20: Journal Entries
    // ============================================
    private String exportJournalEntries() {
        StringBuilder csv = new StringBuilder();
        csv.append("journal_number,journal_date,transaction_number,description,status,");
        csv.append("account_code,debit_amount,credit_amount,posted_at,voided_at,void_reason\n");

        List<JournalEntry> entries = journalEntryRepository.findAll(Sort.by("journalNumber"));
        for (JournalEntry je : entries) {
            csv.append(escapeCsv(je.getJournalNumber())).append(",");
            csv.append(je.getEffectiveJournalDate() != null ? je.getEffectiveJournalDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(je.getTransaction() != null ? escapeCsv(je.getTransaction().getTransactionNumber()) : "").append(",");
            csv.append(escapeCsv(je.getEffectiveDescription())).append(",");
            csv.append(je.getStatus()).append(",");
            csv.append(je.getAccount() != null ? escapeCsv(je.getAccount().getAccountCode()) : "").append(",");
            csv.append(je.getDebitAmount()).append(",");
            csv.append(je.getCreditAmount()).append(",");
            csv.append(je.getPostedAt() != null ? je.getPostedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(je.getVoidedAt() != null ? je.getVoidedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(je.getVoidReason())).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 21: Payroll Runs
    // ============================================
    private String exportPayrollRuns() {
        StringBuilder csv = new StringBuilder();
        csv.append("payroll_period,period_start,period_end,status,total_gross,total_deductions,total_net_pay,");
        csv.append("total_company_bpjs,total_pph21,employee_count,notes,posted_at,cancelled_at,cancel_reason,created_at\n");

        List<PayrollRun> payrollRuns = payrollRunRepository.findAll(Sort.by("payrollPeriod"));
        for (PayrollRun pr : payrollRuns) {
            csv.append(escapeCsv(pr.getPayrollPeriod())).append(",");
            csv.append(pr.getPeriodStart() != null ? pr.getPeriodStart().format(DATE_FORMATTER) : "").append(",");
            csv.append(pr.getPeriodEnd() != null ? pr.getPeriodEnd().format(DATE_FORMATTER) : "").append(",");
            csv.append(pr.getStatus()).append(",");
            csv.append(pr.getTotalGross()).append(",");
            csv.append(pr.getTotalDeductions()).append(",");
            csv.append(pr.getTotalNetPay()).append(",");
            csv.append(pr.getTotalCompanyBpjs()).append(",");
            csv.append(pr.getTotalPph21()).append(",");
            csv.append(pr.getEmployeeCount()).append(",");
            csv.append(escapeCsv(pr.getNotes())).append(",");
            csv.append(pr.getPostedAt() != null ? pr.getPostedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(pr.getCancelledAt() != null ? pr.getCancelledAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(pr.getCancelReason())).append(",");
            csv.append(pr.getCreatedAt() != null ? pr.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 22: Payroll Details
    // ============================================
    private String exportPayrollDetails() {
        StringBuilder csv = new StringBuilder();
        csv.append("payroll_period,employee_id,gross_salary,total_deductions,net_pay,");
        csv.append("bpjs_kes_employee,bpjs_kes_company,bpjs_jht_employee,bpjs_jht_company,");
        csv.append("bpjs_jp_employee,bpjs_jp_company,bpjs_jkk,bpjs_jkm,pph21\n");

        List<PayrollRun> payrollRuns = payrollRunRepository.findAll(Sort.by("payrollPeriod"));
        for (PayrollRun pr : payrollRuns) {
            List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunId(pr.getId());
            for (PayrollDetail pd : details) {
                csv.append(escapeCsv(pr.getPayrollPeriod())).append(",");
                csv.append(pd.getEmployee() != null ? escapeCsv(pd.getEmployee().getEmployeeId()) : "").append(",");
                csv.append(pd.getGrossSalary()).append(",");
                csv.append(pd.getTotalDeductions()).append(",");
                csv.append(pd.getNetPay()).append(",");
                csv.append(pd.getBpjsKesEmployee()).append(",");
                csv.append(pd.getBpjsKesCompany()).append(",");
                csv.append(pd.getBpjsJhtEmployee()).append(",");
                csv.append(pd.getBpjsJhtCompany()).append(",");
                csv.append(pd.getBpjsJpEmployee()).append(",");
                csv.append(pd.getBpjsJpCompany()).append(",");
                csv.append(pd.getBpjsJkk()).append(",");
                csv.append(pd.getBpjsJkm()).append(",");
                csv.append(pd.getPph21()).append("\n");
            }
        }
        return csv.toString();
    }

    // ============================================
    // 23: Amortization Schedules
    // ============================================
    private String exportAmortizationSchedules() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,schedule_type,source_account_code,target_account_code,");
        csv.append("total_amount,total_periods,period_amount,start_date,status,");
        csv.append("auto_post,completed_periods,amortized_amount\n");

        List<AmortizationSchedule> schedules = amortizationScheduleRepository.findAll(Sort.by("code"));
        for (AmortizationSchedule as : schedules) {
            csv.append(escapeCsv(as.getCode())).append(",");
            csv.append(escapeCsv(as.getName())).append(",");
            csv.append(as.getScheduleType()).append(",");
            csv.append(as.getSourceAccount() != null ? escapeCsv(as.getSourceAccount().getAccountCode()) : "").append(",");
            csv.append(as.getTargetAccount() != null ? escapeCsv(as.getTargetAccount().getAccountCode()) : "").append(",");
            csv.append(as.getTotalAmount()).append(",");
            csv.append(as.getTotalPeriods()).append(",");
            csv.append(as.getPeriodAmount()).append(",");
            csv.append(as.getStartDate() != null ? as.getStartDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(as.getStatus()).append(",");
            csv.append(as.getAutoPost()).append(",");
            csv.append(as.getCompletedPeriods()).append(",");
            csv.append(as.getAmortizedAmount()).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 24: Amortization Entries
    // ============================================
    private String exportAmortizationEntries() {
        StringBuilder csv = new StringBuilder();
        csv.append("schedule_code,period_number,period_start,period_end,amount,status,journal_number,posted_at\n");

        List<AmortizationSchedule> schedules = amortizationScheduleRepository.findAll(Sort.by("code"));
        for (AmortizationSchedule as : schedules) {
            List<AmortizationEntry> entries = amortizationEntryRepository.findByScheduleIdOrderByPeriodNumberAsc(as.getId());
            for (AmortizationEntry ae : entries) {
                csv.append(escapeCsv(as.getCode())).append(",");
                csv.append(ae.getPeriodNumber()).append(",");
                csv.append(ae.getPeriodStart() != null ? ae.getPeriodStart().format(DATE_FORMATTER) : "").append(",");
                csv.append(ae.getPeriodEnd() != null ? ae.getPeriodEnd().format(DATE_FORMATTER) : "").append(",");
                csv.append(ae.getAmount()).append(",");
                csv.append(ae.getStatus()).append(",");
                csv.append(escapeCsv(ae.getJournalNumber())).append(",");
                csv.append(ae.getPostedAt() != null ? ae.getPostedAt().format(DATETIME_FORMATTER) : "").append("\n");
            }
        }
        return csv.toString();
    }

    // ============================================
    // 25: Tax Transaction Details
    // ============================================
    private String exportTaxTransactionDetails() {
        StringBuilder csv = new StringBuilder();
        csv.append("transaction_number,tax_type,counterparty_name,counterparty_npwp,counterparty_nik,");
        csv.append("counterparty_nitku,tax_object_code,dpp,tax_amount,faktur_number,faktur_date\n");

        List<TaxTransactionDetail> details = taxTransactionDetailRepository.findAll();
        for (TaxTransactionDetail ttd : details) {
            csv.append(ttd.getTransaction() != null ? escapeCsv(ttd.getTransaction().getTransactionNumber()) : "").append(",");
            csv.append(ttd.getTaxType()).append(",");
            csv.append(escapeCsv(ttd.getCounterpartyName())).append(",");
            csv.append(escapeCsv(ttd.getCounterpartyNpwp())).append(",");
            csv.append(escapeCsv(ttd.getCounterpartyNik())).append(",");
            csv.append(escapeCsv(ttd.getCounterpartyNitku())).append(",");
            csv.append(escapeCsv(ttd.getTaxObjectCode())).append(",");
            csv.append(ttd.getDpp() != null ? ttd.getDpp() : "").append(",");
            csv.append(ttd.getTaxAmount() != null ? ttd.getTaxAmount() : "").append(",");
            csv.append(escapeCsv(ttd.getFakturNumber())).append(",");
            csv.append(ttd.getFakturDate() != null ? ttd.getFakturDate().format(DATE_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 26: Tax Deadline Completions
    // ============================================
    private String exportTaxDeadlineCompletions() {
        StringBuilder csv = new StringBuilder();
        csv.append("deadline_type,year,month,completed_date,completed_by,reference_number,notes\n");

        List<TaxDeadlineCompletion> completions = taxDeadlineCompletionRepository.findAll();
        for (TaxDeadlineCompletion tdc : completions) {
            csv.append(tdc.getTaxDeadline().getDeadlineType()).append(",");
            csv.append(tdc.getYear()).append(",");
            csv.append(tdc.getMonth()).append(",");
            csv.append(tdc.getCompletedDate() != null ? tdc.getCompletedDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(escapeCsv(tdc.getCompletedBy())).append(",");
            csv.append(escapeCsv(tdc.getReferenceNumber())).append(",");
            csv.append(escapeCsv(tdc.getNotes())).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 27: Draft Transactions
    // ============================================
    private String exportDraftTransactions() {
        StringBuilder csv = new StringBuilder();
        csv.append("source,status,merchant_name,transaction_date,amount,suggested_template_name,");
        csv.append("merchant_confidence,date_confidence,amount_confidence,raw_ocr_text,");
        csv.append("processed_at,processed_by,rejection_reason\n");

        List<DraftTransaction> drafts = draftTransactionRepository.findAll();
        for (DraftTransaction dt : drafts) {
            csv.append(dt.getSource()).append(",");
            csv.append(dt.getStatus()).append(",");
            csv.append(escapeCsv(dt.getMerchantName())).append(",");
            csv.append(dt.getTransactionDate() != null ? dt.getTransactionDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(dt.getAmount() != null ? dt.getAmount() : "").append(",");
            csv.append(dt.getSuggestedTemplate() != null ? escapeCsv(dt.getSuggestedTemplate().getTemplateName()) : "").append(",");
            csv.append(dt.getMerchantConfidence() != null ? dt.getMerchantConfidence() : "").append(",");
            csv.append(dt.getDateConfidence() != null ? dt.getDateConfidence() : "").append(",");
            csv.append(dt.getAmountConfidence() != null ? dt.getAmountConfidence() : "").append(",");
            csv.append(escapeCsv(dt.getRawOcrText())).append(",");
            csv.append(dt.getProcessedAt() != null ? dt.getProcessedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(dt.getProcessedBy())).append(",");
            csv.append(escapeCsv(dt.getRejectionReason())).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 28: Users
    // ============================================
    private String exportUsers() {
        StringBuilder csv = new StringBuilder();
        csv.append("username,password,full_name,email,active,created_at\n");

        List<User> users = userRepository.findAll(Sort.by("username"));
        for (User u : users) {
            csv.append(escapeCsv(u.getUsername())).append(",");
            csv.append(escapeCsv(u.getPassword())).append(",");  // bcrypt hash preserved
            csv.append(escapeCsv(u.getFullName())).append(",");
            csv.append(escapeCsv(u.getEmail())).append(",");
            csv.append(u.getActive()).append(",");
            csv.append(u.getCreatedAt() != null ? u.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 29: User Roles
    // ============================================
    private String exportUserRoles() {
        StringBuilder csv = new StringBuilder();
        csv.append("username,role,created_by,created_at\n");

        List<User> users = userRepository.findAll(Sort.by("username"));
        for (User u : users) {
            for (UserRole ur : u.getUserRoles()) {
                csv.append(escapeCsv(u.getUsername())).append(",");
                csv.append(ur.getRole()).append(",");
                csv.append(escapeCsv(ur.getCreatedBy())).append(",");
                csv.append(ur.getCreatedAt() != null ? ur.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
            }
        }
        return csv.toString();
    }

    // ============================================
    // 30: User Template Preferences
    // ============================================
    private String exportUserTemplatePreferences() {
        StringBuilder csv = new StringBuilder();
        csv.append("username,template_name,is_favorite,use_count,last_used_at\n");

        List<UserTemplatePreference> prefs = userTemplatePreferenceRepository.findAll();
        for (UserTemplatePreference utp : prefs) {
            csv.append(escapeCsv(utp.getUser().getUsername())).append(",");
            csv.append(escapeCsv(utp.getJournalTemplate().getTemplateName())).append(",");
            csv.append(utp.getIsFavorite()).append(",");
            csv.append(utp.getUseCount()).append(",");
            csv.append(utp.getLastUsedAt() != null ? utp.getLastUsedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 31: Telegram User Links
    // ============================================
    private String exportTelegramUserLinks() {
        StringBuilder csv = new StringBuilder();
        csv.append("telegram_user_id,telegram_username,username,is_active,linked_at\n");

        List<TelegramUserLink> links = telegramUserLinkRepository.findAll();
        for (TelegramUserLink tul : links) {
            csv.append(tul.getTelegramUserId()).append(",");
            csv.append(escapeCsv(tul.getTelegramUsername())).append(",");
            csv.append(tul.getUser() != null ? escapeCsv(tul.getUser().getUsername()) : "").append(",");
            csv.append(tul.getIsActive()).append(",");
            csv.append(tul.getLinkedAt() != null ? tul.getLinkedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 32: Audit Logs
    // ============================================
    private String exportAuditLogs() {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,username,action,entity_type,entity_id,ip_address\n");

        List<AuditLog> logs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        for (AuditLog log : logs) {
            csv.append(log.getCreatedAt() != null ? log.getCreatedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(log.getUser() != null ? escapeCsv(log.getUser().getUsername()) : "").append(",");
            csv.append(escapeCsv(log.getAction())).append(",");
            csv.append(escapeCsv(log.getEntityType())).append(",");
            csv.append(log.getEntityId() != null ? log.getEntityId().toString() : "").append(",");
            csv.append(escapeCsv(log.getIpAddress())).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 33: Transaction Sequences
    // ============================================
    private String exportTransactionSequences() {
        StringBuilder csv = new StringBuilder();
        csv.append("sequence_type,prefix,year,last_number\n");

        List<TransactionSequence> sequences = transactionSequenceRepository.findAll();
        for (TransactionSequence ts : sequences) {
            csv.append(escapeCsv(ts.getSequenceType())).append(",");
            csv.append(escapeCsv(ts.getPrefix())).append(",");
            csv.append(ts.getYear()).append(",");
            csv.append(ts.getLastNumber()).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // 34: Asset Categories
    // ============================================
    private String exportAssetCategories() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,description,depreciation_method,useful_life_months,depreciation_rate,");
        csv.append("asset_account_code,accumulated_depreciation_account_code,depreciation_expense_account_code,active\n");

        List<AssetCategory> categories = assetCategoryRepository.findAll(Sort.by("code"));
        for (AssetCategory ac : categories) {
            csv.append(escapeCsv(ac.getCode())).append(",");
            csv.append(escapeCsv(ac.getName())).append(",");
            csv.append(escapeCsv(ac.getDescription())).append(",");
            csv.append(ac.getDepreciationMethod()).append(",");
            csv.append(ac.getUsefulLifeMonths()).append(",");
            csv.append(ac.getDepreciationRate() != null ? ac.getDepreciationRate() : "").append(",");
            csv.append(ac.getAssetAccount() != null ? escapeCsv(ac.getAssetAccount().getAccountCode()) : "").append(",");
            csv.append(ac.getAccumulatedDepreciationAccount() != null ? escapeCsv(ac.getAccumulatedDepreciationAccount().getAccountCode()) : "").append(",");
            csv.append(ac.getDepreciationExpenseAccount() != null ? escapeCsv(ac.getDepreciationExpenseAccount().getAccountCode()) : "").append(",");
            csv.append(ac.getActive()).append("\n");
        }
        return csv.toString();
    }

    // ============================================
    // Documents (files + index)
    // ============================================
    private void exportDocuments(ZipOutputStream zos) throws IOException {
        List<Document> documents = documentRepository.findAll();
        for (Document doc : documents) {
            try {
                Path filePath = documentStorageService.getRootLocation().resolve(doc.getStoragePath());
                if (Files.exists(filePath)) {
                    byte[] content = Files.readAllBytes(filePath);
                    String path = "documents/" + doc.getStoragePath();
                    addBinaryEntry(zos, path, content);
                }
            } catch (Exception e) {
                log.warn("Failed to export document {}: {}", doc.getId(), e.getMessage());
            }
        }

        // Add document index
        StringBuilder index = new StringBuilder();
        index.append("storage_path,original_filename,content_type,file_size,transaction_number,journal_number,uploaded_at\n");
        for (Document doc : documents) {
            index.append(escapeCsv(doc.getStoragePath())).append(",");
            index.append(escapeCsv(doc.getOriginalFilename())).append(",");
            index.append(escapeCsv(doc.getContentType())).append(",");
            index.append(doc.getFileSize()).append(",");
            index.append(doc.getTransaction() != null ? escapeCsv(doc.getTransaction().getTransactionNumber()) : "").append(",");
            index.append(doc.getJournalEntry() != null ? escapeCsv(doc.getJournalEntry().getJournalNumber()) : "").append(",");
            index.append(doc.getCreatedAt() != null ? doc.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        addTextEntry(zos, "documents/index.csv", index.toString());
    }

    private void addTextEntry(ZipOutputStream zos, String filename, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(filename));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void addBinaryEntry(ZipOutputStream zos, String filename, byte[] content) throws IOException {
        zos.putNextEntry(new ZipEntry(filename));
        zos.write(content);
        zos.closeEntry();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains special chars
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // DTO for export statistics
    public record ExportStatistics(
            long accountCount,
            long journalEntryCount,
            long transactionCount,
            long clientCount,
            long projectCount,
            long invoiceCount,
            long employeeCount,
            long payrollRunCount,
            long documentCount,
            long auditLogCount,
            long templateCount,
            long userCount
    ) {
        public long totalRecords() {
            return accountCount + journalEntryCount + transactionCount + clientCount +
                    projectCount + invoiceCount + employeeCount + payrollRunCount +
                    documentCount + auditLogCount + templateCount + userCount;
        }
    }
}
