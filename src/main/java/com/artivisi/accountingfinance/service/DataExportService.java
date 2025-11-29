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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DataExportService {

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

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Export all company data to a ZIP archive.
     */
    public byte[] exportAllData() throws IOException {
        log.info("Starting full data export");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Export metadata
            addManifest(zos);

            // Export CSV data
            addTextEntry(zos, "chart_of_accounts.csv", exportChartOfAccounts());
            addTextEntry(zos, "journal_entries.csv", exportJournalEntries());
            addTextEntry(zos, "transactions.csv", exportTransactions());
            addTextEntry(zos, "clients.csv", exportClients());
            addTextEntry(zos, "projects.csv", exportProjects());
            addTextEntry(zos, "invoices.csv", exportInvoices());
            addTextEntry(zos, "employees.csv", exportEmployees());
            addTextEntry(zos, "payroll_runs.csv", exportPayrollRuns());
            addTextEntry(zos, "payroll_details.csv", exportPayrollDetails());
            addTextEntry(zos, "audit_logs.csv", exportAuditLogs());

            // Export documents
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
                auditLogRepository.count()
        );
    }

    private void addManifest(ZipOutputStream zos) throws IOException {
        CompanyConfig config = companyConfigRepository.findFirst().orElse(null);

        StringBuilder manifest = new StringBuilder();
        manifest.append("# Data Export Manifest\n\n");
        manifest.append("Export Date: ").append(LocalDateTime.now().format(DATETIME_FORMATTER)).append("\n");
        manifest.append("Application: Aplikasi Akunting\n");
        manifest.append("Format Version: 1.0\n\n");

        if (config != null) {
            manifest.append("## Company Information\n");
            manifest.append("Name: ").append(config.getCompanyName()).append("\n");
            manifest.append("NPWP: ").append(config.getNpwp() != null ? config.getNpwp() : "-").append("\n");
            manifest.append("\n");
        }

        ExportStatistics stats = getExportStatistics();
        manifest.append("## Export Contents\n");
        manifest.append("- Chart of Accounts: ").append(stats.accountCount()).append(" records\n");
        manifest.append("- Journal Entries: ").append(stats.journalEntryCount()).append(" records\n");
        manifest.append("- Transactions: ").append(stats.transactionCount()).append(" records\n");
        manifest.append("- Clients: ").append(stats.clientCount()).append(" records\n");
        manifest.append("- Projects: ").append(stats.projectCount()).append(" records\n");
        manifest.append("- Invoices: ").append(stats.invoiceCount()).append(" records\n");
        manifest.append("- Employees: ").append(stats.employeeCount()).append(" records\n");
        manifest.append("- Payroll Runs: ").append(stats.payrollRunCount()).append(" records\n");
        manifest.append("- Documents: ").append(stats.documentCount()).append(" files\n");
        manifest.append("- Audit Logs: ").append(stats.auditLogCount()).append(" records\n");

        addTextEntry(zos, "MANIFEST.md", manifest.toString());
    }

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

    private String exportJournalEntries() {
        StringBuilder csv = new StringBuilder();
        csv.append("journal_number,journal_date,description,status,posted_at,voided_at,void_reason,");
        csv.append("account_code,debit_amount,credit_amount\n");

        List<JournalEntry> entries = journalEntryRepository.findAll(Sort.by("journalDate", "journalNumber"));
        for (JournalEntry je : entries) {
            csv.append(escapeCsv(je.getJournalNumber())).append(",");
            csv.append(je.getJournalDate() != null ? je.getJournalDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(escapeCsv(je.getDescription())).append(",");
            csv.append(je.getStatus()).append(",");
            csv.append(je.getPostedAt() != null ? je.getPostedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(je.getVoidedAt() != null ? je.getVoidedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(escapeCsv(je.getVoidReason())).append(",");
            csv.append(je.getAccount() != null ? escapeCsv(je.getAccount().getAccountCode()) : "").append(",");
            csv.append(je.getDebitAmount()).append(",");
            csv.append(je.getCreditAmount()).append("\n");
        }
        return csv.toString();
    }

    private String exportTransactions() {
        StringBuilder csv = new StringBuilder();
        csv.append("transaction_number,transaction_date,description,template_category,status,amount,");
        csv.append("reference_number,project_code,created_at\n");

        List<Transaction> transactions = transactionRepository.findAll(Sort.by(Sort.Direction.DESC, "transactionDate", "transactionNumber"));
        for (Transaction t : transactions) {
            csv.append(escapeCsv(t.getTransactionNumber())).append(",");
            csv.append(t.getTransactionDate() != null ? t.getTransactionDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(escapeCsv(t.getDescription())).append(",");
            csv.append(t.getJournalTemplate() != null ? t.getJournalTemplate().getCategory() : "").append(",");
            csv.append(t.getStatus()).append(",");
            csv.append(t.getAmount()).append(",");
            csv.append(escapeCsv(t.getReferenceNumber())).append(",");
            csv.append(t.getProject() != null ? escapeCsv(t.getProject().getCode()) : "").append(",");
            csv.append(t.getCreatedAt() != null ? t.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    private String exportClients() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,contact_person,email,phone,address,npwp,active,created_at\n");

        List<Client> clients = clientRepository.findAll();
        for (Client c : clients) {
            csv.append(escapeCsv(c.getCode())).append(",");
            csv.append(escapeCsv(c.getName())).append(",");
            csv.append(escapeCsv(c.getContactPerson())).append(",");
            csv.append(escapeCsv(c.getEmail())).append(",");
            csv.append(escapeCsv(c.getPhone())).append(",");
            csv.append(escapeCsv(c.getAddress())).append(",");
            csv.append(escapeCsv(c.getNpwp())).append(",");
            csv.append(c.getActive()).append(",");
            csv.append(c.getCreatedAt() != null ? c.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    private String exportProjects() {
        StringBuilder csv = new StringBuilder();
        csv.append("code,name,client_code,status,start_date,end_date,budget_amount,description,created_at\n");

        List<Project> projects = projectRepository.findAll();
        for (Project p : projects) {
            csv.append(escapeCsv(p.getCode())).append(",");
            csv.append(escapeCsv(p.getName())).append(",");
            csv.append(p.getClient() != null ? escapeCsv(p.getClient().getCode()) : "").append(",");
            csv.append(p.getStatus()).append(",");
            csv.append(p.getStartDate() != null ? p.getStartDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(p.getEndDate() != null ? p.getEndDate().format(DATE_FORMATTER) : "").append(",");
            csv.append(p.getBudgetAmount()).append(",");
            csv.append(escapeCsv(p.getDescription())).append(",");
            csv.append(p.getCreatedAt() != null ? p.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    private String exportInvoices() {
        StringBuilder csv = new StringBuilder();
        csv.append("invoice_number,invoice_date,due_date,client_code,project_code,status,amount,notes,created_at\n");

        List<Invoice> invoices = invoiceRepository.findAll();
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

    private String exportEmployees() {
        StringBuilder csv = new StringBuilder();
        csv.append("employee_id,name,email,nik_ktp,npwp,ptkp_status,job_title,department,");
        csv.append("employment_type,hire_date,bank_name,bank_account,employment_status,created_at\n");

        List<Employee> employees = employeeRepository.findAll();
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
            csv.append(escapeCsv(e.getBankName())).append(",");
            csv.append(escapeCsv(e.getBankAccountNumber())).append(",");
            csv.append(e.getEmploymentStatus()).append(",");
            csv.append(e.getCreatedAt() != null ? e.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    private String exportPayrollRuns() {
        StringBuilder csv = new StringBuilder();
        csv.append("payroll_period,status,total_gross,total_deductions,total_net_pay,");
        csv.append("total_company_bpjs,total_pph21,employee_count,posted_at,created_at\n");

        List<PayrollRun> payrollRuns = payrollRunRepository.findAll(Sort.by(Sort.Direction.DESC, "payrollPeriod"));
        for (PayrollRun pr : payrollRuns) {
            csv.append(escapeCsv(pr.getPayrollPeriod())).append(",");
            csv.append(pr.getStatus()).append(",");
            csv.append(pr.getTotalGross()).append(",");
            csv.append(pr.getTotalDeductions()).append(",");
            csv.append(pr.getTotalNetPay()).append(",");
            csv.append(pr.getTotalCompanyBpjs()).append(",");
            csv.append(pr.getTotalPph21()).append(",");
            csv.append(pr.getEmployeeCount()).append(",");
            csv.append(pr.getPostedAt() != null ? pr.getPostedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(pr.getCreatedAt() != null ? pr.getCreatedAt().format(DATETIME_FORMATTER) : "").append("\n");
        }
        return csv.toString();
    }

    private String exportPayrollDetails() {
        StringBuilder csv = new StringBuilder();
        csv.append("payroll_period,employee_id,employee_name,gross_salary,total_deductions,net_pay,");
        csv.append("bpjs_kes_employee,bpjs_kes_company,bpjs_jht_employee,bpjs_jht_company,");
        csv.append("bpjs_jp_employee,bpjs_jp_company,bpjs_jkk,bpjs_jkm,pph21\n");

        // Get all payroll runs and their details
        List<PayrollRun> payrollRuns = payrollRunRepository.findAll(Sort.by(Sort.Direction.DESC, "payrollPeriod"));
        for (PayrollRun pr : payrollRuns) {
            List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunId(pr.getId());
            for (PayrollDetail pd : details) {
                csv.append(escapeCsv(pr.getPayrollPeriod())).append(",");
                csv.append(pd.getEmployee() != null ? escapeCsv(pd.getEmployee().getEmployeeId()) : "").append(",");
                csv.append(pd.getEmployee() != null ? escapeCsv(pd.getEmployee().getName()) : "").append(",");
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

    private String exportAuditLogs() {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,user_id,action,entity_type,entity_id,ip_address\n");

        List<AuditLog> logs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        for (AuditLog log : logs) {
            csv.append(log.getCreatedAt() != null ? log.getCreatedAt().format(DATETIME_FORMATTER) : "").append(",");
            csv.append(log.getUser() != null ? log.getUser().getId().toString() : "").append(",");
            csv.append(escapeCsv(log.getAction())).append(",");
            csv.append(escapeCsv(log.getEntityType())).append(",");
            csv.append(log.getEntityId() != null ? log.getEntityId().toString() : "").append(",");
            csv.append(escapeCsv(log.getIpAddress())).append("\n");
        }
        return csv.toString();
    }

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
        index.append("id,filename,content_type,size,storage_path,uploaded_at\n");
        for (Document doc : documents) {
            index.append(doc.getId()).append(",");
            index.append(escapeCsv(doc.getOriginalFilename())).append(",");
            index.append(escapeCsv(doc.getContentType())).append(",");
            index.append(doc.getFileSize()).append(",");
            index.append(escapeCsv(doc.getStoragePath())).append(",");
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
            long auditLogCount
    ) {
        public long totalRecords() {
            return accountCount + journalEntryCount + transactionCount + clientCount +
                    projectCount + invoiceCount + employeeCount + payrollRunCount +
                    documentCount + auditLogCount;
        }
    }
}
