package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.SalaryComponentRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.ApiScope;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * REST API authorization: a bearer token's effective authority is its scopes intersected with the
 * token user's current role permissions, plus the specific permission of each operation. Session
 * (cookie) requests never reach /api/** endpoints, and cookie requests to the web JSON endpoints
 * under /*&#47;api/** need the CSRF token.
 */
@DisplayName("API authorization")
@Import(ServiceTestDataInitializer.class)
class ApiAuthorizationTest extends PlaywrightTestBase {

    private static final String TEMPLATE_NAME = "Setoran Modal";
    private static final int ADJUSTMENT_YEAR = 2041;
    private static final String PAYROLL_PERIOD = "2041-03";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryComponentRepository salaryComponentRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private FixedAssetRepository fixedAssetRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private FiscalAdjustmentRepository fiscalAdjustmentRepository;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private DraftTransactionRepository draftRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private APIRequestContext api;

    @BeforeEach
    void setUpApi() {
        // RateLimitFilter allows 100 /api requests per client IP per minute; give each test its own
        // client address so the endpoint matrix does not exhaust a budget shared across tests.
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl())
                .setExtraHTTPHeaders(Map.of("X-Forwarded-For", "10.77." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256))));
    }

    @AfterEach
    void tearDownApi() {
        api.dispose();
    }

    // ==================== role intersection ====================

    @ParameterizedTest(name = "{0} device-flow token is denied every API write its role lacks")
    @EnumSource(value = Role.class, names = {"AUDITOR", "STAFF", "EMPLOYEE"})
    @DisplayName("Device-flow token is denied every API write its role lacks")
    void deviceTokenDeniedWritesRoleLacks(Role role) {
        User user = createUser(role);
        String token = deviceFlowToken(user);
        Set<String> granted = Permission.getPermissionsForRole(role);

        String marker = uid();
        List<String> checked = new ArrayList<>();
        for (WriteCase c : writeCases(marker)) {
            if (c.allowed().test(granted)) {
                continue;
            }
            APIResponse response = send(token, c);
            assertThat(response.status())
                    .as("%s %s as %s: %s", c.method(), c.path(), role, response.text())
                    .isEqualTo(403);
            checked.add(c.method() + " " + c.path());
        }
        assertThat(checked).as("write cases checked for " + role).isNotEmpty();
        if (role == Role.AUDITOR || role == Role.EMPLOYEE) {
            assertThat(checked).as("every write case denied for " + role).hasSize(writeCases(marker).size());
        }

        assertThat(employeeRepository.existsByEmployeeId("E-" + marker)).isFalse();
        assertThat(salaryComponentRepository.existsByCode("SC-" + marker)).isFalse();
        assertThat(accountRepository.findByAccountCode("9." + marker.substring(0, 6))).isEmpty();
        assertThat(fixedAssetRepository.existsByAssetCode("FA-" + marker)).isFalse();
        assertThat(templateRepository.existsByTemplateNameAndIsCurrentVersionTrue("Template " + marker)).isFalse();
        assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(ADJUSTMENT_YEAR))
                .noneMatch(a -> a.getDescription().contains(marker));
        assertThat(draftRepository.findByCreatedBy(user.getUsername())).isEmpty();
    }

    @Test
    @DisplayName("AUDITOR device-flow token reads every API area")
    void auditorTokenReads() {
        String token = deviceFlowToken(createUser(Role.AUDITOR));
        for (String path : List.of(
                "/api/employees", "/api/salary-components", "/api/payroll",
                "/api/fiscal-adjustments?year=" + ADJUSTMENT_YEAR, "/api/templates",
                "/api/bank-reconciliation/parser-configs", "/api/drafts/templates", "/api/drafts/accounts",
                "/api/accounts", "/api/fixed-assets", "/api/tax-filings", "/api/bills", "/api/analysis/reports")) {
            APIResponse response = api.get(path, bearer(token));
            assertThat(response.status()).as("GET %s: %s", path, response.text()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("ACCOUNTANT token writes what its role holds, not what the scope alone would allow")
    void accountantTokenFollowsRolePermissions() throws Exception {
        String token = deviceFlowToken(createUser(Role.ACCOUNTANT));
        String marker = uid();

        APIResponse adjustment = api.post("/api/fiscal-adjustments", json(token, fiscalAdjustment(marker)));
        assertThat(adjustment.status()).as(adjustment.text()).isEqualTo(201);
        UUID adjustmentId = UUID.fromString(read(adjustment).get("id").asText());
        assertThat(fiscalAdjustmentRepository.findById(adjustmentId)).isPresent();

        APIResponse payroll = api.post("/api/payroll", json(token, Map.of("payrollPeriod", PAYROLL_PERIOD)));
        assertThat(payroll.status()).as(payroll.text()).isEqualTo(201);
        UUID payrollId = UUID.fromString(read(payroll).get("id").asText());
        assertThat(api.delete("/api/payroll/" + payrollId, bearer(token)).status()).isEqualTo(204);
        assertThat(payrollRunRepository.findByPayrollPeriod(PAYROLL_PERIOD)).isEmpty();

        // payroll:write is granted (PAYROLL_CREATE) but employee master data needs EMPLOYEE_CREATE
        APIResponse employee = api.post("/api/employees", json(token, employee(marker)));
        assertThat(employee.status()).as(employee.text()).isEqualTo(403);
        assertThat(employeeRepository.existsByEmployeeId("E-" + marker)).isFalse();

        // transactions:post is granted but purging needs TRANSACTION_PURGE
        assertThat(api.delete("/api/transactions/purge-voided", bearer(token)).status()).isEqualTo(403);

        // bank-recon:write is granted (MATCH) but parser configs need BANK_RECONCILIATION_CONFIG
        assertThat(api.post("/api/bank-reconciliation/parser-configs", json(token, parserConfig(marker))).status())
                .isEqualTo(403);
        assertThat(api.get("/api/bank-reconciliation/parser-configs", bearer(token)).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Role change after issuance takes effect on the next request; deactivation revokes the token")
    void roleChangeAppliesToIssuedToken() throws Exception {
        User user = createUser(Role.ACCOUNTANT);
        String token = deviceFlowToken(user);

        APIResponse created = api.post("/api/fiscal-adjustments", json(token, fiscalAdjustment(uid())));
        assertThat(created.status()).as(created.text()).isEqualTo(201);

        User reloaded = userRepository.findByUsername(user.getUsername()).orElseThrow();
        reloaded.setRoles(Set.of(Role.AUDITOR), "test");
        userRepository.save(reloaded);

        String marker = uid();
        APIResponse denied = api.post("/api/fiscal-adjustments", json(token, fiscalAdjustment(marker)));
        assertThat(denied.status()).as(denied.text()).isEqualTo(403);
        assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(ADJUSTMENT_YEAR))
                .noneMatch(a -> a.getDescription().contains(marker));
        assertThat(api.get("/api/fiscal-adjustments?year=" + ADJUSTMENT_YEAR, bearer(token)).status()).isEqualTo(200);

        reloaded = userRepository.findByUsername(user.getUsername()).orElseThrow();
        reloaded.setActive(false);
        userRepository.save(reloaded);
        assertThat(api.get("/api/fiscal-adjustments?year=" + ADJUSTMENT_YEAR, bearer(token)).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Device-flow token carries every registered scope")
    void deviceTokenCarriesAllScopes() {
        User user = createUser(Role.AUDITOR);
        deviceFlowToken(user);
        String scopes = deviceTokenRepository.findActiveByUser(user).getFirst().getScopes();
        assertThat(List.of(scopes.split(","))).containsExactlyInAnyOrderElementsOf(ApiScope.allScopes());
    }

    // ==================== scopes ====================

    @Test
    @DisplayName("tax-export:read token cannot write payroll, employees, salary components or fiscal adjustments")
    void taxExportReadTokenCannotWrite() {
        String token = serviceToken("tax-export:read");
        String marker = uid();

        assertThat(api.get("/api/payroll", bearer(token)).status()).isEqualTo(200);
        assertThat(api.get("/api/employees", bearer(token)).status()).isEqualTo(200);
        assertThat(api.get("/api/fiscal-adjustments?year=" + ADJUSTMENT_YEAR, bearer(token)).status()).isEqualTo(200);

        assertThat(api.post("/api/payroll", json(token, Map.of("payrollPeriod", PAYROLL_PERIOD))).status())
                .isEqualTo(403);
        assertThat(api.post("/api/employees", json(token, employee(marker))).status()).isEqualTo(403);
        assertThat(api.post("/api/salary-components", json(token, salaryComponent(marker))).status()).isEqualTo(403);
        assertThat(api.post("/api/fiscal-adjustments", json(token, fiscalAdjustment(marker))).status()).isEqualTo(403);
        assertThat(api.post("/api/fiscal-adjustments/loss-carryforward",
                json(token, Map.of("originYear", ADJUSTMENT_YEAR, "originalAmount", 1000))).status()).isEqualTo(403);

        assertThat(payrollRunRepository.findByPayrollPeriod(PAYROLL_PERIOD)).isEmpty();
        assertThat(employeeRepository.existsByEmployeeId("E-" + marker)).isFalse();
        assertThat(salaryComponentRepository.existsByCode("SC-" + marker)).isFalse();
    }

    @Test
    @DisplayName("Draft, bank reconciliation and template endpoints require their own scopes")
    void draftBankReconTemplateEndpointsRequireScopes() {
        String token = serviceToken("transactions:post");
        String marker = uid();

        assertThat(api.post("/api/drafts/from-text", json(token, draftFromText(marker))).status()).isEqualTo(403);
        assertThat(api.get("/api/drafts/templates", bearer(token)).status()).isEqualTo(403);
        assertThat(api.post("/api/drafts/" + UUID.randomUUID() + "/reject",
                json(token, Map.of("reason", "x"))).status()).isEqualTo(403);
        assertThat(api.get("/api/bank-reconciliation/parser-configs", bearer(token)).status()).isEqualTo(403);
        assertThat(api.post("/api/bank-reconciliation/parser-configs", json(token, parserConfig(marker))).status())
                .isEqualTo(403);
        assertThat(api.get("/api/templates", bearer(token)).status()).isEqualTo(403);
        assertThat(api.post("/api/templates", json(token, template(marker))).status()).isEqualTo(403);
        assertThat(draftRepository.findByCreatedBy("admin")).noneMatch(d -> marker.equals(d.getMerchantName()));

        String scoped = serviceToken("drafts:create,drafts:read,templates:read,bank-recon:read");
        assertThat(api.get("/api/drafts/templates", bearer(scoped)).status()).isEqualTo(200);
        assertThat(api.get("/api/templates", bearer(scoped)).status()).isEqualTo(200);
        assertThat(api.get("/api/bank-reconciliation/parser-configs", bearer(scoped)).status()).isEqualTo(200);
        APIResponse draft = api.post("/api/drafts/from-text", json(scoped, draftFromText(marker)));
        assertThat(draft.status()).as(draft.text()).isEqualTo(201);
        assertThat(api.post("/api/templates", json(scoped, template(marker))).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("Invalid bearer token is rejected even when a session cookie is present")
    void invalidBearerIgnoresSession() {
        loginAsAdmin();
        APIResponse response = page.request().get(baseUrl() + "/api/templates",
                RequestOptions.create().setHeader("Authorization", "Bearer " + "0".repeat(64)));
        assertThat(response.status()).isEqualTo(401);
    }

    // ==================== session requests ====================

    @Test
    @DisplayName("Browser session cannot call /api/** endpoints, with or without CSRF token")
    void sessionCannotCallApi() {
        loginAsAdmin();
        String csrf = csrfToken();
        String marker = uid();

        APIResponse withCsrf = page.request().post(baseUrl() + "/api/fiscal-adjustments",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setData(fiscalAdjustment(marker)));
        assertThat(withCsrf.status()).isEqualTo(403);
        APIResponse withoutCsrf = page.request().post(baseUrl() + "/api/fiscal-adjustments",
                RequestOptions.create().setData(fiscalAdjustment(marker)));
        assertThat(withoutCsrf.status()).isEqualTo(403);
        assertThat(page.request().get(baseUrl() + "/api/templates").status()).isEqualTo(403);

        assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(ADJUSTMENT_YEAR))
                .noneMatch(a -> a.getDescription().contains(marker));
    }

    @Test
    @DisplayName("Session POST to web JSON endpoints under /*/api/** requires the CSRF token")
    void webJsonEndpointsRequireCsrf() {
        DraftTransaction draft = createPendingDraft();
        Transaction transaction = createDraftTransaction();

        loginAsAdmin();
        String csrf = csrfToken();

        APIResponse rejectWithout = page.request().post(baseUrl() + "/drafts/api/" + draft.getId() + "/reject",
                RequestOptions.create().setData(Map.of("reason", "tanpa csrf")));
        assertThat(rejectWithout.status()).isEqualTo(403);
        assertThat(draftRepository.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(DraftTransaction.Status.PENDING);

        APIResponse deleteWithout = page.request().delete(baseUrl() + "/transactions/api/" + transaction.getId());
        assertThat(deleteWithout.status()).isEqualTo(403);
        assertThat(transactionRepository.findById(transaction.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.DRAFT);

        APIResponse rejectWith = page.request().post(baseUrl() + "/drafts/api/" + draft.getId() + "/reject",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setData(Map.of("reason", "dengan csrf")));
        assertThat(rejectWith.status()).as(rejectWith.text()).isEqualTo(200);
        assertThat(draftRepository.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(DraftTransaction.Status.REJECTED);

        APIResponse deleteWith = page.request().delete(baseUrl() + "/transactions/api/" + transaction.getId(),
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf));
        assertThat(deleteWith.status()).as(deleteWith.text()).isEqualTo(200);
        assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
    }

    // ==================== write cases ====================

    private record WriteCase(String method, String path, RequestOptions body, Predicate<Set<String>> allowed) {}

    private List<WriteCase> writeCases(String m) {
        String r = "/" + UUID.randomUUID();
        String r2 = "/" + UUID.randomUUID();
        UUID id = UUID.randomUUID();
        List<WriteCase> c = new ArrayList<>();

        // transactions:post
        c.add(new WriteCase("POST", "/api/transactions", data(Map.of("templateId", id, "merchant", "M " + m,
                "amount", 1000, "transactionDate", "2026-03-05", "description", "D " + m)),
                all(Permission.TRANSACTION_CREATE, Permission.TRANSACTION_POST)));
        c.add(new WriteCase("POST", "/api/transactions/journal-entry", data(Map.of("transactionDate", "2026-03-05",
                "description", "D " + m, "lines", List.of(
                        Map.of("accountId", id, "debit", 1000, "credit", 0),
                        Map.of("accountId", UUID.randomUUID(), "debit", 0, "credit", 1000)))),
                all(Permission.TRANSACTION_CREATE)));
        c.add(new WriteCase("PUT", "/api/transactions" + r, data(Map.of("description", "D " + m)),
                all(Permission.TRANSACTION_EDIT)));
        c.add(new WriteCase("DELETE", "/api/transactions" + r, none(), all(Permission.TRANSACTION_DELETE)));
        c.add(new WriteCase("POST", "/api/transactions" + r + "/post", none(), all(Permission.TRANSACTION_POST)));
        c.add(new WriteCase("POST", "/api/transactions" + r + "/void", data(Map.of("reason", "INPUT_ERROR")),
                all(Permission.TRANSACTION_VOID)));
        c.add(new WriteCase("POST", "/api/transactions/bulk-post", data(Map.of("transactionIds", List.of(id))),
                all(Permission.TRANSACTION_POST)));
        c.add(new WriteCase("DELETE", "/api/transactions/purge-voided", none(), all(Permission.TRANSACTION_PURGE)));
        c.add(new WriteCase("POST", "/api/transactions" + r + "/documents", pdf(),
                any(Permission.TRANSACTION_CREATE, Permission.TRANSACTION_EDIT)));
        c.add(new WriteCase("DELETE", "/api/documents" + r, none(),
                any(Permission.TRANSACTION_EDIT, Permission.JOURNAL_EDIT, Permission.INVOICE_EDIT)));
        c.add(new WriteCase("POST", "/api/transactions" + r + "/tax-details", data(taxDetail()),
                all(Permission.TAX_EXPORT, Permission.TRANSACTION_EDIT)));
        c.add(new WriteCase("PUT", "/api/transactions" + r + "/tax-details" + r2, data(taxDetail()),
                all(Permission.TAX_EXPORT, Permission.TRANSACTION_EDIT)));
        c.add(new WriteCase("DELETE", "/api/transactions" + r + "/tax-details" + r2, none(),
                all(Permission.TAX_EXPORT, Permission.TRANSACTION_EDIT)));
        c.add(new WriteCase("POST", "/api/tax-details/bulk",
                data(Map.of("items", List.of(Map.of("transactionId", id, "detail", taxDetail())))),
                all(Permission.TAX_EXPORT, Permission.TRANSACTION_EDIT)));

        // drafts
        c.add(new WriteCase("POST", "/api/drafts", data(Map.of("templateId", id, "description", "D " + m,
                "amount", 1000, "transactionDate", "2026-03-05")), all(Permission.TRANSACTION_CREATE)));
        c.add(new WriteCase("POST", "/api/drafts/from-receipt", data(Map.of("merchant", m, "amount", 1000,
                "transactionDate", "2026-03-05")), all(Permission.TRANSACTION_CREATE)));
        c.add(new WriteCase("POST", "/api/drafts/from-text", data(draftFromText(m)), all(Permission.TRANSACTION_CREATE)));
        c.add(new WriteCase("PATCH", "/api/drafts" + r, data(Map.of("description", "D " + m)),
                all(Permission.TRANSACTION_CREATE)));
        c.add(new WriteCase("POST", "/api/drafts" + r + "/approve", data(Map.of("templateId", id,
                "description", "D " + m, "amount", 1000)), all(Permission.DRAFT_APPROVE)));
        c.add(new WriteCase("POST", "/api/drafts" + r + "/reject", data(Map.of("reason", "R " + m)),
                all(Permission.DRAFT_REJECT)));

        // templates
        c.add(new WriteCase("POST", "/api/templates", data(template(m)), all(Permission.TEMPLATE_CREATE)));
        c.add(new WriteCase("PUT", "/api/templates" + r, data(template(m)), all(Permission.TEMPLATE_EDIT)));
        c.add(new WriteCase("DELETE", "/api/templates" + r, none(), all(Permission.TEMPLATE_DELETE)));

        // bank reconciliation
        String recon = "/api/bank-reconciliation/reconciliations" + r;
        c.add(new WriteCase("POST", "/api/bank-reconciliation/parser-configs", data(parserConfig(m)),
                all(Permission.BANK_RECONCILIATION_CONFIG)));
        c.add(new WriteCase("POST", "/api/bank-reconciliation/statements/import",
                RequestOptions.create().setMultipart(FormData.create()
                        .set("bankAccountId", id.toString()).set("parserConfigId", id.toString())
                        .set("periodStart", "2026-03-01").set("periodEnd", "2026-03-31")
                        .set("file", new FilePayload("stmt.csv", "text/csv",
                                "date,desc,debit,credit\n".getBytes(StandardCharsets.UTF_8)))),
                all(Permission.BANK_RECONCILIATION_IMPORT)));
        c.add(new WriteCase("POST", "/api/bank-reconciliation/reconciliations",
                data(Map.of("bankStatementId", id)), all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("POST", recon + "/auto-match", none(), all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("POST", recon + "/match", data(Map.of("statementItemId", id, "transactionId", id)),
                all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("POST", recon + "/mark-bank-only", data(Map.of("statementItemId", id)),
                all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("POST", recon + "/mark-book-only", data(Map.of("transactionId", id)),
                all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("DELETE", recon + "/items" + r2, none(), all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("POST", recon + "/create-transaction",
                data(Map.of("statementItemId", id, "templateId", id)), all(Permission.BANK_RECONCILIATION_MATCH)));
        c.add(new WriteCase("POST", recon + "/complete", none(), all(Permission.BANK_RECONCILIATION_COMPLETE)));

        // payroll master data and runs
        c.add(new WriteCase("POST", "/api/employees", data(employee(m)), all(Permission.EMPLOYEE_CREATE)));
        c.add(new WriteCase("PUT", "/api/employees" + r, data(employee(m)), all(Permission.EMPLOYEE_EDIT)));
        Map<String, Object> assignment = Map.of("salaryComponentId", id, "amount", 1000, "effectiveDate", "2026-03-01");
        c.add(new WriteCase("POST", "/api/employees" + r + "/salary-components", data(assignment),
                all(Permission.EMPLOYEE_EDIT)));
        c.add(new WriteCase("PUT", "/api/employees" + r + "/salary-components" + r2, data(assignment),
                all(Permission.EMPLOYEE_EDIT)));
        c.add(new WriteCase("POST", "/api/salary-components", data(salaryComponent(m)),
                all(Permission.SALARY_COMPONENT_MANAGE)));
        c.add(new WriteCase("PUT", "/api/salary-components" + r, data(salaryComponent(m)),
                all(Permission.SALARY_COMPONENT_MANAGE)));
        c.add(new WriteCase("DELETE", "/api/salary-components" + r, none(), all(Permission.SALARY_COMPONENT_MANAGE)));
        c.add(new WriteCase("POST", "/api/payroll", data(Map.of("payrollPeriod", PAYROLL_PERIOD)),
                all(Permission.PAYROLL_CREATE)));
        c.add(new WriteCase("POST", "/api/payroll" + r + "/calculate", data(Map.of("baseSalary", 5000000)),
                all(Permission.PAYROLL_CREATE)));
        c.add(new WriteCase("POST", "/api/payroll" + r + "/approve", none(), all(Permission.PAYROLL_APPROVE)));
        c.add(new WriteCase("POST", "/api/payroll" + r + "/post", none(), all(Permission.PAYROLL_POST)));
        c.add(new WriteCase("DELETE", "/api/payroll" + r, none(), all(Permission.PAYROLL_CREATE)));
        c.add(new WriteCase("POST", "/api/payroll/schedule", data(Map.of("dayOfMonth", 25, "baseSalary", 5000000,
                "jkkRiskClass", 1, "autoCalculate", true)), all(Permission.PAYROLL_CREATE, Permission.PAYROLL_APPROVE)));
        c.add(new WriteCase("DELETE", "/api/payroll/schedule", none(),
                all(Permission.PAYROLL_CREATE, Permission.PAYROLL_APPROVE)));

        // fiscal adjustments
        c.add(new WriteCase("POST", "/api/fiscal-adjustments", data(fiscalAdjustment(m)),
                all(Permission.FISCAL_PERIOD_MANAGE)));
        c.add(new WriteCase("PUT", "/api/fiscal-adjustments" + r, data(fiscalAdjustment(m)),
                all(Permission.FISCAL_PERIOD_MANAGE)));
        c.add(new WriteCase("DELETE", "/api/fiscal-adjustments" + r, none(), all(Permission.FISCAL_PERIOD_MANAGE)));
        c.add(new WriteCase("POST", "/api/fiscal-adjustments/loss-carryforward",
                data(Map.of("originYear", ADJUSTMENT_YEAR, "originalAmount", 1000)), all(Permission.FISCAL_PERIOD_MANAGE)));
        c.add(new WriteCase("DELETE", "/api/fiscal-adjustments/loss-carryforward" + r, none(),
                all(Permission.FISCAL_PERIOD_MANAGE)));

        // chart of accounts
        Map<String, Object> account = Map.of("accountCode", "9." + m.substring(0, 6), "accountName", "Akun " + m,
                "accountType", "ASSET");
        c.add(new WriteCase("POST", "/api/accounts", data(account), all(Permission.ACCOUNT_CREATE)));
        c.add(new WriteCase("PUT", "/api/accounts" + r, data(account), all(Permission.ACCOUNT_EDIT)));
        c.add(new WriteCase("DELETE", "/api/accounts" + r, none(), all(Permission.ACCOUNT_DELETE)));

        // fixed assets
        Map<String, Object> asset = Map.of("assetCode", "FA-" + m, "name", "Aset " + m,
                "purchaseDate", "2026-03-01", "purchaseCost", 1000000);
        c.add(new WriteCase("POST", "/api/fixed-assets", data(asset), all(Permission.ASSET_CREATE)));
        c.add(new WriteCase("PUT", "/api/fixed-assets" + r, data(asset), all(Permission.ASSET_EDIT)));
        c.add(new WriteCase("DELETE", "/api/fixed-assets" + r, none(), all(Permission.ASSET_DELETE)));
        c.add(new WriteCase("POST", "/api/fixed-assets/depreciation/generate?period=2041-01", none(),
                all(Permission.ASSET_DEPRECIATE)));
        c.add(new WriteCase("POST", "/api/fixed-assets/depreciation" + r + "/post", none(),
                all(Permission.ASSET_DEPRECIATE)));
        c.add(new WriteCase("POST", "/api/fixed-assets/depreciation" + r + "/skip", none(),
                all(Permission.ASSET_DEPRECIATE)));
        c.add(new WriteCase("POST", "/api/fixed-assets/depreciation/post-all?period=2041-01", none(),
                all(Permission.ASSET_DEPRECIATE)));

        // bills
        c.add(new WriteCase("POST", "/api/bills", data(Map.of("vendorName", "Vendor " + m, "billDate", "2026-03-01",
                "dueDate", "2026-03-31", "lines", List.of(Map.of("description", "Baris", "unitPrice", 1000)))),
                all(Permission.BILL_CREATE)));
        c.add(new WriteCase("POST", "/api/bills" + r + "/approve", none(), all(Permission.BILL_APPROVE)));
        c.add(new WriteCase("POST", "/api/bills" + r + "/mark-paid", none(), all(Permission.BILL_MARK_PAID)));

        // tax filings
        Map<String, Object> filing = Map.of("taxType", "PPN", "period", "2041-01", "status", "NOT_FILED");
        c.add(new WriteCase("POST", "/api/tax-filings", data(filing), all(Permission.TAX_CALENDAR_MANAGE)));
        c.add(new WriteCase("PUT", "/api/tax-filings" + r, data(filing), all(Permission.TAX_CALENDAR_MANAGE)));
        c.add(new WriteCase("DELETE", "/api/tax-filings" + r, none(), all(Permission.TAX_CALENDAR_MANAGE)));
        c.add(new WriteCase("POST", "/api/tax-filings" + r + "/documents",
                RequestOptions.create().setMultipart(FormData.create().set("docType", "SPT")
                        .set("file", new FilePayload("spt.pdf", "application/pdf", pdfContent()))),
                all(Permission.TAX_CALENDAR_MANAGE)));
        c.add(new WriteCase("DELETE", "/api/tax-filings" + r + "/documents" + r2, none(),
                all(Permission.TAX_CALENDAR_MANAGE)));

        // analysis and import
        c.add(new WriteCase("POST", "/api/analysis/reports", data(Map.of("title", "Laporan " + m,
                "reportType", "monthly")), all(Permission.ANALYSIS_REPORT_CREATE)));
        c.add(new WriteCase("POST", "/api/data-import", RequestOptions.create().setMultipart(FormData.create()
                .set("file", new FilePayload("import.zip", "application/zip", new byte[] {0x50, 0x4B, 0x05, 0x06}))),
                all(Permission.DATA_IMPORT)));
        return c;
    }

    private static Predicate<Set<String>> all(String... permissions) {
        return granted -> java.util.Arrays.stream(permissions).allMatch(granted::contains);
    }

    private static Predicate<Set<String>> any(String... permissions) {
        return granted -> java.util.Arrays.stream(permissions).anyMatch(granted::contains);
    }

    private static RequestOptions data(Object body) {
        return RequestOptions.create().setHeader("Content-Type", "application/json").setData(body);
    }

    private static RequestOptions none() {
        return RequestOptions.create();
    }

    private static RequestOptions pdf() {
        return RequestOptions.create().setMultipart(FormData.create()
                .set("file", new FilePayload("doc.pdf", "application/pdf", pdfContent())));
    }

    private APIResponse send(String token, WriteCase c) {
        return api.fetch(c.path(), c.body().setMethod(c.method()).setHeader("Authorization", "Bearer " + token));
    }

    // ==================== bodies ====================

    private static Map<String, Object> fiscalAdjustment(String marker) {
        return Map.of("year", ADJUSTMENT_YEAR, "description", "Koreksi " + marker,
                "adjustmentCategory", "PERMANENT", "adjustmentDirection", "POSITIVE", "amount", 750000);
    }

    private static Map<String, Object> employee(String marker) {
        return Map.of("employeeId", "E-" + marker, "name", "Karyawan " + marker, "hireDate", "2026-01-01");
    }

    private static Map<String, Object> salaryComponent(String marker) {
        return Map.of("code", "SC-" + marker, "name", "Komponen " + marker, "componentType", "EARNING");
    }

    private static Map<String, Object> parserConfig(String marker) {
        return Map.of("bankType", "BCA", "configName", "Parser " + marker, "dateColumn", 0,
                "descriptionColumn", 1, "dateFormat", "dd/MM/yyyy");
    }

    private static Map<String, Object> draftFromText(String marker) {
        return Map.of("merchant", marker, "amount", 1000, "transactionDate", "2026-03-05");
    }

    private static Map<String, Object> taxDetail() {
        return Map.of("taxType", "PPN_KELUARAN", "counterpartyName", "Lawan");
    }

    private static Map<String, Object> template(String marker) {
        return Map.of("templateName", "Template " + marker, "category", "EXPENSE", "cashFlowCategory", "OPERATING",
                "lines", List.of(
                        Map.of("lineOrder", 1, "position", "DEBIT", "formula", "amount"),
                        Map.of("lineOrder", 2, "position", "CREDIT", "formula", "amount")));
    }

    private static byte[] pdfContent() {
        return "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }

    // ==================== fixtures ====================

    private DraftTransaction createPendingDraft() {
        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.MANUAL);
        draft.setMerchantName("Merchant CSRF " + uid());
        draft.setTransactionDate(LocalDate.of(2026, 3, 5));
        draft.setAmount(new BigDecimal("150000"));
        draft.setStatus(DraftTransaction.Status.PENDING);
        return draftRepository.save(draft);
    }

    private Transaction createDraftTransaction() {
        JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(TEMPLATE_NAME)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + TEMPLATE_NAME));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));
        try {
            Transaction transaction = new Transaction();
            transaction.setTransactionDate(LocalDate.of(2026, 3, 5));
            transaction.setAmount(new BigDecimal("1500000"));
            transaction.setDescription("Transaksi CSRF " + uid());
            transaction.setJournalTemplate(template);
            return transactionService.create(transaction, Map.of());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("apiauthz-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("apiauthz-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private String serviceToken(String scopes) {
        User admin = userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Seeded user missing: admin"));
        return deviceAuthService.createServiceToken(admin, "apiauthz-" + uid(), "apiauthz", scopes, 60).getTokenHash();
    }

    private String deviceFlowToken(User user) {
        APIResponse codeResponse = api.post("/api/device/code", data(Map.of("clientId", "apiauthz-test")));
        assertThat(codeResponse.status()).as(codeResponse.text()).isEqualTo(200);
        JsonNode code = read(codeResponse);

        login(user.getUsername(), "password");
        navigateTo("/device?code=" + code.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("API authz " + user.getUsername());
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> token = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            APIResponse tokenResponse = api.post("/api/device/token",
                    data(Map.of("deviceCode", code.get("deviceCode").asText())));
            if (tokenResponse.ok()) {
                token.set(read(tokenResponse).get("accessToken").asText());
                return true;
            }
            return false;
        });
        return token.get();
    }

    private String csrfToken() {
        navigateTo("/dashboard");
        waitForPageLoad();
        return page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private static RequestOptions bearer(String token) {
        return RequestOptions.create().setHeader("Authorization", "Bearer " + token);
    }

    private static RequestOptions json(String token, Object body) {
        return data(body).setHeader("Authorization", "Bearer " + token);
    }

    private JsonNode read(APIResponse response) {
        try {
            return objectMapper.readTree(response.text());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON response: " + response.text(), e);
        }
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
