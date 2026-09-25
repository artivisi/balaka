package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.AnalysisReport;
import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.PaymentTrigger;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.enums.TaxDeadlineType;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AmortizationEntryRepository;
import com.artivisi.accountingfinance.repository.AmortizationScheduleRepository;
import com.artivisi.accountingfinance.repository.AnalysisReportRepository;
import com.artivisi.accountingfinance.repository.BillRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.ProjectMilestoneRepository;
import com.artivisi.accountingfinance.repository.ProjectPaymentTermRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TaxDeadlineCompletionRepository;
import com.artivisi.accountingfinance.repository.TaxDeadlineRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AmortizationScheduleService;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.DocumentService;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import com.artivisi.accountingfinance.service.FiscalYearClosingService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
import com.artivisi.accountingfinance.service.ProjectPaymentTermService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.service.TaxDeadlineService;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import com.artivisi.accountingfinance.service.TaxTransactionDetailService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every web write endpoint (POST/DELETE under controller/, not controller/api/) must require its write permission,
 * not just the controller's *_VIEW permission. Each case names the permission(s) that allow it; for a role that
 * holds none of them the request (valid CSRF token, real target) must get 403 and leave the target unchanged.
 * Roles that do hold the permission are covered by each controller's own functional test.
 */
@DisplayName("Write endpoint authorization")
@Import(ServiceTestDataInitializer.class)
class WriteEndpointAuthorizationTest extends PlaywrightTestBase {

    private static final String CLIENT_CODE = "MANDIRI";
    private static final String JOURNAL_NUMBER = "JRN-SVC-001-D";
    private static final String TEMPLATE_NAME = "Setoran Modal";
    private static final YearMonth PAYROLL_PERIOD = YearMonth.of(2037, 1);
    private static final YearMonth NEW_PAYROLL_PERIOD = YearMonth.of(2037, 2);
    private static final int FISCAL_YEAR = 2046;
    private static final int GENERATED_FISCAL_YEAR = 2047;
    private static final int CLOSING_YEAR = 2048;

    private static Fixture fixture;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClientService clientService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private VendorService vendorService;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMilestoneService milestoneService;
    @Autowired private ProjectMilestoneRepository milestoneRepository;
    @Autowired private ProjectPaymentTermService paymentTermService;
    @Autowired private ProjectPaymentTermRepository paymentTermRepository;
    @Autowired private InvoiceService invoiceService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private BillService billService;
    @Autowired private BillRepository billRepository;
    @Autowired private FiscalPeriodService fiscalPeriodService;
    @Autowired private FiscalPeriodRepository fiscalPeriodRepository;
    @Autowired private FiscalYearClosingService fiscalYearClosingService;
    @Autowired private PayrollService payrollService;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private AmortizationScheduleService amortizationScheduleService;
    @Autowired private AmortizationScheduleRepository amortizationScheduleRepository;
    @Autowired private AmortizationEntryRepository amortizationEntryRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private AnalysisReportRepository analysisReportRepository;
    @Autowired private DraftTransactionRepository draftRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TaxTransactionDetailService taxDetailService;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private TaxDeadlineService taxDeadlineService;
    @Autowired private TaxDeadlineRepository taxDeadlineRepository;
    @Autowired private TaxDeadlineCompletionRepository completionRepository;
    @Autowired private TaxReportDetailService taxReportDetailService;
    @Autowired private FiscalAdjustmentRepository fiscalAdjustmentRepository;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentRepository documentRepository;

    private String csrfHeader;
    private String csrfToken;

    @ParameterizedTest(name = "{0}: every write without its permission is refused with 403 and changes nothing")
    @EnumSource(value = Role.class, names = {"AUDITOR", "STAFF", "EMPLOYEE"})
    void shouldRefuseWritesWithoutPermission(Role role) {
        Fixture f = fixture();
        Set<String> granted = Permission.getPermissionsForRole(role);
        List<WriteCase> refused = cases(f).stream().filter(c -> !c.allowed().test(granted)).toList();
        assertFalse(refused.isEmpty(), role + " holds every write permission");

        loginAs(role);

        List<String> failures = new ArrayList<>();
        for (WriteCase c : refused) {
            String before = c.state().get();
            APIResponse response = send(c);
            String after = c.state().get();
            if (response.status() != 403) {
                failures.add(c.method() + " " + c.path() + " -> HTTP " + response.status());
            }
            if (!Objects.equals(before, after)) {
                failures.add(c.method() + " " + c.path() + " changed state: " + before + " -> " + after);
            }
        }
        assertTrue(failures.isEmpty(), role + " performed writes it has no permission for:\n"
                + String.join("\n", failures));
    }

    @Test
    @DisplayName("AUDITOR sees the records but none of the write controls")
    void shouldHideWriteControlsFromAuditor() {
        Fixture f = fixture();
        loginAs(Role.AUDITOR);

        navigateTo("/invoices/" + f.invoice.getInvoiceNumber());
        waitForPageLoad();
        assertThat(page.getByText(f.invoice.getInvoiceNumber()).first()).isVisible();
        assertThat(page.locator("#form-send, #form-cancel, #form-delete, [data-testid='payment-form-section']"))
                .hasCount(0);
        assertThat(page.locator("a[href$='/edit']")).hasCount(0);
        navigateTo("/invoices");
        waitForPageLoad();
        assertThat(page.locator("#btn-new-invoice")).hasCount(0);

        navigateTo("/bills/" + f.bill.getBillNumber());
        waitForPageLoad();
        assertThat(page.getByText(f.bill.getBillNumber()).first()).isVisible();
        assertThat(page.locator("form[action^='/bills/']")).hasCount(0);
        assertThat(page.locator("a[href$='/edit']")).hasCount(0);

        navigateTo("/clients/" + f.client.getCode());
        waitForPageLoad();
        assertThat(page.getByText(f.client.getName()).first()).isVisible();
        assertThat(page.locator("form[action^='/clients/']")).hasCount(0);
        assertThat(page.locator("a[href$='/edit']")).hasCount(0);

        navigateTo("/projects/" + f.project.getCode());
        waitForPageLoad();
        assertThat(page.getByText(f.milestone.getName()).first()).isVisible();
        assertThat(page.getByText(f.paymentTerm.getName()).first()).isVisible();
        assertThat(page.locator("form[action^='/projects/']")).hasCount(0);
        assertThat(page.locator("a[href$='/edit'], a[href$='/new']")).hasCount(0);

        navigateTo("/payroll/" + f.payrollRun.getId());
        waitForPageLoad();
        assertThat(page.locator("#page-title")).containsText("Detail Payroll");
        assertThat(page.locator("form[action^='/payroll/']")).hasCount(0);
        navigateTo("/payroll");
        waitForPageLoad();
        assertThat(page.locator("a[href='/payroll/new']")).hasCount(0);

        navigateTo("/fiscal-periods/" + f.fiscalPeriod.getId());
        waitForPageLoad();
        assertThat(page.locator("form[action^='/fiscal-periods/']")).hasCount(0);
        navigateTo("/fiscal-periods");
        waitForPageLoad();
        assertThat(page.locator("form[action='/fiscal-periods/generate-year'], a[href='/fiscal-periods/new']"))
                .hasCount(0);

        navigateTo("/amortization/" + f.schedule.getId());
        waitForPageLoad();
        assertThat(page.getByText(f.schedule.getName()).first()).isVisible();
        assertThat(page.locator("form[action^='/amortization/'], [hx-post^='/amortization/']")).hasCount(0);
        assertThat(page.locator("a[href$='/edit']")).hasCount(0);

        navigateTo("/drafts/" + f.draft.getId());
        waitForPageLoad();
        assertThat(page.locator("form[action^='/drafts/']")).hasCount(0);

        navigateTo("/analysis-reports/" + f.analysisReport.getId());
        waitForPageLoad();
        assertThat(page.getByText(f.analysisReport.getTitle()).first()).isVisible();
        assertThat(page.locator("[data-testid='btn-delete']")).hasCount(0);

        navigateTo("/reports/rekonsiliasi-fiskal?year=" + FISCAL_YEAR);
        waitForPageLoad();
        assertThat(page.getByText(f.fiscalAdjustment.getDescription()).first()).isVisible();
        assertThat(page.locator("form[action^='/reports/rekonsiliasi-fiskal/']")).hasCount(0);

        navigateTo("/reports/fiscal-closing?year=" + CLOSING_YEAR);
        waitForPageLoad();
        assertThat(page.locator("#form-execute, #btn-reverse")).hasCount(0);

        navigateTo("/transactions/" + f.transaction.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='document-list']")).isVisible();
        assertThat(page.locator("[data-testid='document-upload-form'], [hx-delete^='/documents/']")).hasCount(0);
    }

    // ==================== cases ====================

    private record WriteCase(String method, String path, UnaryOperator<RequestOptions> body,
                             Predicate<Set<String>> allowed, Supplier<String> state) {
    }

    private List<WriteCase> cases(Fixture f) {
        List<WriteCase> c = new ArrayList<>();
        String client = "/clients/" + f.client.getCode();
        Supplier<String> clientState = () -> clientRepository.findByCode(f.client.getCode())
                .map(x -> x.getName() + "|" + x.getActive()).orElseThrow();
        String newClientCode = "WEA-C-" + uid();
        c.add(post("/clients/new", form("code", newClientCode, "name", "Klien Baru"), any(Permission.CLIENT_CREATE),
                () -> String.valueOf(clientRepository.existsByCode(newClientCode))));
        c.add(post(client, form("code", f.client.getCode(), "name", "Diubah"), any(Permission.CLIENT_EDIT), clientState));
        c.add(post(client + "/deactivate", form(), any(Permission.CLIENT_EDIT), clientState));
        c.add(post("/clients/" + f.inactiveClient.getCode() + "/activate", form(), any(Permission.CLIENT_EDIT),
                () -> clientRepository.findByCode(f.inactiveClient.getCode()).orElseThrow().getActive().toString()));

        String vendor = "/vendors/" + f.vendor.getCode();
        Supplier<String> vendorState = () -> vendorRepository.findByCode(f.vendor.getCode())
                .map(x -> x.getName() + "|" + x.getActive()).orElseThrow();
        String newVendorCode = "WEA-V-" + uid();
        c.add(post("/vendors/new", form("code", newVendorCode, "name", "Vendor Baru"), any(Permission.VENDOR_CREATE),
                () -> String.valueOf(vendorRepository.existsByCode(newVendorCode))));
        c.add(post(vendor, form("code", f.vendor.getCode(), "name", "Diubah"), any(Permission.VENDOR_EDIT), vendorState));
        c.add(post(vendor + "/deactivate", form(), any(Permission.VENDOR_DELETE), vendorState));
        c.add(post("/vendors/" + f.inactiveVendor.getCode() + "/activate", form(), any(Permission.VENDOR_EDIT),
                () -> vendorRepository.findByCode(f.inactiveVendor.getCode()).orElseThrow().getActive().toString()));

        String project = "/projects/" + f.project.getCode();
        Supplier<String> projectState = () -> projectRepository.findByCode(f.project.getCode())
                .map(x -> x.getName() + "|" + x.getStatus()).orElseThrow();
        String newProjectCode = "WEA-P-" + uid();
        c.add(post("/projects/new", form("code", newProjectCode, "name", "Proyek Baru"), any(Permission.PROJECT_CREATE),
                () -> String.valueOf(projectRepository.existsByCode(newProjectCode))));
        c.add(post(project, form("code", f.project.getCode(), "name", "Diubah"), any(Permission.PROJECT_EDIT), projectState));
        c.add(post(project + "/complete", form(), any(Permission.PROJECT_EDIT), projectState));
        c.add(post(project + "/archive", form(), any(Permission.PROJECT_EDIT), projectState));
        c.add(post(project + "/reactivate", form(), any(Permission.PROJECT_EDIT), projectState));

        String milestone = project + "/milestones/" + f.milestone.getId();
        Supplier<String> milestoneState = () -> milestoneRepository.findByProjectIdOrderBySequenceAsc(f.project.getId())
                .stream().map(m -> m.getName() + "|" + m.getStatus()).toList().toString();
        c.add(post(project + "/milestones/new", form("sequence", "2", "name", "Milestone Baru", "weightPercent", "10"),
                any(Permission.PROJECT_EDIT), milestoneState));
        c.add(post(milestone, form("sequence", "1", "name", "Diubah", "weightPercent", "50"),
                any(Permission.PROJECT_EDIT), milestoneState));
        c.add(post(milestone + "/start", form(), any(Permission.PROJECT_EDIT), milestoneState));
        c.add(post(milestone + "/complete", form(), any(Permission.PROJECT_EDIT), milestoneState));
        c.add(post(milestone + "/reset", form(), any(Permission.PROJECT_EDIT), milestoneState));
        c.add(post(milestone + "/delete", form(), any(Permission.PROJECT_EDIT), milestoneState));

        String term = project + "/payment-terms/" + f.paymentTerm.getId();
        Supplier<String> termState = () -> paymentTermRepository.findByProjectIdOrderBySequenceAsc(f.project.getId())
                .stream().map(t -> t.getName() + "|" + t.getAmount()).toList()
                + "|invoices=" + invoiceRepository.findByPaymentTermId(f.paymentTerm.getId()).size();
        c.add(post(project + "/payment-terms/new", form("sequence", "2", "name", "Termin Baru",
                "dueTrigger", "ON_SIGNING", "amount", "1000000"), any(Permission.PROJECT_EDIT), termState));
        c.add(post(term, form("sequence", "1", "name", "Diubah", "dueTrigger", "ON_SIGNING", "amount", "2000000"),
                any(Permission.PROJECT_EDIT), termState));
        c.add(post(term + "/delete", form(), any(Permission.PROJECT_EDIT), termState));
        c.add(post(term + "/generate-invoice", form(), any(Permission.INVOICE_CREATE), termState));

        String invoice = "/invoices/" + f.invoice.getInvoiceNumber();
        Supplier<String> invoiceState = () -> invoiceRepository.findByInvoiceNumber(f.invoice.getInvoiceNumber())
                .map(x -> x.getStatus() + "|" + x.getAmount()).orElse("deleted")
                + "|invoices=" + invoiceRepository.count();
        c.add(post("/invoices/new", form("client.id", f.client.getId().toString(), "invoiceDate", "2026-03-01",
                "dueDate", "2026-03-31", "amount", "1000000"), any(Permission.INVOICE_CREATE), invoiceState));
        c.add(post(invoice, form("client.id", f.client.getId().toString(), "invoiceDate", "2026-03-01",
                "dueDate", "2026-03-31", "amount", "9999999"), any(Permission.INVOICE_EDIT), invoiceState));
        c.add(post(invoice + "/send", form(), any(Permission.INVOICE_SEND), invoiceState));
        c.add(post(invoice + "/mark-paid", form(), any(Permission.INVOICE_MARK_PAID), invoiceState));
        c.add(post(invoice + "/payments", form("paymentDate", "2026-03-10", "amount", "1000",
                "paymentMethod", "TRANSFER"), any(Permission.INVOICE_MARK_PAID), invoiceState));
        c.add(post(invoice + "/cancel", form(), any(Permission.INVOICE_EDIT), invoiceState));
        c.add(post(invoice + "/delete", form(), any(Permission.INVOICE_EDIT), invoiceState));

        String bill = "/bills/" + f.bill.getBillNumber();
        Supplier<String> billState = () -> billRepository.findByBillNumber(f.bill.getBillNumber())
                .map(x -> x.getStatus() + "|" + x.getAmount()).orElse("deleted")
                + "|bills=" + billRepository.count();
        c.add(post("/bills/new", form("vendor.id", f.vendor.getId().toString(), "billDate", "2026-03-01",
                "dueDate", "2026-03-31"), any(Permission.BILL_CREATE), billState));
        c.add(post(bill, form("vendor.id", f.vendor.getId().toString(), "billDate", "2026-03-01",
                "dueDate", "2026-03-31"), any(Permission.BILL_EDIT), billState));
        c.add(post(bill + "/approve", form(), any(Permission.BILL_APPROVE), billState));
        c.add(post(bill + "/mark-paid", form(), any(Permission.BILL_MARK_PAID), billState));
        c.add(post(bill + "/payments", form("paymentDate", "2026-03-10", "amount", "1000",
                "paymentMethod", "TRANSFER"), any(Permission.BILL_MARK_PAID), billState));
        c.add(post(bill + "/cancel", form(), any(Permission.BILL_EDIT), billState));
        c.add(post(bill + "/delete", form(), any(Permission.BILL_EDIT), billState));

        String draft = "/drafts/" + f.draft.getId();
        Supplier<String> draftState = () -> draftRepository.findById(f.draft.getId())
                .map(d -> d.getStatus().name()).orElse("deleted");
        c.add(post(draft + "/approve", form("templateId", f.templateId.toString()), any(Permission.DRAFT_APPROVE),
                draftState));
        c.add(post(draft + "/reject", form("reason", "Tidak valid"), any(Permission.DRAFT_REJECT), draftState));
        c.add(new WriteCase("DELETE", draft, o -> o, any(Permission.DRAFT_REJECT), draftState));
        c.add(post("/drafts/api/" + f.draft.getId() + "/approve",
                o -> o.setData(Map.of("templateId", f.templateId.toString())), any(Permission.DRAFT_APPROVE), draftState));
        c.add(post("/drafts/api/" + f.draft.getId() + "/reject", o -> o.setData(Map.of("reason", "Tidak valid")),
                any(Permission.DRAFT_REJECT), draftState));

        String period = "/fiscal-periods/" + f.fiscalPeriod.getId();
        Supplier<String> periodState = () -> fiscalPeriodRepository.findById(f.fiscalPeriod.getId())
                .orElseThrow().getStatus() + "|months=" + fiscalPeriodRepository.findByYear(FISCAL_YEAR).size()
                + "|generated=" + fiscalPeriodRepository.findByYear(GENERATED_FISCAL_YEAR).size();
        c.add(post("/fiscal-periods/new", form("year", String.valueOf(FISCAL_YEAR), "month", "2"),
                any(Permission.FISCAL_PERIOD_MANAGE), periodState));
        c.add(post("/fiscal-periods/generate-year", form("year", String.valueOf(GENERATED_FISCAL_YEAR)),
                any(Permission.FISCAL_PERIOD_MANAGE), periodState));
        c.add(post(period + "/close-month", form(), any(Permission.FISCAL_PERIOD_MANAGE), periodState));
        c.add(post(period + "/file-tax", form(), any(Permission.FISCAL_PERIOD_MANAGE), periodState));
        c.add(post(period + "/reopen", form("reason", "Koreksi"), any(Permission.FISCAL_PERIOD_MANAGE), periodState));

        String payroll = "/payroll/" + f.payrollRun.getId();
        Supplier<String> payrollState = () -> payrollRunRepository.findById(f.payrollRun.getId())
                .map(r -> r.getStatus() + "|" + r.getTotalGross()).orElse("deleted")
                + "|new=" + payrollRunRepository.existsByPayrollPeriod(NEW_PAYROLL_PERIOD.toString());
        c.add(post("/payroll/create", form("period", NEW_PAYROLL_PERIOD.toString(), "baseSalary", "10000000",
                "jkkRiskClass", "1"), any(Permission.PAYROLL_CREATE), payrollState));
        c.add(post(payroll + "/approve", form(), any(Permission.PAYROLL_APPROVE), payrollState));
        c.add(post(payroll + "/cancel", form("reason", "Batal"), any(Permission.PAYROLL_CANCEL), payrollState));
        c.add(post(payroll + "/post", form(), any(Permission.PAYROLL_POST), payrollState));
        c.add(post(payroll + "/recalculate", form("baseSalary", "20000000"), any(Permission.PAYROLL_CREATE),
                payrollState));
        c.add(post(payroll + "/delete", form(), any(Permission.PAYROLL_CREATE), payrollState));

        String schedule = "/amortization/" + f.schedule.getId();
        Supplier<String> scheduleState = () -> amortizationScheduleRepository.findById(f.schedule.getId())
                .map(s -> s.getName() + "|" + s.getStatus()).orElse("deleted")
                + "|" + amortizationEntryRepository.findByScheduleIdOrderByPeriodNumberAsc(f.schedule.getId()).stream()
                .map(e -> e.getStatus().name()).toList()
                + "|schedules=" + amortizationScheduleRepository.count();
        c.add(post("/amortization", form("code", "WEA-A-" + uid(), "name", "Amortisasi Baru",
                "scheduleType", ScheduleType.PREPAID_EXPENSE.name(),
                "sourceAccountId", f.prepaidAccount.getId().toString(),
                "targetAccountId", f.expenseAccount.getId().toString(), "totalAmount", "1200000",
                "startDate", "2026-01-01", "endDate", "2026-12-31", "frequency", AmortizationFrequency.MONTHLY.name()),
                any(Permission.AMORTIZATION_CREATE), scheduleState));
        c.add(post(schedule, form("name", "Diubah"), any(Permission.AMORTIZATION_EDIT), scheduleState));
        c.add(post(schedule + "/cancel", form(), any(Permission.AMORTIZATION_EDIT), scheduleState));
        c.add(post(schedule + "/delete", form(), any(Permission.AMORTIZATION_EDIT), scheduleState));
        c.add(post(schedule + "/entries/" + f.firstEntry.getId() + "/post", form(), any(Permission.AMORTIZATION_POST),
                scheduleState));
        c.add(post(schedule + "/entries/" + f.firstEntry.getId() + "/skip", form(), any(Permission.AMORTIZATION_POST),
                scheduleState));
        c.add(post(schedule + "/entries/post-all", form(), any(Permission.AMORTIZATION_POST), scheduleState));
        c.add(post("/amortization/batch/process", form(), any(Permission.AMORTIZATION_POST), scheduleState));

        c.add(post("/analysis-reports/" + f.analysisReport.getId() + "/delete", form(),
                any(Permission.ANALYSIS_REPORT_DELETE),
                () -> String.valueOf(analysisReportRepository.findById(f.analysisReport.getId()).isPresent())));

        Supplier<String> adjustmentState = () -> fiscalAdjustmentRepository
                .findByYearOrderByAdjustmentCategoryAscDescriptionAsc(FISCAL_YEAR).stream()
                .map(a -> a.getDescription() + "|" + a.getAmount()).toList().toString();
        c.add(post("/reports/rekonsiliasi-fiskal/adjustments", form("year", String.valueOf(FISCAL_YEAR),
                "description", "Koreksi baru", "adjustmentCategory", FiscalAdjustmentCategory.PERMANENT.name(),
                "adjustmentDirection", FiscalAdjustmentDirection.POSITIVE.name(), "amount", "500000"),
                any(Permission.FISCAL_PERIOD_MANAGE), adjustmentState));
        c.add(post("/reports/rekonsiliasi-fiskal/adjustments/" + f.fiscalAdjustment.getId() + "/delete",
                form("year", String.valueOf(FISCAL_YEAR)), any(Permission.FISCAL_PERIOD_MANAGE), adjustmentState));
        Supplier<String> closingState = () -> String.valueOf(fiscalYearClosingService.hasClosingEntries(CLOSING_YEAR));
        c.add(post("/reports/fiscal-closing/" + CLOSING_YEAR + "/execute", form(),
                any(Permission.FISCAL_PERIOD_MANAGE), closingState));
        c.add(post("/reports/fiscal-closing/" + CLOSING_YEAR + "/reverse", form("reason", "Koreksi"),
                any(Permission.FISCAL_PERIOD_MANAGE), closingState));

        Supplier<String> completionState = () -> completionRepository.findByYear(FISCAL_YEAR).stream()
                .map(x -> x.getId().toString()).sorted().toList().toString();
        c.add(post("/tax-calendar/complete/" + f.openDeadline.getId(), form("year", String.valueOf(FISCAL_YEAR),
                "month", "1", "completedDate", "2046-02-10"), any(Permission.TAX_CALENDAR_MANAGE), completionState));
        c.add(post("/tax-calendar/uncomplete/" + f.completionId, form("year", String.valueOf(FISCAL_YEAR),
                "month", "1"), any(Permission.TAX_CALENDAR_MANAGE), completionState));

        String taxDetails = "/transactions/" + f.transaction.getId() + "/tax-details";
        Predicate<Set<String>> taxDetailWrite = p -> p.contains(Permission.TAX_EXPORT)
                && p.contains(Permission.TRANSACTION_EDIT);
        Supplier<String> taxDetailState = () -> taxDetailRepository
                .findAllByTransactionIdOrderByTaxTypeAsc(f.transaction.getId()).stream()
                .map(d -> d.getTaxType() + "|" + d.getCounterpartyName()).toList().toString();
        c.add(post(taxDetails, form("taxType", TaxType.PPH_25.name(), "counterpartyName", "Baru"), taxDetailWrite,
                taxDetailState));
        c.add(post(taxDetails + "/" + f.taxDetail.getId(), form("taxType", TaxType.PPH_25.name(),
                "counterpartyName", "Diubah"), taxDetailWrite, taxDetailState));
        c.add(new WriteCase("DELETE", taxDetails + "/" + f.taxDetail.getId(), o -> o, taxDetailWrite, taxDetailState));
        c.add(post("/transactions/tax-details/bulk", form("taxType_" + f.transaction.getId(), TaxType.PPH_25.name(),
                "counterpartyName_" + f.transaction.getId(), "Massal"), taxDetailWrite, taxDetailState));

        Supplier<String> documentState = () -> documentRepository.countByTransactionId(f.transaction.getId())
                + "|" + documentRepository.countByJournalEntryId(f.journalEntryId)
                + "|" + documentRepository.countByInvoiceId(f.invoice.getId());
        c.add(post("/documents/transaction/" + f.transaction.getId(), upload(),
                any(Permission.TRANSACTION_CREATE, Permission.TRANSACTION_EDIT), documentState));
        c.add(post("/documents/journal-entry/" + f.journalEntryId, upload(), any(Permission.JOURNAL_EDIT),
                documentState));
        c.add(post("/documents/invoice/" + f.invoice.getId(), upload(), any(Permission.INVOICE_EDIT), documentState));
        c.add(new WriteCase("DELETE", "/documents/" + f.documentId + "?transactionId=" + f.transaction.getId(),
                o -> o, any(Permission.TRANSACTION_EDIT, Permission.JOURNAL_EDIT, Permission.INVOICE_EDIT),
                documentState));

        c.add(post("/settings/export/download", form(), any(Permission.DATA_IMPORT), () -> "read-only"));
        return c;
    }

    private static WriteCase post(String path, UnaryOperator<RequestOptions> body, Predicate<Set<String>> allowed,
                                  Supplier<String> state) {
        return new WriteCase("POST", path, body, allowed, state);
    }

    private static Predicate<Set<String>> any(String... permissions) {
        return granted -> java.util.Arrays.stream(permissions).anyMatch(granted::contains);
    }

    private static UnaryOperator<RequestOptions> form(String... keyValues) {
        return options -> {
            FormData data = FormData.create();
            for (int i = 0; i < keyValues.length; i += 2) {
                data.set(keyValues[i], keyValues[i + 1]);
            }
            return options.setForm(data);
        };
    }

    private static UnaryOperator<RequestOptions> upload() {
        return options -> options.setMultipart(FormData.create()
                .set("file", new FilePayload("wea-" + uid() + ".pdf", "application/pdf", pdfContent())));
    }

    private APIResponse send(WriteCase c) {
        RequestOptions options = c.body().apply(RequestOptions.create()
                .setMethod(c.method())
                .setHeader(csrfHeader, csrfToken)
                .setMaxRedirects(0));
        return page.request().fetch(baseUrl() + c.path(), options);
    }

    private void loginAs(Role role) {
        User user = createUser(role);
        login(user.getUsername(), "password");
        navigateTo(role == Role.EMPLOYEE ? "/self-service/payslips" : "/dashboard");
        waitForPageLoad();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("wea-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("wea-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    // ==================== fixture ====================

    private record Fixture(Client client, Client inactiveClient, Vendor vendor, Vendor inactiveVendor,
                           Project project, ProjectMilestone milestone, ProjectPaymentTerm paymentTerm,
                           Invoice invoice, Bill bill, DraftTransaction draft, UUID templateId,
                           FiscalPeriod fiscalPeriod, PayrollRun payrollRun, AmortizationSchedule schedule,
                           AmortizationEntry firstEntry, ChartOfAccount prepaidAccount, ChartOfAccount expenseAccount,
                           AnalysisReport analysisReport, FiscalAdjustment fiscalAdjustment, TaxDeadline openDeadline,
                           UUID completionId, Transaction transaction, TaxTransactionDetail taxDetail,
                           UUID journalEntryId, UUID documentId) {
    }

    private Fixture fixture() {
        if (fixture != null) {
            return fixture;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));
        try {
            fixture = createFixture();
        } catch (IOException e) {
            throw new IllegalStateException("Fixture document upload failed", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
        return fixture;
    }

    private Fixture createFixture() throws IOException {
        Client client = createClient(true);
        Client inactiveClient = createClient(false);
        Vendor vendor = createVendor(true);
        Vendor inactiveVendor = createVendor(false);

        Project project = new Project();
        project.setCode("WEA-P-" + uid());
        project.setName("Proyek Otorisasi " + uid());
        project.setContractValue(new BigDecimal("100000000"));
        project = projectService.create(project, clientRepository.findByCode(CLIENT_CODE)
                .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + CLIENT_CODE)).getId());
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setSequence(1);
        milestone.setName("Milestone Otorisasi " + uid());
        milestone.setWeightPercent(50);
        milestone = milestoneService.create(project.getId(), milestone);
        ProjectPaymentTerm term = new ProjectPaymentTerm();
        term.setSequence(1);
        term.setName("Termin Otorisasi " + uid());
        term.setDueTrigger(PaymentTrigger.ON_SIGNING);
        term.setAmount(new BigDecimal("5000000"));
        term = paymentTermService.create(project.getId(), term);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("WEA-INV-" + uid());
        invoice.setClient(client);
        invoice.setInvoiceDate(LocalDate.of(2026, 3, 1));
        invoice.setDueDate(LocalDate.of(2026, 3, 31));
        invoice.setAmount(new BigDecimal("2500000"));
        invoice = invoiceService.create(invoice);

        Bill bill = new Bill();
        bill.setBillNumber("WEA-BILL-" + uid());
        bill.setVendor(vendor);
        bill.setBillDate(LocalDate.of(2026, 3, 1));
        bill.setDueDate(LocalDate.of(2026, 3, 31));
        bill.setAmount(new BigDecimal("1500000"));
        bill = billService.create(bill, List.of());

        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.MANUAL);
        draft.setMerchantName("Merchant Otorisasi " + uid());
        draft.setTransactionDate(LocalDate.of(2026, 3, 5));
        draft.setAmount(new BigDecimal("150000"));
        draft.setStatus(DraftTransaction.Status.PENDING);
        draft = draftRepository.save(draft);
        var template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(TEMPLATE_NAME)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + TEMPLATE_NAME));

        FiscalPeriod fiscalPeriod = fiscalPeriodService.create(FISCAL_YEAR, 1);

        PayrollRun payrollRun = payrollService.createPayrollRun(PAYROLL_PERIOD);
        payrollRun = payrollService.calculatePayroll(payrollRun.getId(), new BigDecimal("10000000"), 1);

        ChartOfAccount prepaid = account("1.1.15");
        ChartOfAccount expense = account("5.1.05");
        AmortizationSchedule schedule = new AmortizationSchedule();
        schedule.setCode("WEA-A-" + uid());
        schedule.setName("Sewa Otorisasi " + uid());
        schedule.setScheduleType(ScheduleType.PREPAID_EXPENSE);
        schedule.setSourceAccount(prepaid);
        schedule.setTargetAccount(expense);
        schedule.setTotalAmount(new BigDecimal("600000"));
        schedule.setStartDate(LocalDate.of(2026, 1, 1));
        schedule.setEndDate(LocalDate.of(2026, 6, 30));
        schedule.setFrequency(AmortizationFrequency.MONTHLY);
        schedule.setAutoPost(true);
        schedule.setPostDay(1);
        schedule = amortizationScheduleService.create(schedule);
        AmortizationEntry firstEntry = amortizationEntryRepository
                .findByScheduleIdOrderByPeriodNumberAsc(schedule.getId()).getFirst();

        AnalysisReport report = new AnalysisReport();
        report.setTitle("Analisis Otorisasi " + uid());
        report.setReportType("monthly");
        report = analysisReportRepository.save(report);

        FiscalAdjustment adjustment = new FiscalAdjustment();
        adjustment.setYear(FISCAL_YEAR);
        adjustment.setDescription("Koreksi Otorisasi " + uid());
        adjustment.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
        adjustment.setAdjustmentDirection(FiscalAdjustmentDirection.POSITIVE);
        adjustment.setAmount(new BigDecimal("750000"));
        adjustment = taxReportDetailService.saveAdjustment(adjustment);

        TaxDeadline openDeadline = deadline(TaxDeadlineType.PPH_21_PAYMENT);
        UUID completionId = taxDeadlineService.markAsCompleted(deadline(TaxDeadlineType.PPH_23_PAYMENT).getId(),
                FISCAL_YEAR, 1, LocalDate.of(FISCAL_YEAR, 2, 10), "NTPN-1", null).getId();

        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.of(2026, 3, 5));
        transaction.setAmount(new BigDecimal("1500000"));
        transaction.setDescription("Transaksi otorisasi " + uid());
        transaction.setJournalTemplate(template);
        transaction = transactionService.create(transaction, Map.of());
        TaxTransactionDetail taxDetail = new TaxTransactionDetail();
        taxDetail.setTaxType(TaxType.PPH_25);
        taxDetail.setCounterpartyName("Lawan Otorisasi");
        taxDetail = taxDetailService.save(transaction.getId(), taxDetail);
        UUID documentId = documentService.uploadForTransaction(transaction.getId(),
                new MockMultipartFile("file", "wea-fixture.pdf", "application/pdf", pdfContent()), "admin").getId();
        UUID journalEntryId = journalEntryRepository.findByJournalNumber(JOURNAL_NUMBER)
                .orElseThrow(() -> new IllegalStateException("Seeded journal entry missing: " + JOURNAL_NUMBER))
                .getId();

        return new Fixture(client, inactiveClient, vendor, inactiveVendor, project, milestone, term, invoice, bill,
                draft, template.getId(), fiscalPeriod, payrollRun, schedule, firstEntry, prepaid, expense, report,
                adjustment, openDeadline, completionId, transaction, taxDetail, journalEntryId, documentId);
    }

    private Client createClient(boolean active) {
        Client client = new Client();
        client.setCode("WEA-C-" + uid());
        client.setName("Klien Otorisasi " + uid());
        client = clientService.create(client);
        if (!active) {
            clientService.deactivate(client.getId());
        }
        return client;
    }

    private Vendor createVendor(boolean active) {
        Vendor vendor = new Vendor();
        vendor.setCode("WEA-V-" + uid());
        vendor.setName("Vendor Otorisasi " + uid());
        vendor = vendorService.create(vendor);
        if (!active) {
            vendorService.deactivate(vendor.getId());
        }
        return vendor;
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private TaxDeadline deadline(TaxDeadlineType type) {
        return taxDeadlineRepository.findByDeadlineType(type)
                .orElseThrow(() -> new IllegalStateException("Seeded tax deadline missing: " + type));
    }

    private static byte[] pdfContent() {
        String pdf = "%PDF-1.4\n% " + UUID.randomUUID() + "\n" + """
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj
                << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj
                << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
                endobj
                trailer
                << /Size 4 /Root 1 0 R >>
                %%EOF""";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
