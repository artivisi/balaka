package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ArchiveDocument;
import com.artivisi.accountingfinance.entity.EvidenceRequirement;
import com.artivisi.accountingfinance.entity.TaxInquiryCase;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.enums.TaxInquiryStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryCaseRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryDisclosureRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.ArchiveTestFiles;
import com.artivisi.accountingfinance.service.DocumentArchiveService;
import com.artivisi.accountingfinance.service.EvidenceRequirementService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax audit workspace UI (issue #51): year view to trace to ledger line, archive import and search, inquiry case with
 * disclosure export, evidence at posting. IT service pack. Reserved test years 2022 (year view, asserted exactly),
 * 2023 (archive and case), 2025 (posting policy, draft only).
 */
@DisplayName("Tax audit workspace UI")
@Import(ServiceTestDataInitializer.class)
class TaxAuditFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2022;
    private static final int CASE_YEAR = 2023;
    private static final int POLICY_YEAR = 2025;
    private static final String OTHER_EXPENSE = "5.1.99";
    private static final String HEADER = "file,year,type,source,downloaded_on,doc_number,counterparty,amount,description,transaction_number,account_code\n";

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EvidenceRequirementService requirementService;
    @Autowired private DocumentArchiveService archiveService;
    @Autowired private TaxInquiryCaseRepository caseRepository;
    @Autowired private TaxInquiryDisclosureRepository disclosureRepository;
    @Autowired private CompanyConfigRepository companyConfigRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final List<UUID> rules = new ArrayList<>();
    private CompanyConfigSnapshot config;

    @BeforeEach
    void setUp() {
        config = CompanyConfigSnapshot.capture(companyConfigRepository);
        // Applies only to other-expense lines of at least 7.9 million; removed after each test.
        EvidenceRequirement rule = requirementService.create(new EvidenceRequirementService.NewRequirement(
                account(OTHER_EXPENSE), null, EvidenceType.CONTRACT, new BigDecimal("7900000"), "TaxAuditFunctionalTest", "test"));
        rules.add(rule.getId());
        loginAsAdmin();
    }

    @AfterEach
    void tearDown() {
        rules.forEach(requirementService::delete);
        rules.clear();
        config.restore(companyConfigRepository, accountRepository);
    }

    @Test
    @DisplayName("Year view shows the self-test line; its trace flags the evidence gap and drills down to the ledger line")
    void yearTraceLedger() {
        String number = postedExpense(LocalDate.of(YEAR, 4, 10), "Biaya lain tanpa bukti", "8000000");

        navigateTo("/dashboard");
        waitForPageLoad();
        page.evaluate("document.querySelectorAll('details').forEach(d => d.open = true)");
        page.locator("#nav-tax-audit").click();
        waitForPageLoad();
        page.getByTestId("tax-audit-year").fill(String.valueOf(YEAR));
        page.getByTestId("btn-show-tax-audit").click();
        waitForPageLoad();

        Locator line = page.getByTestId("audit-line-UNITEMISED_OTHER_EXPENSE");
        assertThat(line.getByTestId("audit-line-book")).hasText("8.000.000,00");
        assertThat(line.getByTestId("audit-line-tax")).hasText("0,00");
        assertThat(line.getByTestId("audit-line-status")).hasText("Ada selisih");
        assertThat(page.getByTestId("tax-audit-gap-summary")).hasText("1 dari 1 transaksi terposting belum lengkap buktinya");
        assertThat(page.getByTestId("tax-audit-gap")).hasCount(1);
        assertThat(page.getByTestId("tax-audit-gap")).containsText(number);
        assertThat(page.getByTestId("tax-audit-gap")).containsText("Kontrak");
        assertThat(page.getByTestId("close-checklist")).isVisible();

        line.getByTestId("audit-line-trace").click();
        waitForPageLoad();
        assertThat(page.getByTestId("trace-book")).hasText("8.000.000,00");
        assertThat(page.getByTestId("trace-tax")).hasText("0,00");
        assertThat(page.getByTestId("trace-gap-count")).hasText("1");
        Locator row = page.locator("[data-testid='trace-book-rows'] [data-testid='trace-row'][data-transaction='" + number + "']");
        assertThat(row).hasCount(1);
        assertThat(row.getByTestId("trace-row-evidence")).hasText("Kurang: Kontrak");
        assertThat(page.locator("[data-testid='trace-tax-rows'] [data-testid='trace-row']")).hasCount(0);

        row.getByTestId("trace-row-link").click();
        waitForPageLoad();
        Locator selected = page.locator("[data-testid='ledger-line'][data-selected='true']");
        assertThat(selected).hasCount(1);
        assertThat(selected).hasAttribute("data-account", OTHER_EXPENSE);
        assertThat(page.getByTestId("ledger-evidence-gap")).hasText("Bukti belum ada: Kontrak");
        assertThat(page.getByTestId("ledger-evidence")).hasAttribute("data-complete", "false");
    }

    @Test
    @DisplayName("Archive import rejects a bad ZIP with every error, imports a good one, finds it by text; a case records and exports the hand-over")
    void archiveCaseAndDisclosure() {
        String token = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String number = postedExpense(LocalDate.of(CASE_YEAR, 5, 12), "Biaya jasa " + token, "9000000");

        navigateTo("/tax-audit/archive/import");
        waitForPageLoad();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("manifest.csv", HEADER + "faktur.pdf,2020,FAKTUR,TAX_PORTAL,2025-01-15,,,,,,\n");
        bad.put("faktur.pdf", ArchiveTestFiles.pdf("x " + token));
        bad.put("lain.pdf", ArchiveTestFiles.pdf("y " + token));
        page.locator("#file").setInputFiles(new FilePayload("salah.zip", "application/zip", ArchiveTestFiles.zip(bad)));
        page.locator("#btn-import-archive").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).containsText("Impor ditolak, tidak ada dokumen yang disimpan.");
        assertThat(page.getByTestId("import-error")).hasText(new String[]{
                "Baris 2: type tidak dikenal: FAKTUR (pilihan: FAKTUR_PAJAK, BUPOT, SPT, BPE, BANK_STATEMENT, INVOICE, RECEIPT, CONTRACT, TAX_LETTER, OTHER)",
                "File di ZIP tidak tercantum di manifest.csv: lain.pdf"});
        assertThat(archiveService.search(token, null, null, null)).isEmpty();

        Map<String, Object> good = new LinkedHashMap<>();
        good.put("manifest.csv", HEADER + "2020/faktur-" + token + ".pdf,2020,FAKTUR_PAJAK,TAX_PORTAL,2025-01-15,010-" + token
                + ",PT Klien Lama,9000000,Faktur jasa," + number + "," + OTHER_EXPENSE + "\n");
        good.put("2020/faktur-" + token + ".pdf", ArchiveTestFiles.pdf("Faktur Pajak jasa konsultasi " + token));
        page.locator("#file").setInputFiles(new FilePayload("arsip.zip", "application/zip", ArchiveTestFiles.zip(good)));
        page.locator("#btn-import-archive").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("1 dokumen diimpor ke arsip");

        page.getByTestId("archive-query").fill(token);
        page.getByTestId("btn-archive-search").click();
        waitForPageLoad();
        Locator hit = page.getByTestId("archive-hit");
        assertThat(hit).hasCount(1);
        assertThat(hit).hasAttribute("data-doc-number", "010-" + token);
        assertThat(hit.getByTestId("archive-excerpt")).containsText("[" + token + "]");
        hit.locator("a").first().click();
        waitForPageLoad();
        assertThat(page.getByTestId("archive-source")).hasText("Portal pajak (Coretax/DJP Online), diunduh 2025-01-15");
        assertThat(page.getByTestId("archive-link")).hasText(number + " baris " + OTHER_EXPENSE);
        assertThat(page.getByTestId("archive-text-status")).hasText("Teks terbaca");
        ArchiveDocument archived = archiveService.search(token, null, null, null).getFirst().document();

        navigateTo("/tax-audit/cases/new?year=" + CASE_YEAR);
        waitForPageLoad();
        page.locator("#inquiryType").selectOption("SP2DK");
        page.locator("#letterNumber").fill("S-" + token);
        page.locator("#taxOffice").fill("KPP Pratama Jakarta Setiabudi");
        page.locator("#letterDate").fill("2026-01-05");
        page.locator("#receivedDate").fill("2026-01-07");
        page.locator("#deadline").fill("2026-01-21");
        page.locator("#subject").fill("Penjelasan beban lain-lain " + CASE_YEAR);
        page.locator("#letterFile").setInputFiles(new FilePayload("sp2dk.pdf", "application/pdf", ArchiveTestFiles.pdf("SP2DK " + token)));
        page.locator("#btn-create-case").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Kasus SP2DK S-" + token + " dibuat");
        assertThat(page.getByTestId("case-status")).hasText("Terbuka");
        assertThat(page.getByTestId("case-letter")).hasText("sp2dk.pdf");
        TaxInquiryCase c = caseRepository.findAll().stream().filter(x -> x.getLetterNumber().equals("S-" + token))
                .findFirst().orElseThrow();

        page.getByTestId("item-description").fill("Rincian beban lain-lain");
        page.getByTestId("item-line").selectOption("UNITEMISED_OTHER_EXPENSE");
        page.getByTestId("item-month").fill("0");
        page.getByTestId("btn-add-item").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Permintaan 1 dicatat");
        assertThat(page.getByTestId("case-item")).hasCount(1);
        assertThat(page.getByTestId("case-item")).containsText("1. Rincian beban lain-lain");

        page.getByTestId("entry-type").selectOption("REPLY");
        page.getByTestId("entry-date").fill("2026-01-15");
        page.getByTestId("entry-content").fill("Surat jawaban 01/2026");
        page.getByTestId("btn-add-entry").click();
        waitForPageLoad();
        Locator reply = page.locator("[data-testid='case-entry'][data-type='REPLY']");
        assertThat(reply.getByTestId("case-entry-content")).hasText("Surat jawaban 01/2026");
        reply.getByTestId("correction-content").fill("Nomor yang benar 02/2026");
        reply.getByTestId("btn-correct").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Koreksi dicatat");
        Locator corrected = page.locator("[data-testid='case-entry'][data-type='REPLY']");
        assertThat(corrected.getByTestId("case-entry-content")).hasText("Surat jawaban 01/2026");
        assertThat(corrected.getByTestId("case-correction")).containsText("Nomor yang benar 02/2026");

        page.locator("#btn-prepare-disclosure").click();
        waitForPageLoad();
        page.getByTestId("candidate-transactions").fill(number);
        page.getByTestId("btn-find-candidates").click();
        waitForPageLoad();
        Locator candidate = page.locator("[data-testid='candidate'][data-filename='faktur-" + token + ".pdf']");
        assertThat(page.getByTestId("candidate")).hasCount(1);
        candidate.getByTestId("candidate-select").check();
        candidate.getByTestId("candidate-item").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(1));
        page.locator("#disclosedOn").fill("2026-01-16");
        page.locator("#btn-record-disclosure").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Penyerahan 1 dokumen dicatat. Unduh file ZIP penyerahan di bawah.");
        assertThat(page.getByTestId("disclosure-row")).hasCount(1);
        assertThat(page.getByTestId("disclosure-row")).hasAttribute("data-filename", "faktur-" + token + ".pdf");
        assertThat(disclosureRepository.findByCase(c.getId())).singleElement().satisfies(d -> {
            assertThat(d.getDisclosedOn()).isEqualTo(LocalDate.of(2026, 1, 16));
            assertThat(d.getItem().getItemNo()).isEqualTo(1);
            assertThat(d.getArchiveDocument().getId()).isEqualTo(archived.getId());
            assertThat(d.getTransaction().getTransactionNumber()).isEqualTo(number);
        });

        String href = page.getByTestId("disclosure-download").getAttribute("href");
        APIResponse download = page.request().get(baseUrl() + href);
        assertThat(download.status()).isEqualTo(200);
        Map<String, byte[]> files = ArchiveTestFiles.unzip(download.body());
        String path = "dokumen/1/" + archived.getDocument().getId() + "-faktur-" + token + ".pdf";
        assertThat(files.keySet()).containsExactly("index.csv", path);
        assertThat(new String(files.get("index.csv"), StandardCharsets.UTF_8).lines().skip(1).toList()).containsExactly(
                "1,Rincian beban lain-lain," + path + ",faktur-" + token + ".pdf," + archived.getDocument().getChecksumSha256() + ","
                        + archived.getDocument().getFileSize() + ",\"Arsip Faktur Pajak 2020 (Portal pajak (Coretax/DJP Online), diunduh 2025-01-15)\","
                        + number + ",2026-01-16,SP2DK S-" + token);

        UUID trx = transactionRepository.findByTransactionNumber(number).orElseThrow().getId();
        navigateTo("/tax-audit/transactions/" + trx);
        waitForPageLoad();
        assertThat(page.getByTestId("ledger-archive-document")).containsText("010-" + token);
        assertThat(page.getByTestId("ledger-disclosure")).hasText("faktur-" + token + ".pdf ke SP2DK S-" + token + ", 2026-01-16, permintaan 1");

        navigateTo("/tax-audit/cases/" + c.getId());
        waitForPageLoad();
        page.getByTestId("decision-date").fill("2026-02-10");
        page.getByTestId("decision-text").fill("LHP2DK: penjelasan diterima");
        page.getByTestId("btn-record-decision").click();
        waitForPageLoad();
        assertThat(page.getByTestId("case-status")).hasText("Selesai");
        assertThat(page.getByTestId("case-decision")).hasText("Keputusan 2026-02-10: LHP2DK: penjelasan diterima");
        assertThat(page.locator("#btn-prepare-disclosure")).hasCount(0);
        assertThat(caseRepository.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(TaxInquiryStatus.CLOSED);
    }

    @Test
    @DisplayName("With the posting policy set, a draft missing required evidence shows the warning and posting is refused")
    void evidenceAtPosting() {
        navigateTo("/evidence/requirements");
        waitForPageLoad();
        page.getByTestId("evidence-required-from").fill(POLICY_YEAR + "-06-01");
        page.getByTestId("btn-save-posting-policy").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Posting transaksi bertanggal " + POLICY_YEAR + "-06-01 ke atas wajib berbukti lengkap");
        assertThat(companyConfigRepository.findFirst().orElseThrow().getEvidenceRequiredFrom()).isEqualTo(LocalDate.of(POLICY_YEAR, 6, 1));

        UUID draft = expense(LocalDate.of(POLICY_YEAR, 6, 15), "Biaya lain Juni", "9500000");
        navigateTo("/transactions/" + draft);
        waitForPageLoad();
        assertThat(page.getByTestId("evidence-posting-policy"))
                .hasText("Posting akan ditolak sampai bukti yang belum ada dilampirkan dan diklasifikasikan (berlaku sejak " + POLICY_YEAR + "-06-01).");
        List<String> dialogs = new ArrayList<>();
        page.onDialog(dialog -> {
            dialogs.add(dialog.message());
            dialog.accept();
        });
        page.waitForResponse(r -> r.url().endsWith("/transactions/api/" + draft + "/post"), () -> page.locator("#btn-post").click());
        page.waitForCondition(() -> dialogs.size() == 2);
        assertThat(dialogs.get(1)).isEqualTo("Gagal posting: Posting ditolak: bukti belum lengkap (Kontrak). Transaksi bertanggal "
                + POLICY_YEAR + "-06-15 wajib berbukti lengkap sejak " + POLICY_YEAR + "-06-01. Lampirkan dan klasifikasikan dokumennya, lalu posting lagi.");
        assertThat(transactionRepository.findById(draft).orElseThrow().isDraft()).isTrue();

        navigateTo("/evidence/requirements");
        waitForPageLoad();
        page.getByTestId("evidence-required-from").fill("");
        page.getByTestId("btn-save-posting-policy").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Pemeriksaan bukti saat posting dimatikan");
        assertThat(companyConfigRepository.findFirst().orElseThrow().getEvidenceRequiredFrom()).isNull();
    }

    @Test
    @DisplayName("STAFF cannot open the workspace or import into the archive")
    void staffRefused() {
        User staff = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        staff.setUsername("tax-staff-" + id);
        staff.setPassword(passwordEncoder.encode("password"));
        staff.setFullName("Staff " + id);
        staff.setEmail("tax-" + id + "@example.com");
        staff.setActive(true);
        staff.addRole(Role.STAFF, "system");
        userRepository.save(staff);
        login(staff.getUsername(), "password");

        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page.locator("#nav-tax-audit")).hasCount(0);
        assertThat(page.request().get(baseUrl() + "/tax-audit?year=" + YEAR, RequestOptions.create().setMaxRedirects(0)).status())
                .isEqualTo(403);
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse refused = page.request().post(baseUrl() + "/tax-audit/archive/import", RequestOptions.create()
                .setHeader("X-CSRF-TOKEN", csrf)
                .setMultipart(FormData.create().set("file", new FilePayload("a.zip", "application/zip", new byte[]{1})))
                .setMaxRedirects(0));
        assertThat(refused.status()).isEqualTo(403);
    }

    private String postedExpense(LocalDate date, String description, String amount) {
        UUID id = expense(date, description, amount);
        return transactionService.post(id, "admin").getTransactionNumber();
    }

    private UUID expense(LocalDate date, String description, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(account(OTHER_EXPENSE), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(account("1.1.02"), BigDecimal.ZERO, value))), "admin").transactionId();
    }

    private UUID account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code)).getId();
    }
}
