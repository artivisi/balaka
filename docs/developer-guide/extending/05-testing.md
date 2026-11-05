# Testing

## Test Pyramid

| Level | Tool | Data Source | Location | Count |
|-------|------|-------------|----------|-------|
| Unit | JUnit 5 + Mockito | Mocks | `src/test/java/.../unit/` | Fast |
| Integration | Testcontainers (PostgreSQL) | V800-V915 migrations | `src/test/java/.../integration/` | ~30s startup |
| Functional (E2E) | Playwright | Industry seed initializers | `src/test/java/.../functional/` | ~60-90 min total |
| Security (SAST) | SpotBugs | Source code | N/A | ~1 min |
| Security (DAST) | OWASP ZAP | Running app | `src/test/java/.../functional/ZapDastTest.java` | ~5-15 min |

## Running Tests

### Full Test Suite

The full test suite takes 60-90 minutes. Always run in background with log capture. Never run multiple instances simultaneously.

```bash
# Background execution with log capture
nohup ./mvnw test > target/test-output.log 2>&1 &

# Or foreground with tee
./mvnw test 2>&1 | tee target/test-output.log
```

### Specific Test

```bash
./mvnw test -Dtest=TransactionTest
```

### Visible Browser (Debugging)

```bash
./mvnw test -Dtest=TransactionTest -Dplaywright.headless=false -Dplaywright.slowmo=100
```

### SpotBugs Only

```bash
./mvnw spotbugs:check
# Results: target/spotbugsXml.xml
```

### DAST Only

```bash
# Full scan (active + passive, ~15 min)
./mvnw test -Dtest=ZapDastTest

# Quick scan (passive only, ~1 min)
./mvnw test -Dtest=ZapDastTest -Ddast.quick=true

# Results: target/security-reports/zap-*.html
```

## Test Data Strategy

### Integration Tests

Use Flyway test migrations in `src/test/resources/db/test/integration/`:

| Migration | Content |
|-----------|---------|
| `V800__base_test_data.sql` | Base test data (users, roles) |
| `V900-V915` | Domain-specific data (templates, transactions, payroll, drafts, etc.) |

These migrations run automatically when Testcontainers spins up the PostgreSQL instance.

### Functional Tests

Functional tests do NOT use migration-based test data. Instead, they use `@TestConfiguration` initializers that load industry seed packs programmatically:

```java
@TestConfiguration
@Profile("functional")
public class ItServiceTestDataInitializer {

    @Autowired
    private DataImportService dataImportService;

    @PostConstruct
    public void init() {
        dataImportService.importFromSeedPack("it-service");
    }
}
```

Test classes import the relevant initializer:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("functional")
@Import(ItServiceTestDataInitializer.class)
class TransactionTest {
    // ...
}
```

Industry-specific tests have their own subdirectories:
- `functional/seller/` -- online seller tests
- `functional/manufacturing/` -- coffee shop tests
- `functional/campus/` -- campus tests

### Rules: Tests Must Verify the Feature

Established by the 2026-09 test-suite audit (`docs/test-suite-audit-2026-09.md`), which rewrote ~80 classes that asserted nothing. Every test must fail when the feature it names is broken.

Forbidden patterns:
- **Skip guards**: `repo.findAll().stream().findFirst()` + `if (x.isPresent()) { ... }`, `if (x.isEmpty()) return;`, `if (locator.count() > 0) { ... }`. A missing precondition must fail loudly (`orElseThrow(() -> new IllegalStateException("Seeded X missing: ..."))`).
- **Weak-only assertions**: `assertThat(page.locator("body")).isVisible()`, page title/heading visible, `isNotNull`, `isNotEmpty`, count `> 0`. Assert exact values instead: specific rows by natural key with exact amounts/texts, persisted state via repositories, HTTP status + headers + body.
- **Data resets in a shared DB**: no `deleteAll()`, `TRUNCATE`, `DELETE FROM`. Test classes with the same initializer share one Spring context and one database.
- **Silent no-ops in page objects/helpers**: a picker that finds no result must throw (see `functional/page/AccountPicker`).

Required patterns:
- **Resolve seed data by natural key** (transaction number, account/client/project code, template name). `DataImportService` regenerates UUIDs on import, so CSV UUIDs do not survive. Integration tests (V800/V9xx migrations) may use fixed UUIDs.
- **Create what you mutate**: tests that create, edit, deactivate or delete use records they created with unique codes (`prefix + UUID`). Never mutate seeded records; other classes assert them exactly.
- **Singleton config** (company config, tax profile, default bank account): snapshot and restore in `@AfterEach` (`CompanyConfigSnapshot`).
- **Shared tables**: never assert an exact count or "first row" over a table other classes write to. Scope counts to your own data, compare against a repository query, or filter/search by code first (list pages are paginated).
- **Accumulating reports** (trial balance, balance sheet, ledger balance): assert the delta between two dates, or post into a reserved year and assert exact figures for that period.
- **Downloads**: fetch with `page.request()` (shares the session cookie) and parse the content; `page.navigate()` fails with "Download is starting" on `Content-Disposition: attachment`.
- **Session calls to `/*/api/**`** need the `X-CSRF-TOKEN` header; browser sessions cannot call `/api/**` (bearer token only).
- **Prove the test is not vacuous**: run it against the unfixed code (or a deliberately wrong expected value) and see it fail at least once.
- **HTMX + Alpine timing**: wait for the specific state (element value, `htmx-settling` class removed, `waitForResponse`), never `waitForTimeout`.

### Reserved Test Years

Period-scoped data (transactions, payroll runs, fiscal periods, schedules) goes into a year no other class uses. Add your class here when you reserve one.

| Year / period | Owner |
|---|---|
| 2001 | PurgeVoidedTransactionsApiTest (bounds < 2001-11-10) |
| 2002-01..02 | AmortizationControllerFunctionalTest batch |
| 2003-06-15 | ReportApiPersistenceTest (one project cost) |
| 2004 / 2005 / 2006 | TaxDetailApiTest / TaxExportApiTest / TaxDetailAutoPopulateTest (API rejects future dates) |
| 2007 | DraftAndTransactionCorrectionApiTest |
| 2024-01 (seller, coffee-shop ctx), 2024-07 (campus ctx) | Seed data asserted exactly — never post into these months in those contexts |
| 2028 | PayrollApiTest |
| 2029 | PayrollSchedulerTest |
| 2113-2121 (integration DB) | BenefitInKindPayrollTest |
| 2122-2129 (integration DB) | FiscalAdjustmentDraftServiceTest |
| 2130 | FiscalTreatmentFunctionalTest |
| 2131 | FiscalDraftAndBenefitApiTest |
| 2132-2141 (integration DB) | WithholdingPaymentServiceTest |
| 2142, 2008-01 | WithholdingApiTest (2008: journal-entry API rejects future dates) |
| 2143 | WithholdingFunctionalTest |
| 2144-2152 (integration DB), 2098 (tax_filings stops at 2100) | SalesTaxChainServiceTest |
| 2153 | SalesTaxChainApiTest |
| 2154 | SalesTaxChainFunctionalTest |
| 2155-2162 (integration DB) | PayrollBonusThrTest |
| 2163-2171, 2009-01..03 (integration DB; 2009: the alert needs an ended period) | PayrollReconciliationServiceTest |
| 2011, 2013 (past: the journal-entry API rejects future dates; payroll list sorts newest first, so later years push PayrollControllerFunctionalTest off page 1) | PayrollReconciliationApiTest |
| 2014 (past, same reason) | PayrollBonusFunctionalTest |
| 2172-2179 (integration DB) | AssetDisposalServiceTest |
| 2180-2185 (integration DB) | AssetVerificationServiceTest |
| 2185 | AssetRegisterApiTest |
| 2186 | FixedAssetRegisterFunctionalTest |
| 2187-2190, 2010-03 (integration DB; 2010: the alert evaluates against today) | EvidenceServiceTest |
| 2191, 2192, 2015 (past: the archive refuses a month that has not ended) | EvidenceFunctionalTest |
| 2193 | EvidenceApiTest |
| 2194-2198, 2018-2019, 2012 (integration DB; 2018-2019: tax filings stop at 2100; 2012: the alert evaluates against today) | CloseCheckServiceTest |
| 2016 | CloseCheckFunctionalTest |
| 2017, 2018-01 | CloseCheckApiTest |
| 2199 (integration DB) | SelfTestServiceTest |
| 2020 | SelfTestApiTest |
| 2021 | SelfTestFunctionalTest |
| 2022 (year view, asserted exactly), 2023, 2025 (past: the journal-entry API rejects future dates) | TaxAuditFunctionalTest |
| 2099 (integration DB; tax filings stop at 2100) | TaxAuditServiceTest |
| 2031–2034 | PayrollControllerFunctionalTest, ServicePayrollComprehensiveTest |
| 2040, 2042 | TaxFilingApiTest |
| 2043 | ServiceReportsTest |
| 2044 | TaxDetailEntryTest |
| 2045 / 2047 | AmortizationControllerFunctionalTest / ServiceAmortizationTest |
| 2048 (rollback), 2048-03 (rollback) | InventoryReportServiceTest, InventoryServiceTest |
| 2049-03 / 2050 | InventoryReportControllerFunctionalTest / InventoryTransactionControllerFunctionalTest |
| 2051 / 2052 / 2053–2056 (rollback) | FixedAssetControllerFunctionalTest / FixedAssetPersistenceTest / DepreciationReportServiceTest |
| 2057-12-31, 2058, 2059, 2060, 2061, 2062-01 | JournalEntryApiTest, TransactionPersistenceTest, JournalEntryPersistenceTest, drafts, chart of accounts, TransactionTagFilterTest |
| 2063 (+ fiscal 2063-01) | PeriodReportTest |
| 2064-01/02, 2065-01, 2066-01 | SelfService, DataSubject, SecurityRegressionTest payroll periods |
| 2067 | TaxCalendarControllerFunctionalTest |
| 2068, 2072, 2098 (rollback) | FiscalYearClosingServiceTest |
| 2069 / 2070 / 2071 | FiscalPeriodControllerFunctionalTest (generate / single / filters) |
| 2073 (rollback) | CoretaxExportServiceTest |
| 2074 | ReportApiPersistenceTest |
| 2075-04 | ExportReportTest |
| 2076 | CspAlpineTest (fiscal adjustments) |
| 2077 | SellerTransactionExecutionTest |
| 2078 / 2079 / 2080 | SellerSalesTest / SellerInventoryTest / SellerMarketplaceTest |
| 2081 / 2082 / 2083 / 2084 | CampusBilling / CampusPayment / CampusScholarship / CampusReports |
| 2085 / 2086 / 2087 / 2088 / 2089 (2090 read) / 2091 | Bill controller / Bill API / PaymentTracking / Aging / Statement / InvoiceLifecycle |
| 2092–2093 / 2094 / 2095 | TaxReportDetailTest / TaxExportFunctionalTest / SptChecklistFunctionalTest |
| 2096-02/03 / 2097 | ServiceAccountingTest dashboard / ServicePayrollLifecycleTest |
| 2100-01 | BankReconciliationTest (own context) |
| 2101 / 2102 / 2103 | FiscalYearClosingPersistenceTest (closed / reversed / empty) |
| 2104-01, 2104-03 | Tag CRUD/assignment, TagReportTest |
| 2105-01 | JournalEntryUiTest |
| 2107 | FinancialAnalysisApiTest |
| 2108-02 | QuickTransactionTest |
| 2109 / 2110 / 2111 / 2112 | FinancialAnalysisApiTest receivables / SellerAnalysisReportTest receivables / FinancialAnalysisApiTest payables / SellerAnalysisReportTest payables |

Fiscal periods are validated to year 2100 at most; classes past 2100 do not create fiscal periods.

## Writing Functional Tests (Playwright)

### Basic Structure

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("functional")
class WidgetTest {

    @LocalServerPort
    private int port;

    private Playwright playwright;
    private Browser browser;
    private Page page;

    @BeforeEach
    void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
    }

    @AfterEach
    void teardown() {
        browser.close();
        playwright.close();
    }

    @Test
    void shouldCreateWidget() {
        // Login
        page.navigate("http://localhost:" + port + "/login");
        page.fill("input[name=username]", "admin");
        page.fill("input[name=password]", "password");
        page.click("button[type=submit]");

        // Navigate and interact
        page.navigate("http://localhost:" + port + "/widgets");
        // ... assertions
    }
}
```

### Playwright Gotchas

- **`<option>` elements in `<select>` are NOT "visible"** to Playwright. Do not use `assertThat(option).isVisible()`. Use `evaluate("el => el.value")` to get the selected value.

- **`text=X` is a substring match**. Use `.font-medium:text-is('Exact Text')` or `getByText(text, new Page.GetByTextOptions().setExact(true))` when precision is needed.

- **`<form>` inside `<tr>` is invalid HTML**. The browser strips it. Use card layout with forms instead of table rows wrapping forms.

- **Lazy fetch in tests**: entity relationships marked `LAZY` will throw `LazyInitializationException` outside a transaction. Use `JOIN FETCH` in repository queries or `@Transactional` on the test class.

- **Locator specificity**: `a[href*='/report']` can match sidebar links. Use more specific selectors like `a[href*='reconciliations'][href*='report']`.

## Writing Integration Tests

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WidgetRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private WidgetRepository widgetRepository;

    @Test
    void shouldFindByStatus() {
        // Test repository methods against real PostgreSQL
    }
}
```

## SpotBugs

The project maintains a zero-issue SpotBugs policy. Any new exclusions in `spotbugs-exclude.xml` must include comprehensive justifications with mitigation details.

```bash
./mvnw spotbugs:check
```

If SpotBugs reports a false positive:
1. Add an exclusion to `spotbugs-exclude.xml`.
2. Include a detailed comment explaining why it is a false positive.
3. Document what mitigation is in place.

---

Next: [Contributing](06-contributing.md)
