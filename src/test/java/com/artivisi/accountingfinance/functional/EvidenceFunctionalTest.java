package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.Contract;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.BankStatementArchiveRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.ContractRepository;
import com.artivisi.accountingfinance.repository.EvidenceRequirementRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DocumentService;
import com.artivisi.accountingfinance.service.EvidenceQueryService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidence rules, missing-evidence report, document classification, contract register, bank statement archive
 * and audit pack through the web UI on the IT service seed pack. Reserved test years 2191, 2192 and 2015 (the
 * archive refuses a month that has not ended).
 */
@DisplayName("Evidence completeness UI")
@Import(ServiceTestDataInitializer.class)
class EvidenceFunctionalTest extends PlaywrightTestBase {

    private static final String RENT = "5.1.05";
    private static final String TELCO = "5.1.06";

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EvidenceRequirementRepository requirementRepository;
    @Autowired private EvidenceQueryService queryService;
    @Autowired private DocumentService documentService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private CompanyBankAccountRepository bankAccountRepository;
    @Autowired private BankStatementArchiveRepository archiveRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("A rule set on the page lists the posted rent over the minimum; uploading a typed contract on the transaction clears it")
    void ruleReportAndUpload() {
        // Only this test puts a rule on the telecom account, through the form.
        UUID telcoId = accountRepository.findByAccountCode(TELCO).orElseThrow().getId();
        navigateTo("/evidence/requirements");
        waitForPageLoad();
        page.locator("#accountId").selectOption(telcoId.toString());
        page.locator("#evidenceType").selectOption("CONTRACT");
        page.locator("#minAmount").fill("1000000");
        page.locator("#btn-save-requirement").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Aturan bukti tersimpan");
        Locator row = page.locator("[data-testid='requirement-row'][data-type='CONTRACT'][data-label^='Akun " + TELCO + "']");
        assertThat(row).hasCount(1);
        assertThat(row).containsText("1.000.000");

        UUID due = posted(expense(TELCO, LocalDate.of(2191, 2, 1), "Internet kantor Februari", "2000000"));
        posted(expense(TELCO, LocalDate.of(2191, 2, 2), "Pulsa", "500000"));
        expense(TELCO, LocalDate.of(2191, 2, 3), "Internet draf", "2000000");
        String dueNumber = transactionRepository.findById(due).orElseThrow().getTransactionNumber();

        navigateTo("/evidence/missing?from=2191-02-01&to=2191-02-28");
        waitForPageLoad();
        assertThat(page.getByTestId("gap-row")).hasCount(1);
        assertThat(page.locator("[data-testid='gap-row'][data-number='" + dueNumber + "']")).hasAttribute("data-missing", "CONTRACT");
        assertThat(page.locator("#missing-summary")).containsText("2 transaksi terposting diperiksa, 1 wajib bukti, 1 kurang bukti");

        navigateTo("/transactions/" + due);
        waitForPageLoad();
        Locator required = page.locator("[data-testid='evidence-required'][data-type='CONTRACT']");
        assertThat(required).hasAttribute("data-present", "false");
        assertThat(required).containsText("Belum ada");
        page.locator("#document-file-input").setInputFiles(new FilePayload("kontrak-sewa.pdf", "application/pdf",
                "%PDF-1.4 sewa".getBytes(StandardCharsets.UTF_8)));
        page.locator("#document-evidence-type").selectOption("CONTRACT");
        page.locator("#btn-upload-document").click();
        assertThat(page.getByTestId("document-upload-success")).isVisible();
        assertThat(page.locator("[data-testid='evidence-panel']")).hasAttribute("data-complete", "true");
        assertThat(required).hasAttribute("data-present", "true");
        assertThat(page.locator("[data-testid='evidence-item'][data-type='CONTRACT']")).hasText("Kontrak: kontrak-sewa.pdf");

        navigateTo("/evidence/missing?from=2191-02-01&to=2191-02-28");
        waitForPageLoad();
        assertThat(page.getByTestId("gap-row")).hasCount(0);
    }

    @Test
    @DisplayName("Reclassifying an attachment on the transaction page reopens and closes the gap")
    void classifyExisting() throws Exception {
        UUID rentId = accountRepository.findByAccountCode(RENT).orElseThrow().getId();
        ensureRentRule(rentId);
        UUID trx = posted(rent(LocalDate.of(2191, 3, 1), "Sewa Maret", "3000000"));
        UUID docId = documentService.uploadForTransaction(trx, new MockMultipartFile("file", "scan.pdf", "application/pdf",
                "%PDF-1.4 scan".getBytes(StandardCharsets.UTF_8)), "admin", null).getId();

        navigateTo("/transactions/" + trx);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='evidence-panel']")).hasAttribute("data-complete", "false");
        assertThat(page.getByTestId("document-evidence-label-" + docId)).hasText("Belum diklasifikasikan");
        assertThat(page.getByTestId("evidence-note")).containsText("belum diklasifikasikan");

        page.getByTestId("document-classify-" + docId).selectOption("CONTRACT");
        assertThat(page.getByTestId("document-evidence-label-" + docId)).hasText("Kontrak");
        assertThat(page.locator("[data-testid='evidence-panel']")).hasAttribute("data-complete", "true");
        assertThat(queryService.evidenceFor(trx).missing()).isEmpty();
    }

    @Test
    @DisplayName("Contract registered for a client counts as evidence for a linked transaction only once its file is uploaded")
    void contractRegister() {
        UUID rentId = accountRepository.findByAccountCode(RENT).orElseThrow().getId();
        ensureRentRule(rentId);
        String marker = UUID.randomUUID().toString().substring(0, 6);
        Client client = new Client();
        client.setCode("EVC-" + marker);
        client.setName("Klien Kontrak " + marker);
        client.setActive(true);
        clientRepository.save(client);
        UUID trx = posted(rent(LocalDate.of(2191, 4, 1), "Sewa ruang server", "4000000"));
        String trxNumber = transactionRepository.findById(trx).orElseThrow().getTransactionNumber();

        navigateTo("/contracts/new");
        waitForPageLoad();
        page.locator("#contractNumber").fill("K-" + marker);
        page.locator("#title").fill("Sewa ruang server " + marker);
        page.locator("#startDate").fill("2191-01-01");
        page.locator("#btn-save-contract").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Pilih tepat satu pihak: klien atau vendor");

        page.locator("#contractNumber").fill("K-" + marker);
        page.locator("#title").fill("Sewa ruang server " + marker);
        page.locator("#clientId").selectOption(client.getId().toString());
        page.locator("#startDate").fill("2191-01-01");
        page.locator("#endDate").fill("2191-12-31");
        page.locator("#btn-save-contract").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Kontrak tersimpan");
        assertThat(page.locator("#contract-party")).hasText("Klien: Klien Kontrak " + marker);
        assertThat(page.locator("#contract-file-missing")).isVisible();

        page.locator("#transactionNumber").fill(trxNumber);
        page.locator("#btn-link-transaction").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Transaksi ditautkan");
        assertThat(page.locator("[data-testid='linked-transaction'][data-number='" + trxNumber + "']")).hasCount(1);
        assertThat(queryService.evidenceFor(trx).missing()).containsExactly(EvidenceType.CONTRACT);
        assertThat(queryService.evidenceFor(trx).notes()).contains("Kontrak K-" + marker + " belum memiliki dokumen");

        page.locator("#contract-file").setInputFiles(new FilePayload("kontrak.pdf", "application/pdf",
                "%PDF-1.4 kontrak".getBytes(StandardCharsets.UTF_8)));
        page.locator("#btn-upload-contract-file").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Dokumen kontrak tersimpan");
        assertThat(page.locator("#contract-file-link")).hasText("kontrak.pdf");
        assertThat(queryService.evidenceFor(trx).missing()).isEmpty();

        navigateTo("/contracts");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='contract-row'][data-number='K-" + marker + "']")).containsText("Ada");
        Contract stored = contractRepository.findByContractNumber("K-" + marker).orElseThrow();
        assertThat(stored.getEndDate()).isEqualTo(LocalDate.of(2191, 12, 31));
    }

    @Test
    @DisplayName("Statement archive: uploaded month shows archived, later months missing, earlier not expected; delete only after retention")
    void bankStatementArchive() {
        String marker = UUID.randomUUID().toString().substring(0, 6);
        CompanyBankAccount bank = new CompanyBankAccount();
        bank.setBankName("Bank Arsip " + marker);
        bank.setAccountNumber("7700" + marker);
        bank.setAccountName("Operasional");
        bankAccountRepository.save(bank);

        navigateTo("/evidence/bank-statements?year=2015");
        waitForPageLoad();
        page.locator("#bankAccountId").selectOption(bank.getId().toString());
        page.locator("#month").fill("2015-06");
        page.locator("#statement-file").setInputFiles(new FilePayload("rk-2015-06.pdf", "application/pdf",
                "%PDF-1.4 rk".getBytes(StandardCharsets.UTF_8)));
        page.locator("#btn-upload-statement").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Rekening koran 2015-06 diarsipkan");

        Locator row = page.locator("[data-testid='statement-row'][data-bank='Bank Arsip " + marker + "']");
        assertThat(row.locator("[data-month='2015-05']")).hasAttribute("data-status", "NOT_EXPECTED");
        assertThat(row.locator("[data-month='2015-06']")).hasAttribute("data-status", "ARCHIVED");
        assertThat(row.locator("[data-month='2015-07']")).hasAttribute("data-status", "MISSING");
        assertThat(row.locator("[data-month='2015-12']")).hasAttribute("data-status", "MISSING");

        var archive = archiveRepository.findActiveByBankAccount(bank.getId()).getFirst();
        assertThat(archive.getRetentionUntil()).isEqualTo(LocalDate.of(2025, 6, 30));
        Locator archiveRow = page.locator("[data-testid='archive-row'][data-month='2015-06']").filter(
                new Locator.FilterOptions().setHasText("Bank Arsip " + marker));
        // 2015-06 plus 10 years ended 2025-06-30, so the row is past retention and deletable
        assertThat(archiveRow.getByTestId("btn-delete-archive")).hasCount(1);

        page.locator("#bankAccountId").selectOption(bank.getId().toString());
        page.locator("#month").fill("2099-01");
        page.locator("#statement-file").setInputFiles(new FilePayload("rk-2099.pdf", "application/pdf",
                "%PDF-1.4 rk".getBytes(StandardCharsets.UTF_8)));
        page.locator("#btn-upload-statement").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).containsText("bulan belum berakhir");

        java.time.YearMonth recent = java.time.YearMonth.now().minusMonths(1);
        page.locator("#bankAccountId").selectOption(bank.getId().toString());
        page.locator("#month").fill(recent.toString());
        page.locator("#statement-file").setInputFiles(new FilePayload("rk-baru.pdf", "application/pdf",
                "%PDF-1.4 baru".getBytes(StandardCharsets.UTF_8)));
        page.locator("#btn-upload-statement").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Rekening koran " + recent + " diarsipkan");
        Locator recentRow = page.locator("[data-testid='archive-row'][data-month='" + recent + "']").filter(
                new Locator.FilterOptions().setHasText("Bank Arsip " + marker));
        assertThat(recentRow).containsText("Masa simpan berjalan");
        assertThat(recentRow.getByTestId("btn-delete-archive")).hasCount(0);
        var recentArchive = archiveRepository.findActiveByBankAccount(bank.getId()).getLast();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        page.request().post(baseUrl() + "/evidence/bank-statements/" + recentArchive.getId() + "/delete",
                RequestOptions.create().setForm(FormData.create().set("_csrf", csrf).set("year", String.valueOf(recent.getYear()))).setMaxRedirects(0));
        navigateTo("/evidence/bank-statements?year=" + recent.getYear());
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).containsText("wajib disimpan sampai " + recent.atEndOfMonth().plusYears(10));
        assertThat(archiveRepository.findById(recentArchive.getId())).isPresent();

        navigateTo("/evidence/bank-statements?year=2015");
        waitForPageLoad();
        page.locator("[data-testid='archive-row'][data-month='2015-06']").filter(
                new Locator.FilterOptions().setHasText("Bank Arsip " + marker)).getByTestId("btn-delete-archive").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Arsip rekening koran dihapus");
        assertThat(archiveRepository.findActiveByBankAccount(bank.getId())).extracting(a -> a.getYearMonth().toString())
                .containsExactly(recent.toString());
    }

    @Test
    @DisplayName("STAFF sees the report and rules but no rule form, and a direct rule post is refused")
    void staffCannotConfigure() {
        User staff = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        staff.setUsername("evd-staff-" + id);
        staff.setPassword(passwordEncoder.encode("password"));
        staff.setFullName("Staff " + id);
        staff.setEmail("evd-" + id + "@example.com");
        staff.setActive(true);
        staff.addRole(Role.STAFF, "system");
        userRepository.save(staff);
        login(staff.getUsername(), "password");

        navigateTo("/evidence/requirements");
        waitForPageLoad();
        assertThat(page.locator("#requirement-table")).isVisible();
        assertThat(page.locator("#requirement-form")).hasCount(0);
        long before = requirementRepository.count();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse refused = page.request().post(baseUrl() + "/evidence/requirements", RequestOptions.create()
                .setForm(FormData.create().set("_csrf", csrf).set("templateId", UUID.randomUUID().toString())
                        .set("evidenceType", "INVOICE")).setMaxRedirects(0));
        assertThat(refused.status()).isEqualTo(403);
        assertThat(requirementRepository.count()).isEqualTo(before);

        APIResponse pack = page.request().get(baseUrl() + "/evidence/audit-pack/download?year=2192",
                RequestOptions.create().setMaxRedirects(0));
        assertThat(pack.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("Audit pack download holds the ledger, statements, evidence index row and the attached file")
    void auditPackDownload() throws Exception {
        UUID trx = rent(LocalDate.of(2192, 5, 1), "Sewa Mei 2192", "2500000");
        UUID docId = documentService.uploadForTransaction(trx, new MockMultipartFile("file", "bukti-bayar.pdf",
                "application/pdf", "%PDF-1.4 bayar".getBytes(StandardCharsets.UTF_8)), "admin", EvidenceType.PROOF_OF_PAYMENT).getId();
        posted(trx);
        String number = transactionRepository.findById(trx).orElseThrow().getTransactionNumber();

        navigateTo("/evidence/audit-pack?year=2192");
        waitForPageLoad();
        assertThat(page.locator("#year")).hasValue("2192");
        APIResponse response = page.request().get(baseUrl() + "/evidence/audit-pack/download?year=2192&includeFiles=true");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().get("content-disposition")).isEqualTo("attachment; filename=paket-audit-2192.zip");
        Map<String, byte[]> zip = unzip(response.body());

        String file = "dokumen/transaksi/" + number + "/" + docId + "-bukti-bayar.pdf";
        assertThat(zip).containsKeys("README.txt", "buku-besar-2192.csv", "neraca-saldo-2192.xlsx", "laporan-keuangan-2192.pdf",
                "indeks-bukti-2192.csv", "arsip-rekening-koran-2192.csv", "register-kontrak-2192.csv", file);
        assertThat(new String(zip.get(file), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 bayar");
        List<String> index = new String(zip.get("indeks-bukti-2192.csv"), StandardCharsets.UTF_8).lines().toList();
        assertThat(index).hasSize(2);
        assertThat(index.get(1)).startsWith(number + ",2192-05-01,Sewa Mei 2192,2500000.00,")
                .contains("PROOF_OF_PAYMENT:ATTACHMENT:bukti-bayar.pdf");
        List<String> ledger = new String(zip.get("buku-besar-2192.csv"), StandardCharsets.UTF_8).lines().toList();
        assertThat(ledger).hasSize(3);
        assertThat(ledger).anyMatch(l -> l.startsWith(RENT + ",Beban Sewa,2192-05-01," + number + ",")
                && l.endsWith(",Sewa Mei 2192,2500000.00,0.00"));
    }

    // ==================== helpers ====================

    private void ensureRentRule(UUID rentId) {
        if (!requirementRepository.existsByAccountIdAndEvidenceType(rentId, EvidenceType.CONTRACT)) {
            var rule = new com.artivisi.accountingfinance.entity.EvidenceRequirement();
            rule.setAccount(accountRepository.findById(rentId).orElseThrow());
            rule.setEvidenceType(EvidenceType.CONTRACT);
            rule.setMinAmount(new BigDecimal("1000000"));
            rule.setCreatedBy("admin");
            requirementRepository.save(rule);
        }
    }

    private UUID rent(LocalDate date, String description, String amount) {
        return expense(RENT, date, description, amount);
    }

    private UUID expense(String code, LocalDate date, String description, String amount) {
        var rent = accountRepository.findByAccountCode(code).orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(rent.getId(), new BigDecimal(amount), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, new BigDecimal(amount)))), "admin")
                .transactionId();
    }

    private UUID posted(UUID id) {
        transactionService.post(id, "admin");
        return id;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                entries.put(entry.getName(), zin.readAllBytes());
            }
        }
        return entries;
    }
}
