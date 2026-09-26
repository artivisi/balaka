package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coretax export page (/reports/tax-export) on tax details this class posts into {@link #YEAR}
 * (reserved for it). The column contents of the Coretax workbooks are verified by
 * CoretaxExportServiceTest and TaxExportApiTest; here the page's statistics, period selection and
 * the downloads it links to.
 *
 * - 03-10 faktur keluaran DPP 20M / PPN 2.2M
 * - 03-15 faktur masukan DPP 3M / PPN 0.33M
 * - 03-20 PPh 23 bupot gross 6M / 120k
 * - 03-28 faktur keluaran on a VOID transaction (excluded)
 * - 04-05 faktur keluaran DPP 8M / PPN 0.88M
 * - 04-12 PPh 23 bupot gross 2.5M / 50k
 */
@DisplayName("Tax Export (Coretax) Page")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaxExportFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2094;
    private static final String MARCH = YEAR + "-03";
    private static final String APRIL = YEAR + "-04";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DataFormatter FORMATTER = new DataFormatter();

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;

    private String fakturMarch;
    private String fakturApril;
    private String fakturVoided;
    private String fakturMasukan;
    private String bupotMarch;
    private String bupotApril;

    @BeforeAll
    void postTaxData() {
        assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .as("Another test posted into reserved tax export year " + YEAR)
                .isEmpty();

        fakturMarch = faktur();
        fakturApril = faktur();
        fakturVoided = faktur();
        fakturMasukan = faktur();
        bupotMarch = "BP23-" + YEAR + "-" + digits(6);
        bupotApril = "BP23-" + YEAR + "-" + digits(6);

        Transaction sales = post(LocalDate.of(YEAR, 3, 10), "Pendapatan Jasa + PPN", TransactionStatus.POSTED,
                line("1.1.02", "22200000", "0"), line("4.1.01", "0", "20000000"), line("2.1.03", "0", "2200000"));
        detail(sales, TaxType.PPN_KELUARAN, d -> faktur(d, fakturMarch, LocalDate.of(YEAR, 3, 10), "20000000", "2200000"));

        Transaction purchase = post(LocalDate.of(YEAR, 3, 15), "Pembelian dengan PPN", TransactionStatus.POSTED,
                line("5.1.21", "3000000", "0"), line("1.1.25", "330000", "0"), line("1.1.02", "0", "3330000"));
        detail(purchase, TaxType.PPN_MASUKAN, d -> faktur(d, fakturMasukan, LocalDate.of(YEAR, 3, 15), "3000000", "330000"));

        Transaction service = post(LocalDate.of(YEAR, 3, 20), "Pembayaran Jasa dengan PPh 23", TransactionStatus.POSTED,
                line("5.1.20", "6000000", "0"), line("1.1.02", "0", "5880000"), line("2.1.21", "0", "120000"));
        detail(service, TaxType.PPH_23, d -> bupot(d, bupotMarch, "6000000", "120000"));

        Transaction voided = post(LocalDate.of(YEAR, 3, 28), "Pendapatan Jasa + PPN", TransactionStatus.VOID,
                line("1.1.02", "1110000", "0"), line("4.1.01", "0", "1000000"), line("2.1.03", "0", "110000"));
        detail(voided, TaxType.PPN_KELUARAN, d -> faktur(d, fakturVoided, LocalDate.of(YEAR, 3, 28), "1000000", "110000"));

        Transaction aprilSales = post(LocalDate.of(YEAR, 4, 5), "Pendapatan Jasa + PPN", TransactionStatus.POSTED,
                line("1.1.02", "8880000", "0"), line("4.1.01", "0", "8000000"), line("2.1.03", "0", "880000"));
        detail(aprilSales, TaxType.PPN_KELUARAN, d -> faktur(d, fakturApril, LocalDate.of(YEAR, 4, 5), "8000000", "880000"));

        Transaction aprilService = post(LocalDate.of(YEAR, 4, 12), "Pembayaran Jasa dengan PPh 23", TransactionStatus.POSTED,
                line("5.1.20", "2500000", "0"), line("1.1.02", "0", "2450000"), line("2.1.21", "0", "50000"));
        detail(aprilService, TaxType.PPH_23, d -> bupot(d, bupotApril, "2500000", "50000"));
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Without parameters the page shows the current month, selectable among the last 24 months, and links its downloads")
    void shouldDefaultToCurrentMonth() {
        navigateTo("/reports/tax-export");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Export Data Pajak untuk Coretax");
        YearMonth current = YearMonth.now();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            expected.add(current.minusMonths(i).toString());
        }
        assertThat(page.locator("#startMonth option").evaluateAll("os => os.map(o => o.value)")).isEqualTo(expected);
        assertThat(page.locator("#endMonth option").evaluateAll("os => os.map(o => o.value)")).isEqualTo(expected);
        assertThat(page.locator("#startMonth")).hasValue(current.toString());
        assertThat(page.locator("#endMonth")).hasValue(current.toString());
        assertThat(page.locator("#period-start")).hasText(dayMonthYear(current.atDay(1)));
        assertThat(page.locator("#period-end")).hasText(dayMonthYear(current.atEndOfMonth()));
        for (String type : List.of("efaktur-keluaran", "efaktur-masukan", "bupot-unifikasi")) {
            assertThat(page.locator("#btn-download-" + type)).hasAttribute("href",
                    "/reports/tax-export/" + type + "?startMonth=" + current + "&endMonth=" + current);
        }
    }

    @Test
    @DisplayName("Statistics count and sum only the chosen months' posted faktur and bupot; the period form narrows them and updates the download links")
    void shouldShowStatisticsForChosenPeriod() {
        navigateTo("/reports/tax-export?startMonth=" + MARCH + "&endMonth=" + APRIL);
        waitForPageLoad();

        assertThat(page.locator("#startMonth")).hasValue(MARCH);
        assertThat(page.locator("#endMonth")).hasValue(APRIL);
        assertThat(page.locator("#period-start")).hasText("01 Maret " + YEAR);
        assertThat(page.locator("#period-end")).hasText("30 April " + YEAR);
        assertStatistics("2 faktur", "3.080.000", "1 faktur", "330.000", "2 bupot", "170.000");

        page.locator("#endMonth").selectOption(MARCH);
        page.locator("button:has-text('Tampilkan')").click();
        page.waitForURL(url -> url.endsWith("/reports/tax-export?startMonth=" + MARCH + "&endMonth=" + MARCH));
        waitForPageLoad();

        assertThat(page.locator("#endMonth")).hasValue(MARCH);
        assertThat(page.locator("#period-end")).hasText("31 Maret " + YEAR);
        assertStatistics("1 faktur", "2.200.000", "1 faktur", "330.000", "1 bupot", "120.000");
        for (String type : List.of("efaktur-keluaran", "efaktur-masukan", "bupot-unifikasi")) {
            assertThat(page.locator("#btn-download-" + type)).hasAttribute("href",
                    "/reports/tax-export/" + type + "?startMonth=" + MARCH + "&endMonth=" + MARCH);
            assertThat(page.locator("#btn-download-" + type)).not().hasClass(Pattern.compile(".*pointer-events-none.*"));
        }
    }

    @Test
    @DisplayName("Download links return Coretax workbooks holding exactly the chosen months' posted faktur and bupot")
    void shouldDownloadWorkbooksForChosenPeriod() throws Exception {
        navigateTo("/reports/tax-export?startMonth=" + MARCH + "&endMonth=" + APRIL);
        waitForPageLoad();

        String suffix = "_" + YEAR + "03_" + YEAR + "04.xlsx";
        try (Workbook keluaran = download("#btn-download-efaktur-keluaran", "efaktur-keluaran" + suffix)) {
            assertThat(column(keluaran.getSheet("DATA"), 1)).containsExactly(fakturMarch, fakturApril);
        }
        try (Workbook masukan = download("#btn-download-efaktur-masukan", "efaktur-masukan" + suffix)) {
            assertThat(column(masukan.getSheet("DATA"), 1)).containsExactly(fakturMasukan);
        }
        try (Workbook bupot = download("#btn-download-bupot-unifikasi", "bupot-unifikasi" + suffix)) {
            assertThat(column(bupot.getSheet("DATA"), 0)).containsExactly(bupotMarch, bupotApril);
        }
    }

    @Test
    @DisplayName("A period without tax details shows zero statistics and disables the downloads")
    void shouldDisableDownloadsForEmptyPeriod() {
        navigateTo("/reports/tax-export?startMonth=" + YEAR + "-06&endMonth=" + YEAR + "-06");
        waitForPageLoad();

        assertStatistics("0 faktur", "0", "0 faktur", "0", "0 bupot", "0");
        for (String type : List.of("efaktur-keluaran", "efaktur-masukan", "bupot-unifikasi")) {
            assertThat(page.locator("#btn-download-" + type)).hasClass(Pattern.compile(".*pointer-events-none.*"));
        }
    }

    @Test
    @DisplayName("Sidebar opens the page with its menu entry highlighted; page links to company settings and the DJP converter")
    void shouldNavigateAndLinkRelatedPages() {
        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-laporan").click();
        page.locator("#nav-tax-export").click();
        page.waitForURL(url -> url.endsWith("/reports/tax-export"));

        assertThat(page.locator("#page-title")).hasText("Export Data Pajak untuk Coretax");
        assertThat(page.locator("#nav-tax-export")).hasClass(Pattern.compile(".*bg-primary-600.*"));
        assertThat(page.locator("main a[href='/settings/company']")).hasText("Pengaturan Perusahaan");
        String converter = "https://www.pajak.go.id/reformdjp/coretax/template-xml-dan-converter-excel-ke-xml";
        assertThat(page.locator("main a[href='" + converter + "']")).hasCount(2);
        for (int i = 0; i < 2; i++) {
            assertThat(page.locator("main a[href='" + converter + "']").nth(i)).hasAttribute("target", "_blank");
        }
    }

    private void assertStatistics(String keluaranCount, String keluaranTotal, String masukanCount,
                                  String masukanTotal, String bupotCount, String bupotTotal) {
        assertThat(page.locator("#efaktur-keluaran-count")).hasText(keluaranCount);
        assertThat(page.locator("#efaktur-keluaran-total")).hasText(keluaranTotal);
        assertThat(page.locator("#efaktur-masukan-count")).hasText(masukanCount);
        assertThat(page.locator("#efaktur-masukan-total")).hasText(masukanTotal);
        assertThat(page.locator("#bupot-unifikasi-count")).hasText(bupotCount);
        assertThat(page.locator("#bupot-unifikasi-total")).hasText(bupotTotal);
    }

    private Workbook download(String buttonSelector, String filename) throws IOException {
        String href = page.locator(buttonSelector).getAttribute("href");
        APIResponse response = page.request().get(baseUrl() + href);
        assertThat(response.status()).as(href).isEqualTo(200);
        assertThat(response.headers().get("content-type")).isEqualTo(XLSX);
        assertThat(response.headers().get("content-disposition")).isEqualTo("attachment; filename=\"" + filename + "\"");
        return new XSSFWorkbook(new ByteArrayInputStream(response.body()));
    }

    private static List<String> column(Sheet sheet, int column) {
        List<String> values = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            values.add(FORMATTER.formatCellValue(sheet.getRow(r).getCell(column)));
        }
        return values;
    }

    private static String dayMonthYear(LocalDate date) {
        String[] months = {"Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus",
                "September", "Oktober", "November", "Desember"};
        return String.format("%02d %s %d", date.getDayOfMonth(), months[date.getMonthValue() - 1], date.getYear());
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private Transaction post(LocalDate date, String templateName, TransactionStatus status, Line... lines) {
        String number = "TXF-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(date);
        tx.setDescription(templateName + " " + number);
        tx.setAmount(lines[0].debit());
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        tx.setStatus(status);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        if (status == TransactionStatus.VOID) {
            tx.setVoidReason(com.artivisi.accountingfinance.enums.VoidReason.INPUT_ERROR);
            tx.setVoidedAt(LocalDateTime.now());
            tx.setVoidedBy("admin");
        }
        Transaction saved = transactionRepository.save(tx);

        int index = 0;
        for (Line l : lines) {
            JournalEntry entry = new JournalEntry();
            entry.setJournalNumber(number + "-" + String.format("%02d", ++index));
            entry.setTransaction(saved);
            entry.setAccount(account(l.accountCode()));
            entry.setDebitAmount(l.debit());
            entry.setCreditAmount(l.credit());
            entry.setPostedAt(saved.getPostedAt());
            entry.setCreatedBy("admin");
            journalEntryRepository.save(entry);
        }
        return saved;
    }

    private void detail(Transaction tx, TaxType type, Consumer<TaxTransactionDetail> fields) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(tx);
        detail.setTaxType(type);
        fields.accept(detail);
        taxDetailRepository.save(detail);
    }

    private static void faktur(TaxTransactionDetail d, String number, LocalDate date, String dpp, String ppn) {
        d.setFakturNumber(number);
        d.setFakturDate(date);
        d.setTransactionCode("01");
        d.setDpp(new BigDecimal(dpp));
        d.setPpn(new BigDecimal(ppn));
        d.setCounterpartyIdType("TIN");
        d.setCounterpartyNpwp("01.310.523.4-091.000");
        d.setCounterpartyName("PT Klien Coretax");
    }

    private static void bupot(TaxTransactionDetail d, String number, String gross, String tax) {
        d.setBupotNumber(number);
        d.setTaxObjectCode("24-104-01");
        d.setGrossAmount(new BigDecimal(gross));
        d.setTaxRate(new BigDecimal("2.00"));
        d.setTaxAmount(new BigDecimal(tax));
        d.setCounterpartyIdType("TIN");
        d.setCounterpartyNpwp("03.555.666.7-888.000");
        d.setCounterpartyName("CV Vendor Coretax");
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String faktur() {
        return "010." + digits(3) + "-94." + digits(8);
    }

    private static String digits(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
