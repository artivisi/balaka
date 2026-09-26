package com.artivisi.accountingfinance.functional.bankrecon;

import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.ReconciliationItem;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.BankStatementParserType;
import com.artivisi.accountingfinance.enums.MatchType;
import com.artivisi.accountingfinance.enums.ReconciliationStatus;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.repository.BankReconciliationRepository;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.BankStatementParserConfigRepository;
import com.artivisi.accountingfinance.repository.BankStatementRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ReconciliationItemRepository;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bank reconciliation: parser configs, statement import, reconciliation create, auto-match
 * (exact / fuzzy date / keyword), manual match and unmatch, bank-only / book-only, completion
 * and the reconciliation report.
 *
 * Every reconciliation test creates its own GL bank account and company bank account, posts its
 * own book transactions to that GL account and imports its own BCA-format CSV through the UI,
 * so book balances and matches only involve that test's data. Dates are in January 2100
 * (reserved for this class).
 */
@DisplayName("Bank Reconciliation")
@Import(BankReconTestDataInitializer.class)
class BankReconciliationTest extends PlaywrightTestBase {

    private static final String CSV_HEADER = "Tanggal,Keterangan,Cabang,Debit,Credit,Saldo\n";
    private static final String INCOME_TEMPLATE = "Pendapatan Jasa Training"; // BANK D / 4.1.01 C
    private static final String RENT_TEMPLATE = "Bayar Beban Sewa";           // 5.1.05 D / BANK C
    private static final String POWER_TEMPLATE = "Bayar Beban Listrik";       // 5.1.07 D / BANK C

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private CompanyBankAccountService bankAccountService;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private BankStatementParserConfigRepository parserConfigRepository;
    @Autowired private BankStatementRepository statementRepository;
    @Autowired private BankStatementItemRepository statementItemRepository;
    @Autowired private BankReconciliationRepository reconciliationRepository;
    @Autowired private ReconciliationItemRepository reconciliationItemRepository;

    private record Bank(String name, UUID id, UUID glAccountId) {}

    // ==================== Landing, navigation, access ====================

    @Test
    @DisplayName("Landing page lists the seeded January 2024 BCA statement and links to import, new reconciliation and parser configs")
    void shouldDisplayLandingPage() {
        loginAsAdmin();
        navigateTo("/bank-reconciliation");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Rekonsiliasi Bank");
        assertThat(page.locator("[data-testid='btn-import-statement']")).hasAttribute("href", "/bank-reconciliation/import");
        assertThat(page.locator("[data-testid='btn-new-reconciliation']"))
                .hasAttribute("href", "/bank-reconciliation/reconciliations/new");
        assertThat(page.locator("[data-testid='btn-parser-configs']"))
                .hasAttribute("href", "/bank-reconciliation/parser-configs");

        Locator seeded = page.locator("[data-testid='recent-statements-table'] tbody tr")
                .filter(new Locator.FilterOptions().setHasText("01/01/2024 - 31/01/2024"));
        assertThat(seeded).hasCount(1);
        assertThat(seeded.locator("td").nth(0)).hasText("BCA");
        assertThat(seeded.locator("td").nth(2)).hasText("5");

        takeManualScreenshot("bank-recon/landing-page");
    }

    @Test
    @DisplayName("Sidebar link opens the bank reconciliation landing page")
    void shouldNavigateViaSidebar() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();

        page.locator("#nav-bank-recon").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/bank-reconciliation");
        assertThat(page.locator("#page-title")).hasText("Rekonsiliasi Bank");
    }

    @Test
    @DisplayName("Staff sees statements and configs without write actions and gets 403 on import, create and auto-match")
    void staffShouldHaveViewOnlyAccess() {
        loginAsAdmin();
        Bank bank = newBank();
        UUID statementId = importStatement(bank, "staff.csv", "0", "100000",
                "03/01/2100,\"Setoran staff\",0316,,100000.00,100000.00\n");
        UUID reconId = createReconciliation(statementId);
        page.context().clearCookies();

        login("staff", "password");
        navigateTo("/bank-reconciliation");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Rekonsiliasi Bank");
        assertThat(page.locator("[data-testid='btn-import-statement']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-new-reconciliation']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-parser-configs']")).hasCount(0);

        navigateTo("/bank-reconciliation/parser-configs");
        waitForPageLoad();
        assertThat(configRow("BCA - CSV Standar")).hasCount(1);
        assertThat(page.locator("[data-testid='btn-new-config']")).hasCount(0);

        navigateTo("/bank-reconciliation/reconciliations/" + reconId);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='recon-status']")).hasText("Draft");
        assertThat(page.locator("[data-testid='btn-auto-match']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-complete']")).hasCount(0);

        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        assertThat(page.request().get(baseUrl() + "/bank-reconciliation/import").status()).isEqualTo(403);
        APIResponse autoMatch = page.request().post(
                baseUrl() + "/bank-reconciliation/reconciliations/" + reconId + "/auto-match",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setMaxRedirects(0));
        assertThat(autoMatch.status()).isEqualTo(403);
        APIResponse create = page.request().post(baseUrl() + "/bank-reconciliation/reconciliations/new",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setMaxRedirects(0)
                        .setForm(FormData.create().set("bankStatementId", statementId.toString())));
        assertThat(create.status()).isEqualTo(403);

        assertThat(reconciliationRepository.findByBankStatementId(statementId)).hasSize(1);
        assertThat(reconciliationRepository.findByIdWithRelations(reconId).orElseThrow().getStatus())
                .isEqualTo(ReconciliationStatus.DRAFT);
    }

    // ==================== Parser Configs ====================

    @Test
    @DisplayName("Parser config list shows the seeded BCA, Mandiri and BNI formats")
    void shouldDisplayParserConfigs() {
        loginAsAdmin();
        navigateTo("/bank-reconciliation/parser-configs");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Konfigurasi Parser Bank");
        for (String[] cfg : new String[][] {
                {"BCA - CSV Standar", "BCA"}, {"Mandiri - CSV Standar", "Mandiri"}, {"BNI - CSV Standar", "BNI"}}) {
            Locator row = configRow(cfg[0]);
            assertThat(row).hasCount(1);
            assertThat(row.locator("td").nth(1)).hasText(cfg[1]);
            assertThat(row.locator("td").nth(2)).hasText("dd/MM/yyyy");
            assertThat(row.locator("td").nth(3)).hasText(",");
        }

        takeManualScreenshot("bank-recon/parser-configs");
    }

    @Test
    @DisplayName("Creating a custom parser config stores its columns and format and lists it")
    void shouldCreateCustomParserConfig() {
        String name = "Parser Kustom " + shortId();
        loginAsAdmin();
        navigateTo("/bank-reconciliation/parser-configs/new");
        waitForPageLoad();

        page.locator("[data-testid='select-bank-type']").selectOption("CUSTOM");
        page.locator("[data-testid='input-config-name']").fill(name);
        page.locator("[data-testid='input-date-column']").fill("0");
        page.locator("[data-testid='input-desc-column']").fill("1");
        page.locator("[data-testid='input-date-format']").fill("yyyy-MM-dd");

        takeManualScreenshot("bank-recon/parser-config-form");

        page.locator("[data-testid='btn-save-config']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/bank-reconciliation/parser-configs");
        assertThat(page.getByText("Konfigurasi parser berhasil dibuat")).isVisible();
        Locator row = configRow(name);
        assertThat(row.locator("td").nth(1)).hasText("Custom");
        assertThat(row.locator("td").nth(2)).hasText("yyyy-MM-dd");

        BankStatementParserConfig saved = parserConfigRepository.findAllByOrderByConfigNameAsc().stream()
                .filter(c -> c.getConfigName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Parser config not saved: " + name));
        assertThat(saved.getBankType()).isEqualTo(BankStatementParserType.CUSTOM);
        assertThat(saved.getDateColumn()).isZero();
        assertThat(saved.getDescriptionColumn()).isEqualTo(1);
        assertThat(saved.getDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(saved.getActive()).isTrue();
    }

    // ==================== Statement Import ====================

    @Test
    @DisplayName("Importing a BCA CSV stores every line with its amounts and shows them on the statement detail and list")
    void shouldImportStatement() {
        loginAsAdmin();
        Bank bank = newBank();
        String filename = "mutasi-" + shortId() + ".csv";

        navigateTo("/bank-reconciliation/import");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Import Mutasi Bank");
        fillImportForm(bank, filename, "1000000", "1735000",
                "02/01/2100,\"Transfer Masuk Klien Alpha\",0316,,1250000.00,2250000.00\n"
                        + "15/01/2100,\"Transfer Keluar Sewa\",0316,500000.00,,1750000.00\n"
                        + "31/01/2100,\"Biaya Admin Bank\",0316,15000.00,,1735000.00\n");
        takeManualScreenshot("bank-recon/import-form");
        page.locator("[data-testid='btn-import']").click();
        waitForPageLoad();

        UUID statementId = idFromUrl("/bank-reconciliation/statements/");
        assertThat(page.locator("[data-testid='flash-success']"))
                .hasText("Berhasil mengimpor 3 transaksi dari " + filename);
        assertThat(page.locator("[data-testid='stmt-bank']")).hasText(bank.name());
        assertThat(page.locator("[data-testid='stmt-total-items']")).hasText("3");
        Locator rows = page.locator("[data-testid='statement-items-table'] tbody tr");
        assertThat(rows).hasCount(3);
        assertRow(rows.nth(0), "2", "02/01/2100", "Transfer Masuk Klien Alpha", "", "1,250,000", "2,250,000", "Belum Cocok");
        assertRow(rows.nth(1), "3", "15/01/2100", "Transfer Keluar Sewa", "500,000", "", "1,750,000", "Belum Cocok");
        assertRow(rows.nth(2), "4", "31/01/2100", "Biaya Admin Bank", "15,000", "", "1,735,000", "Belum Cocok");
        takeManualScreenshot("bank-recon/statement-detail");

        BankStatement statement = statementRepository.findByIdWithRelations(statementId).orElseThrow();
        assertThat(statement.getBankAccount().getId()).isEqualTo(bank.id());
        assertThat(statement.getStatementPeriodStart()).isEqualTo(LocalDate.of(2100, 1, 1));
        assertThat(statement.getStatementPeriodEnd()).isEqualTo(LocalDate.of(2100, 1, 31));
        assertThat(statement.getOpeningBalance()).isEqualByComparingTo("1000000");
        assertThat(statement.getClosingBalance()).isEqualByComparingTo("1735000");
        assertThat(statement.getTotalDebit()).isEqualByComparingTo("515000");
        assertThat(statement.getTotalCredit()).isEqualByComparingTo("1250000");
        assertThat(statement.getOriginalFilename()).isEqualTo(filename);

        navigateTo("/bank-reconciliation/statements");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Mutasi Bank");
        Locator listed = page.locator("[data-testid='statements-table'] tbody tr")
                .filter(new Locator.FilterOptions().setHasText(filename));
        assertThat(listed.locator("td").nth(0)).hasText(bank.name());
        assertThat(listed.locator("td").nth(1)).hasText("01/01/2100 - 31/01/2100");
        assertThat(listed.locator("td").nth(3)).hasText("3");
        assertThat(listed.locator("td").nth(4)).hasText("515,000");
        assertThat(listed.locator("td").nth(5)).hasText("1,250,000");
        takeManualScreenshot("bank-recon/statements-list");
    }

    @Test
    @DisplayName("Importing a CSV with an unparseable date shows the error and stores no statement")
    void shouldRejectInvalidStatementCsv() {
        loginAsAdmin();
        Bank bank = newBank();
        String filename = "rusak-" + shortId() + ".csv";

        navigateTo("/bank-reconciliation/import");
        waitForPageLoad();
        fillImportForm(bank, filename, "0", "100000", "2100-01-02,\"Setoran\",0316,,100000.00,100000.00\n");
        page.locator("[data-testid='btn-import']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/bank-reconciliation/import");
        assertThat(page.locator("[data-testid='flash-error']")).containsText(
                "Gagal mengimpor: Gagal parsing baris 2: Format tanggal tidak valid: '2100-01-02' (expected: dd/MM/yyyy)");
        assertThat(statementRepository.findByBankAccountIdWithRelations(bank.id())).isEmpty();
    }

    // ==================== Reconciliation ====================

    @Test
    @DisplayName("Creating a reconciliation takes the closing balance as bank balance and the GL balance as book balance")
    void shouldCreateReconciliation() {
        loginAsAdmin();
        Bank bank = newBank();
        UUID deposit = postBook(bank, INCOME_TEMPLATE, "2100-01-04", "3000000", "Pendapatan training Gamma");
        UUID rent = postBook(bank, RENT_TEMPLATE, "2100-01-12", "800000", "Sewa kantor Januari");
        UUID statementId = importStatement(bank, "create.csv", "0", "2185000",
                "04/01/2100,\"Transfer Masuk Gamma\",0316,,3000000.00,3000000.00\n"
                        + "12/01/2100,\"Sewa kantor\",0316,800000.00,,2200000.00\n"
                        + "31/01/2100,\"Biaya Admin Bank\",0316,15000.00,,2185000.00\n");

        navigateTo("/bank-reconciliation/reconciliations/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Rekonsiliasi Baru");
        page.locator("[data-testid='select-statement']").selectOption(statementId.toString());
        takeManualScreenshot("bank-recon/recon-form");
        page.locator("[data-testid='btn-create-recon']").click();
        waitForPageLoad();

        UUID reconId = idFromUrl("/bank-reconciliation/reconciliations/");
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekonsiliasi berhasil dibuat");
        assertThat(page.locator("#page-title")).hasText("Rekonsiliasi: " + bank.name() + " (01/01/2100 - 31/01/2100)");
        assertThat(page.locator("[data-testid='recon-status']")).hasText("Draft");
        assertThat(page.locator("[data-testid='recon-book-balance']")).hasText("2,200,000");
        assertThat(page.locator("[data-testid='recon-bank-balance']")).hasText("2,185,000");
        assertThat(page.locator("[data-testid='recon-matched-count']")).hasText("0 / 3");
        assertThat(page.locator("[data-testid='recon-difference']")).hasText("15,000");
        assertThat(page.locator("[data-testid='unmatched-bank-items'] tbody tr[id^='bank-item-']")).hasCount(3);
        assertThat(page.locator("#book-txn-" + deposit)).isVisible();
        assertThat(page.locator("#book-txn-" + rent)).isVisible();
        assertThat(page.locator("[data-testid='unmatched-book-txns'] tbody tr[id^='book-txn-']")).hasCount(2);
        takeManualScreenshot("bank-recon/recon-detail");

        BankReconciliation recon = reconciliationRepository.findByIdWithRelations(reconId).orElseThrow();
        assertThat(recon.getBankStatement().getId()).isEqualTo(statementId);
        assertThat(recon.getBookBalance()).isEqualByComparingTo("2200000");
        assertThat(recon.getBankBalance()).isEqualByComparingTo("2185000");
        assertThat(recon.getTotalStatementItems()).isEqualTo(3);

        navigateTo("/bank-reconciliation/reconciliations");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Rekonsiliasi");
        Locator listed = page.locator("[data-testid='reconciliations-table'] tbody tr")
                .filter(new Locator.FilterOptions().setHasText(bank.name()));
        assertThat(listed.locator("td").nth(1)).hasText("01/01/2100 - 31/01/2100");
        assertThat(listed.locator("td").nth(2)).hasText("Draft");
        assertThat(listed.locator("td").nth(3)).hasText("2,200,000");
        assertThat(listed.locator("td").nth(4)).hasText("2,185,000");
        assertThat(listed.locator("td").nth(5)).hasText("0/3");
    }

    @Test
    @DisplayName("Auto-match pairs same-day, one-day-off and keyword-matching items and leaves the rest unmatched")
    void shouldRunAutoMatch() {
        loginAsAdmin();
        Bank bank = newBank();
        UUID alpha = postBook(bank, INCOME_TEMPLATE, "2100-01-05", "1000000", "Pendapatan training Alpha");
        UUID hosting = postBook(bank, RENT_TEMPLATE, "2100-01-10", "250000", "Sewa server hosting");
        UUID power = postBook(bank, POWER_TEMPLATE, "2100-01-20", "375000", "Pembayaran listrik PLN Januari");
        UUID beta = postBook(bank, INCOME_TEMPLATE, "2100-01-25", "500000", "Pendapatan training Beta");
        UUID statementId = importStatement(bank, "auto.csv", "0", "360000",
                "05/01/2100,\"Transfer Masuk Klien Alpha\",0316,,1000000.00,1000000.00\n"
                        + "11/01/2100,\"Transfer Keluar Hosting\",0316,250000.00,,750000.00\n"
                        + "22/01/2100,\"Tagihan PLN listrik kantor\",0316,375000.00,,375000.00\n"
                        + "31/01/2100,\"Biaya Admin Bank\",0316,15000.00,,360000.00\n");
        UUID reconId = createReconciliation(statementId);

        page.locator("[data-testid='btn-auto-match']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']"))
                .hasText("Auto-match selesai: 3 transaksi berhasil dicocokkan");
        assertThat(page.locator("[data-testid='recon-status']")).hasText("Sedang Dikerjakan");
        assertThat(page.locator("[data-testid='recon-matched-count']")).hasText("3 / 4");
        assertMatchedRow("Transfer Masuk Klien Alpha", "Pendapatan training Alpha", "Persis Sama", "Cocok");
        assertMatchedRow("Transfer Keluar Hosting", "Sewa server hosting", "Tanggal Mendekati", "Cocok");
        assertMatchedRow("Tagihan PLN listrik kantor", "Pembayaran listrik PLN Januari", "Kata Kunci", "Cocok");
        assertThat(page.locator("[data-testid='unmatched-bank-items'] tbody tr[id^='bank-item-']")).hasCount(1);
        assertThat(page.locator("[data-testid='unmatched-bank-items']")).containsText("Biaya Admin Bank");
        assertThat(page.locator("[data-testid='unmatched-book-txns'] tbody tr[id^='book-txn-']")).hasCount(1);
        assertThat(page.locator("#book-txn-" + beta)).isVisible();
        takeManualScreenshot("bank-recon/recon-auto-match");

        List<BankStatementItem> items = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId);
        assertMatch(items.get(0), MatchType.EXACT, alpha);
        assertMatch(items.get(1), MatchType.FUZZY_DATE, hosting);
        assertMatch(items.get(2), MatchType.KEYWORD, power);
        assertThat(items.get(3).getMatchStatus()).isEqualTo(StatementItemMatchStatus.UNMATCHED);
        assertThat(reconciliationRepository.findByIdWithRelations(reconId).orElseThrow().getMatchedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Manual match pairs items auto-match cannot, and unmatch returns both sides to the unmatched lists")
    void shouldManuallyMatchAndUnmatch() {
        loginAsAdmin();
        Bank bank = newBank();
        UUID stationery = postBook(bank, RENT_TEMPLATE, "2100-01-06", "100000", "Beli alat tulis");
        UUID statementId = importStatement(bank, "manual.csv", "100000", "1000",
                "07/01/2100,\"Pembelian ATK\",0316,99000.00,,1000.00\n");
        UUID reconId = createReconciliation(statementId);
        BankStatementItem item = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId).getFirst();

        page.locator("[data-testid='btn-auto-match']").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-success']"))
                .hasText("Auto-match selesai: 0 transaksi berhasil dicocokkan");

        page.locator("[data-testid='select-statement-item']").selectOption(item.getId().toString());
        page.locator("[data-testid='select-transaction']").selectOption(stationery.toString());
        page.locator("[data-testid='btn-manual-match']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Transaksi berhasil dicocokkan");
        assertThat(page.locator("[data-testid='recon-matched-count']")).hasText("1 / 1");
        assertMatchedRow("Pembelian ATK", "Beli alat tulis", "Manual", "Cocok");
        assertThat(page.locator("[data-testid='unmatched-bank-items'] tbody tr[id^='bank-item-']")).hasCount(0);
        assertThat(page.locator("[data-testid='unmatched-book-txns'] tbody tr[id^='book-txn-']")).hasCount(0);
        assertMatch(statementItemRepository.findById(item.getId()).orElseThrow(), MatchType.MANUAL, stationery);

        matchedRow("Pembelian ATK").locator("button[title='Batalkan']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Pencocokan berhasil dibatalkan");
        assertThat(page.locator("[data-testid='recon-matched-count']")).hasText("0 / 1");
        assertThat(page.locator("[data-testid='matched-items-table']")).containsText("Belum ada item yang dicocokkan");
        assertThat(page.locator("#bank-item-" + item.getId())).isVisible();
        assertThat(page.locator("#book-txn-" + stationery)).isVisible();
        BankStatementItem reset = statementItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reset.getMatchStatus()).isEqualTo(StatementItemMatchStatus.UNMATCHED);
        assertThat(reset.getMatchType()).isNull();
        assertThat(reset.getMatchedTransaction()).isNull();
        assertThat(reconciliationItemRepository.findByReconciliationIdOrderByMatchStatusAsc(reconId)).isEmpty();
    }

    @Test
    @DisplayName("Completing needs every bank item resolved; the report then balances bank-only and book-only items to zero")
    void shouldCompleteAndReportReconciliation() {
        loginAsAdmin();
        Bank bank = newBank();
        postBook(bank, INCOME_TEMPLATE, "2100-01-02", "10000000", "Setoran awal Januari");
        UUID rentCheque = postBook(bank, RENT_TEMPLATE, "2100-01-30", "2000000", "Cek sewa gudang");
        UUID cashDeposit = postBook(bank, INCOME_TEMPLATE, "2100-01-31", "700000", "Setoran tunai akhir bulan");
        UUID statementId = importStatement(bank, "complete.csv", "0", "9990000",
                "02/01/2100,\"Setoran awal\",0316,,10000000.00,10000000.00\n"
                        + "31/01/2100,\"Biaya Admin Bank\",0316,15000.00,,9985000.00\n"
                        + "31/01/2100,\"Bunga Jasa Giro\",0316,,5000.00,9990000.00\n");
        UUID reconId = createReconciliation(statementId);
        List<BankStatementItem> items = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId);
        page.onDialog(dialog -> dialog.accept()); // "Yakin menyelesaikan rekonsiliasi ini?"

        page.locator("[data-testid='btn-auto-match']").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-success']"))
                .hasText("Auto-match selesai: 1 transaksi berhasil dicocokkan");

        page.locator("[data-testid='btn-complete']").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-error']")).hasText("Gagal menyelesaikan: Masih ada 2 item yang belum "
                + "dicocokkan. Cocokkan atau tandai sebagai bank-only/book-only terlebih dahulu.");
        assertThat(page.locator("[data-testid='recon-status']")).hasText("Sedang Dikerjakan");

        markBankOnly(items.get(1).getId());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Item ditandai sebagai hanya di bank");
        markBankOnly(items.get(2).getId());
        markBookOnly(rentCheque);
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Transaksi ditandai sebagai hanya di buku");
        markBookOnly(cashDeposit);
        assertMatchedRow("Biaya Admin Bank", "-", "-", "Hanya di Bank");
        assertMatchedRow("Bunga Jasa Giro", "-", "-", "Hanya di Bank");
        assertThat(matchedRowByBook("Cek sewa gudang").locator("td").nth(4)).hasText("Hanya di Buku");
        assertThat(matchedRowByBook("Setoran tunai akhir bulan").locator("td").nth(4)).hasText("Hanya di Buku");

        page.locator("[data-testid='btn-complete']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekonsiliasi berhasil diselesaikan");
        assertThat(page.locator("[data-testid='recon-status']")).hasText("Selesai");
        assertThat(page.locator("[data-testid='btn-auto-match']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-complete']")).hasCount(0);
        assertThat(page.locator("button[title='Batalkan']")).hasCount(0);
        BankReconciliation completed = reconciliationRepository.findByIdWithRelations(reconId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ReconciliationStatus.COMPLETED);
        assertThat(completed.getCompletedBy()).isEqualTo("admin");
        assertThat(completed.getCompletedAt()).isNotNull();

        page.locator("[data-testid='link-report']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/bank-reconciliation/reconciliations/" + reconId + "/report");
        assertThat(page.locator("[data-testid='summary-totalItems']")).hasText("3");
        assertThat(page.locator("[data-testid='summary-matchedCount']")).hasText("1");
        assertThat(page.locator("[data-testid='summary-bankOnlyCount']")).hasText("2");
        assertThat(page.locator("[data-testid='summary-bookOnlyCount']")).hasText("2");
        assertThat(page.locator("[data-testid='summary-bookBalance']")).hasText("8,700,000");
        assertThat(page.locator("[data-testid='summary-bankBalance']")).hasText("9,990,000");
        Map<String, String> expected = Map.of(
                "bookBalance", "8,700,000.00",
                "bankOnlyCreditTotal", "5,000.00",
                "bankOnlyDebitTotal", "15,000.00",
                "adjustedBookBalance", "8,690,000.00",
                "bankBalance", "9,990,000.00",
                "bookOnlyDebitTotal", "700,000.00",
                "bookOnlyCreditTotal", "2,000,000.00",
                "adjustedBankBalance", "8,690,000.00",
                "difference", "0.00");
        expected.forEach((key, value) ->
                assertThat(page.locator("[data-testid='statement-" + key + "']")).hasText(value));
        Locator outstanding = page.locator("[data-testid='outstanding-items-table'] tbody tr");
        assertThat(outstanding).hasCount(4);
        assertThat(page.locator("[data-testid='btn-print']"))
                .hasAttribute("href", "/bank-reconciliation/reconciliations/" + reconId + "/report/print");
        takeManualScreenshot("bank-recon/recon-report");

        String print = page.request().get(baseUrl() + "/bank-reconciliation/reconciliations/" + reconId + "/report/print").text();
        assertThat(print).contains("8,690,000.00").contains("2,000,000.00").contains("700,000.00");
    }

    // ==================== Helpers ====================

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A GL bank account under 1.1 plus a company bank account linked to it, both unique to the caller. */
    private Bank newBank() {
        String suffix = shortId();
        ChartOfAccount parent = accountRepository.findByAccountCode("1.1")
                .orElseThrow(() -> new IllegalStateException("Seeded account 1.1 missing"));
        ChartOfAccount gl = new ChartOfAccount();
        gl.setAccountCode("1.1.R" + suffix);
        gl.setAccountName("Bank Rekon " + suffix);
        gl.setParent(parent);
        gl = accountService.create(gl);

        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName("Bank Rekon " + suffix);
        account.setAccountNumber("RK" + suffix);
        account.setAccountName("PT ArtiVisi Intermedia");
        account.setCurrencyCode("IDR");
        account.setIsDefault(false);
        account.setGlAccount(gl);
        account = bankAccountService.create(account);
        return new Bank(account.getBankName(), account.getId(), gl.getId());
    }

    /** Posts a transaction whose BANK line goes to the bank's GL account; returns its id. */
    private UUID postBook(Bank bank, String templateName, String date, String amount, String description) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template has no BANK line: " + templateName));

        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(LocalDate.parse(date));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(description);
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(), bank.glAccountId()));
        return transactionService.post(created.getId(), "admin").getId();
    }

    private void fillImportForm(Bank bank, String filename, String opening, String closing, String csvLines) {
        UUID bcaConfigId = parserConfigRepository.findByBankTypeAndActiveTrue(BankStatementParserType.BCA).stream()
                .filter(c -> c.getConfigName().equals("BCA - CSV Standar")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Seeded parser config missing: BCA - CSV Standar"))
                .getId();
        page.locator("[data-testid='select-bank-account']").selectOption(bank.id().toString());
        page.locator("[data-testid='select-parser-config']").selectOption(bcaConfigId.toString());
        page.locator("[data-testid='input-period-start']").fill("2100-01-01");
        page.locator("[data-testid='input-period-end']").fill("2100-01-31");
        page.locator("#openingBalance").fill(opening);
        page.locator("#closingBalance").fill(closing);
        page.locator("[data-testid='input-file']").setInputFiles(new FilePayload(
                filename, "text/csv", (CSV_HEADER + csvLines).getBytes(StandardCharsets.UTF_8)));
    }

    private UUID importStatement(Bank bank, String filename, String opening, String closing, String csvLines) {
        navigateTo("/bank-reconciliation/import");
        waitForPageLoad();
        fillImportForm(bank, filename, opening, closing, csvLines);
        page.locator("[data-testid='btn-import']").click();
        waitForPageLoad();
        return idFromUrl("/bank-reconciliation/statements/");
    }

    private UUID createReconciliation(UUID statementId) {
        navigateTo("/bank-reconciliation/reconciliations/new");
        waitForPageLoad();
        page.locator("[data-testid='select-statement']").selectOption(statementId.toString());
        page.locator("[data-testid='btn-create-recon']").click();
        waitForPageLoad();
        return idFromUrl("/bank-reconciliation/reconciliations/");
    }

    private UUID idFromUrl(String prefix) {
        Matcher m = Pattern.compile(Pattern.quote(baseUrl() + prefix) + "([0-9a-f-]{36})$").matcher(page.url());
        if (!m.find()) {
            throw new AssertionError("Expected " + prefix + "{id}, was " + page.url());
        }
        return UUID.fromString(m.group(1));
    }

    private Locator configRow(String configName) {
        return page.locator("[data-testid='parser-config-table'] tbody tr")
                .filter(new Locator.FilterOptions().setHas(page.locator("td:first-child div.font-medium",
                        new com.microsoft.playwright.Page.LocatorOptions().setHasText(configName))));
    }

    private static void assertRow(Locator row, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            assertThat(row.locator("td").nth(i)).hasText(cells[i]);
        }
    }

    private Locator matchedRow(String bankDescription) {
        return page.locator("[data-testid='matched-items-table'] tbody tr")
                .filter(new Locator.FilterOptions().setHasText(bankDescription));
    }

    private Locator matchedRowByBook(String bookDescription) {
        return page.locator("[data-testid='matched-items-table'] tbody tr")
                .filter(new Locator.FilterOptions().setHasText(bookDescription));
    }

    private void assertMatchedRow(String bankDescription, String bookDescription, String type, String status) {
        Locator row = matchedRow(bankDescription);
        assertThat(row).hasCount(1);
        assertThat(row.locator("td").nth(2)).hasText(bookDescription);
        assertThat(row.locator("td").nth(3)).hasText(type);
        assertThat(row.locator("td").nth(4)).hasText(status);
    }

    private static void assertMatch(BankStatementItem item, MatchType type, UUID transactionId) {
        assertThat(item.getMatchStatus()).as("line %d", item.getLineNumber()).isEqualTo(StatementItemMatchStatus.MATCHED);
        assertThat(item.getMatchType()).as("line %d", item.getLineNumber()).isEqualTo(type);
        assertThat(item.getMatchedTransaction().getId()).as("line %d", item.getLineNumber()).isEqualTo(transactionId);
        assertThat(item.getMatchedBy()).isEqualTo("admin");
    }

    private void markBankOnly(UUID statementItemId) {
        page.locator("#bank-item-" + statementItemId + " button[title='Hanya di Bank']").click();
        waitForPageLoad();
    }

    private void markBookOnly(UUID transactionId) {
        page.locator("#book-txn-" + transactionId + " button[title='Hanya di Buku']").click();
        waitForPageLoad();
    }
}
