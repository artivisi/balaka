package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax detail report pages (Rincian PPN, Rincian PPh 23, Cross-check PPN, Rekonsiliasi Fiskal) on
 * journals and tax details this class posts into {@link #YEAR} (reserved for it); fiscal
 * adjustments are added and deleted through the UI in {@link #ADJUSTMENT_YEAR} (reserved, no journals).
 *
 * - 03-05 Pendapatan Jasa + PPN: Dr 1.1.02 33.3M / Cr 4.1.01 30M / Cr 2.1.03 3.3M; faktur keluaran
 * - 03-12 Pembelian dengan PPN: Dr 5.1.21 4M / Dr 1.1.25 0.44M / Cr 1.1.02 4.44M; faktur masukan
 * - 03-18 Pendapatan Jasa + PPN: Dr 1.1.02 11.1M / Cr 4.1.01 10M / Cr 2.1.03 1.1M; faktur keluaran
 * - 03-20 Pembayaran Jasa dengan PPh 23: Dr 5.1.20 5M / Cr 1.1.02 4.9M / Cr 2.1.21 0.1M; bupot
 * - 03-25 Pendapatan Jasa + PPN, VOID: Dr 1.1.02 5.55M / Cr 4.1.01 5M / Cr 2.1.03 0.55M; faktur keluaran
 * - 04-10 Pendapatan Jasa + PPN without faktur: Dr 1.1.02 2.22M / Cr 4.1.01 2M / Cr 2.1.03 0.22M
 * - 06-15 Pendapatan Jasa + PPh 23 Dipotong: Dr 1.1.02 9.8M / Dr 1.1.26 0.2M / Cr 4.1.02 10M
 * - 07-15 Bayar PPh 25: Dr 5.9.02 0.3M / Cr 1.1.02 0.3M
 * - fiscal adjustments: +1.5M permanent, (0.5M) temporary
 * Revenue 52M, expense 9.3M, commercial net income 42.7M, PKP 43.7M.
 */
@DisplayName("Tax Report Detail Pages")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaxReportDetailTest extends PlaywrightTestBase {

    private static final int YEAR = 2092;
    private static final int ADJUSTMENT_YEAR = 2093;
    private static final String MARCH_START = YEAR + "-03-01";
    private static final String MARCH_END = YEAR + "-03-31";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DataFormatter FORMATTER = new DataFormatter();

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private FiscalAdjustmentRepository fiscalAdjustmentRepository;

    private String fakturKeluaran1;
    private String fakturKeluaran2;
    private String fakturVoided;
    private String fakturMasukan;
    private String bupot;

    @BeforeAll
    void postTaxData() {
        for (int year : List.of(YEAR, ADJUSTMENT_YEAR)) {
            assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                    .as("Another test posted into reserved tax detail year " + year)
                    .isEmpty();
            assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(year))
                    .as("Another test added fiscal adjustments to reserved year " + year)
                    .isEmpty();
        }

        fakturKeluaran1 = faktur();
        fakturKeluaran2 = faktur();
        fakturVoided = faktur();
        fakturMasukan = faktur();
        bupot = "BP23-" + YEAR + "-" + digits(6);

        Transaction sales1 = post(LocalDate.of(YEAR, 3, 5), "Pendapatan Jasa + PPN", TransactionStatus.POSTED,
                line("1.1.02", "33300000", "0"), line("4.1.01", "0", "30000000"), line("2.1.03", "0", "3300000"));
        detail(sales1, TaxType.PPN_KELUARAN, d -> faktur(d, fakturKeluaran1, LocalDate.of(YEAR, 3, 5), "30000000", "3300000",
                "01.111.222.3-444.000", "PT Klien Rincian Satu"));

        Transaction purchase = post(LocalDate.of(YEAR, 3, 12), "Pembelian dengan PPN", TransactionStatus.POSTED,
                line("5.1.21", "4000000", "0"), line("1.1.25", "440000", "0"), line("1.1.02", "0", "4440000"));
        detail(purchase, TaxType.PPN_MASUKAN, d -> faktur(d, fakturMasukan, LocalDate.of(YEAR, 3, 12), "4000000", "440000",
                "03.555.666.7-888.000", "PT Pemasok Rincian"));

        Transaction sales2 = post(LocalDate.of(YEAR, 3, 18), "Pendapatan Jasa + PPN", TransactionStatus.POSTED,
                line("1.1.02", "11100000", "0"), line("4.1.01", "0", "10000000"), line("2.1.03", "0", "1100000"));
        detail(sales2, TaxType.PPN_KELUARAN, d -> faktur(d, fakturKeluaran2, LocalDate.of(YEAR, 3, 18), "10000000", "1100000",
                "02.333.444.5-666.000", "PT Klien Rincian Dua"));

        Transaction service = post(LocalDate.of(YEAR, 3, 20), "Pembayaran Jasa dengan PPh 23", TransactionStatus.POSTED,
                line("5.1.20", "5000000", "0"), line("1.1.02", "0", "4900000"), line("2.1.21", "0", "100000"));
        detail(service, TaxType.PPH_23, d -> {
            d.setBupotNumber(bupot);
            d.setTaxObjectCode("24-104-01");
            d.setGrossAmount(new BigDecimal("5000000"));
            d.setTaxRate(new BigDecimal("2.00"));
            d.setTaxAmount(new BigDecimal("100000"));
            d.setCounterpartyIdType("TIN");
            d.setCounterpartyNpwp("04.777.888.9-000.000");
            d.setCounterpartyName("CV Vendor Rincian");
        });

        Transaction voided = post(LocalDate.of(YEAR, 3, 25), "Pendapatan Jasa + PPN", TransactionStatus.VOID,
                line("1.1.02", "5550000", "0"), line("4.1.01", "0", "5000000"), line("2.1.03", "0", "550000"));
        detail(voided, TaxType.PPN_KELUARAN, d -> faktur(d, fakturVoided, LocalDate.of(YEAR, 3, 25), "5000000", "550000",
                "01.111.222.3-444.000", "PT Klien Rincian Batal"));

        post(LocalDate.of(YEAR, 4, 10), "Pendapatan Jasa + PPN", TransactionStatus.POSTED,
                line("1.1.02", "2220000", "0"), line("4.1.01", "0", "2000000"), line("2.1.03", "0", "220000"));
        post(LocalDate.of(YEAR, 6, 15), "Pendapatan Jasa + PPh 23 Dipotong", TransactionStatus.POSTED,
                line("1.1.02", "9800000", "0"), line("1.1.26", "200000", "0"), line("4.1.02", "0", "10000000"));
        post(LocalDate.of(YEAR, 7, 15), "Bayar PPh 25", TransactionStatus.POSTED,
                line("5.9.02", "300000", "0"), line("1.1.02", "0", "300000"));

        adjustment("Beban jamuan tanpa daftar nominatif", FiscalAdjustmentCategory.PERMANENT,
                FiscalAdjustmentDirection.POSITIVE, "1500000");
        adjustment("Selisih penyusutan fiskal", FiscalAdjustmentCategory.TEMPORARY,
                FiscalAdjustmentDirection.NEGATIVE, "500000");
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Report index links each tax detail report to its page")
    void shouldLinkTaxDetailReportsFromIndex() {
        List<String[]> reports = List.of(
                new String[]{"/reports/ppn-detail", "Rincian PPN"},
                new String[]{"/reports/pph23-detail", "Rincian PPh 23"},
                new String[]{"/reports/ppn-crosscheck", "Cross-check PPN"},
                new String[]{"/reports/rekonsiliasi-fiskal", "Rekonsiliasi Fiskal"});
        for (String[] report : reports) {
            navigateTo("/reports");
            waitForPageLoad();
            Locator link = page.locator("main a[href='" + report[0] + "']");
            assertThat(link).hasCount(1);
            assertThat(link.locator("h3")).hasText(report[1]);
            link.click();
            page.waitForURL(url -> url.endsWith(report[0]));
            assertThat(page.locator("#page-title")).hasText(report[1]);
        }
    }

    @Test
    @DisplayName("PPN detail lists each posted faktur keluaran and masukan of the chosen range with totals, excluding voided transactions")
    void shouldListFakturOfRange() {
        navigateTo("/reports/ppn-detail");
        waitForPageLoad();
        assertThat(page.locator("#startDate")).hasValue(LocalDate.now().withDayOfMonth(1).toString());
        assertThat(page.locator("#endDate")).hasValue(LocalDate.now().toString());
        submitRange("/reports/ppn-detail", MARCH_START, MARCH_END);

        assertThat(page.locator("#report-period")).hasText("Periode: 1 Maret " + YEAR + " - 31 Maret " + YEAR);
        Locator keluaran = page.locator("#keluaran-items tr");
        assertThat(keluaran).hasCount(2);
        assertCells(keluaran.nth(0), fakturKeluaran1, "05/03/" + YEAR, "01", "PT Klien Rincian Satu",
                "01.111.222.3-444.000", "30.000.000", "3.300.000");
        assertCells(keluaran.nth(1), fakturKeluaran2, "18/03/" + YEAR, "01", "PT Klien Rincian Dua",
                "02.333.444.5-666.000", "10.000.000", "1.100.000");
        assertThat(page.locator("#ppn-detail-content")).not().containsText(fakturVoided);
        assertThat(page.locator("#total-dpp-keluaran")).hasText("40.000.000");
        assertThat(page.locator("#total-ppn-keluaran")).hasText("4.400.000");

        Locator masukan = page.locator("#masukan-items tr");
        assertThat(masukan).hasCount(1);
        assertCells(masukan.nth(0), fakturMasukan, "12/03/" + YEAR, "01", "PT Pemasok Rincian",
                "03.555.666.7-888.000", "4.000.000", "440.000");
        assertThat(page.locator("#total-dpp-masukan")).hasText("4.000.000");
        assertThat(page.locator("#total-ppn-masukan")).hasText("440.000");

        assertThat(page.locator("#btn-export-pdf")).hasAttribute("href",
                "/reports/ppn-detail/export/pdf?startDate=" + MARCH_START + "&endDate=" + MARCH_END);
        assertThat(page.locator("#btn-export-excel")).hasAttribute("href",
                "/reports/ppn-detail/export/excel?startDate=" + MARCH_START + "&endDate=" + MARCH_END);
    }

    @Test
    @DisplayName("PPN detail of a range without faktur shows the empty rows and zero totals")
    void shouldShowEmptyPpnDetail() {
        navigateTo("/reports/ppn-detail?startDate=" + YEAR + "-04-01&endDate=" + YEAR + "-04-30");
        waitForPageLoad();

        assertThat(page.locator("#keluaran-items tr")).hasCount(1);
        assertThat(page.locator("#keluaran-items")).hasText("Tidak ada data faktur keluaran");
        assertThat(page.locator("#masukan-items")).hasText("Tidak ada data faktur masukan");
        assertThat(page.locator("#total-dpp-keluaran")).hasText("0");
        assertThat(page.locator("#total-ppn-keluaran")).hasText("0");
        assertThat(page.locator("#total-dpp-masukan")).hasText("0");
        assertThat(page.locator("#total-ppn-masukan")).hasText("0");
    }

    @Test
    @DisplayName("PPN detail PDF and Excel exports contain the range's faktur and totals")
    void shouldExportPpnDetail() throws Exception {
        navigateTo("/reports/ppn-detail?startDate=" + MARCH_START + "&endDate=" + MARCH_END);
        waitForPageLoad();

        String pdf = pdfText(download("#btn-export-pdf", "application/pdf",
                "rincian-ppn-" + YEAR + "0301-" + YEAR + "0331.pdf"));
        assertThat(pdf).contains("RINCIAN PPN PER FAKTUR", fakturKeluaran1, fakturKeluaran2, fakturMasukan,
                "PT Klien Rincian Satu", "40.000.000", "4.400.000", "440.000").doesNotContain(fakturVoided);

        try (Workbook workbook = workbook(download("#btn-export-excel", XLSX,
                "rincian-ppn-" + YEAR + "0301-" + YEAR + "0331.xlsx"))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row first = rowStartingWith(sheet, fakturKeluaran1);
            assertThat(text(first, 3)).isEqualTo("PT Klien Rincian Satu");
            assertThat(first.getCell(5).getNumericCellValue()).isEqualTo(30_000_000d);
            assertThat(first.getCell(6).getNumericCellValue()).isEqualTo(3_300_000d);
            assertThat(rowStartingWith(sheet, fakturKeluaran2).getCell(6).getNumericCellValue()).isEqualTo(1_100_000d);
            assertThat(rowStartingWith(sheet, fakturMasukan).getCell(6).getNumericCellValue()).isEqualTo(440_000d);
            assertThat(rowsStartingWith(sheet, fakturVoided)).isZero();
        }
    }

    @Test
    @DisplayName("PPh 23 detail lists the range's bukti potong with rate and totals, and exports them")
    void shouldListBupotOfRange() throws Exception {
        navigateTo("/reports/pph23-detail");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Rincian PPh 23");
        submitRange("/reports/pph23-detail", MARCH_START, MARCH_END);

        assertThat(page.locator("#report-period")).hasText("Periode: 1 Maret " + YEAR + " - 31 Maret " + YEAR);
        Locator rows = page.locator("#pph23-items tr");
        assertThat(rows).hasCount(1);
        assertCells(rows.nth(0), bupot, "24-104-01", "CV Vendor Rincian", "04.777.888.9-000.000",
                "5.000.000", "2,00%", "100.000");
        assertThat(page.locator("#total-gross")).hasText("5.000.000");
        assertThat(page.locator("#total-tax")).hasText("100.000");

        String pdf = pdfText(download("#btn-export-pdf", "application/pdf",
                "rincian-pph23-" + YEAR + "0301-" + YEAR + "0331.pdf"));
        assertThat(pdf).contains(bupot, "CV Vendor Rincian", "5.000.000", "100.000");
        try (Workbook workbook = workbook(download("#btn-export-excel", XLSX,
                "rincian-pph23-" + YEAR + "0301-" + YEAR + "0331.xlsx"))) {
            Row row = rowStartingWith(workbook.getSheetAt(0), bupot);
            assertThat(text(row, 1)).isEqualTo("24-104-01");
            assertThat(text(row, 2)).isEqualTo("CV Vendor Rincian");
        }

        navigateTo("/reports/pph23-detail?startDate=" + YEAR + "-04-01&endDate=" + YEAR + "-04-30");
        waitForPageLoad();
        assertThat(page.locator("#pph23-items tr")).hasCount(1);
        assertThat(page.locator("#pph23-items")).not().containsText(bupot);
        assertThat(page.locator("#total-tax")).hasText("0");
    }

    @Test
    @DisplayName("PPN cross-check matches faktur and ledger for March and reports the April sale without faktur as a difference")
    void shouldCrossCheckFakturAgainstLedger() throws Exception {
        navigateTo("/reports/ppn-crosscheck");
        waitForPageLoad();
        submitRange("/reports/ppn-crosscheck", MARCH_START, MARCH_END);

        assertThat(page.locator("#faktur-ppn-keluaran")).hasText("4.400.000");
        assertThat(page.locator("#ledger-ppn-keluaran")).hasText("4.400.000");
        assertThat(page.locator("#keluaran-diff")).hasText("0");
        assertThat(page.locator("#faktur-ppn-masukan")).hasText("440.000");
        assertThat(page.locator("#ledger-ppn-masukan")).hasText("440.000");
        assertThat(page.locator("#masukan-diff")).hasText("0");
        assertThat(page.locator("#crosscheck-status")).hasText("Data faktur pajak sesuai dengan buku besar.");

        String end = YEAR + "-04-30";
        navigateTo("/reports/ppn-crosscheck?startDate=" + MARCH_START + "&endDate=" + end);
        waitForPageLoad();
        assertThat(page.locator("#faktur-ppn-keluaran")).hasText("4.400.000");
        assertThat(page.locator("#ledger-ppn-keluaran")).hasText("4.620.000");
        assertThat(page.locator("#keluaran-diff")).hasText("-220.000");
        assertThat(page.locator("#keluaran-diff")).hasClass(java.util.regex.Pattern.compile(".*text-red-600.*"));
        assertThat(page.locator("#masukan-diff")).hasText("0");
        assertThat(page.locator("#crosscheck-status")).hasText(
                "Terdapat selisih antara data faktur dan buku besar. Periksa kembali entri jurnal.");

        String pdf = pdfText(download("#btn-export-pdf", "application/pdf",
                "crosscheck-ppn-" + YEAR + "0301-" + YEAR + "0430.pdf"));
        assertThat(pdf).contains("CROSS-CHECK PPN", "4.400.000", "4.620.000");
        try (Workbook workbook = workbook(download("#btn-export-excel", XLSX,
                "crosscheck-ppn-" + YEAR + "0301-" + YEAR + "0430.xlsx"))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row keluaran = rowStartingWith(sheet, "PPN Keluaran");
            assertThat(keluaran.getCell(1).getNumericCellValue()).isEqualTo(4_400_000d);
            assertThat(keluaran.getCell(2).getNumericCellValue()).isEqualTo(4_620_000d);
            assertThat(keluaran.getCell(3).getNumericCellValue()).isEqualTo(-220_000d);
            Row masukan = rowStartingWith(sheet, "PPN Masukan");
            assertThat(masukan.getCell(1).getNumericCellValue()).isEqualTo(440_000d);
            assertThat(masukan.getCell(2).getNumericCellValue()).isEqualTo(440_000d);
            assertThat(masukan.getCell(3).getNumericCellValue()).isZero();
        }
    }

    @Test
    @DisplayName("Rekonsiliasi fiskal computes commercial income, adjustments, PKP, PPh Badan with Pasal 31E and tax credits for the year")
    void shouldReconcileFiscalYear() throws Exception {
        navigateTo("/reports/rekonsiliasi-fiskal");
        waitForPageLoad();
        assertThat(page.locator("#year")).hasValue(String.valueOf(LocalDate.now().getYear()));
        page.locator("#year").fill(String.valueOf(YEAR));
        page.locator("#btn-generate").click();
        page.waitForURL(url -> url.endsWith("/reports/rekonsiliasi-fiskal?year=" + YEAR));

        assertThat(page.locator("#report-year")).hasText("Tahun Pajak: " + YEAR);
        assertThat(page.locator("#total-revenue")).hasText("52.000.000");
        assertThat(page.locator("#total-expense")).hasText("(9.300.000)");
        assertThat(page.locator("#commercial-net-income")).hasText("42.700.000");

        Locator adjustments = page.locator("#adjustment-items tr");
        assertThat(adjustments).hasCount(2);
        assertThat(adjustments.nth(0)).containsText("Beban jamuan tanpa daftar nominatif");
        assertThat(adjustments.nth(0)).containsText("Beda Tetap");
        assertThat(adjustments.nth(0)).containsText("Koreksi Positif");
        assertThat(adjustments.nth(0)).containsText("1.500.000");
        assertThat(adjustments.nth(1)).containsText("Selisih penyusutan fiskal");
        assertThat(adjustments.nth(1)).containsText("Beda Waktu");
        assertThat(adjustments.nth(1)).containsText("Koreksi Negatif");
        assertThat(adjustments.nth(1)).containsText("(500.000)");
        assertThat(page.locator("#total-positive")).hasText("1.500.000");
        assertThat(page.locator("#total-negative")).hasText("(500.000)");
        assertThat(page.locator("#net-adjustment")).hasText("1.000.000");

        assertThat(page.locator("#pkp")).hasText("43.700.000");
        assertThat(page.locator("#calculation-method")).hasText("Fasilitas Pasal 31E penuh (omset < 4,8M)");
        assertThat(page.locator("#pph-terutang")).hasText("4.807.000");
        assertThat(page.locator("#kredit-pph23")).hasText("(200.000)");
        assertThat(page.locator("#kredit-pph25")).hasText("(300.000)");
        assertThat(page.locator("#total-kredit")).hasText("(500.000)");
        assertThat(page.locator("#pph29")).hasText("4.307.000");
        assertThat(page.locator("#rekonsiliasi-fiskal-content")).containsText("PPh Pasal 29 (Kurang Bayar)");

        String pdf = pdfText(download("#btn-export-pdf", "application/pdf", "rekonsiliasi-fiskal-" + YEAR + ".pdf"));
        assertThat(pdf).contains("REKONSILIASI FISKAL", "52.000.000", "42.700.000", "43.700.000", "4.807.000", "4.307.000");
        try (Workbook workbook = workbook(download("#btn-export-excel", XLSX, "rekonsiliasi-fiskal-" + YEAR + ".xlsx"))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(rowStartingWith(sheet, "Pendapatan").getCell(1).getNumericCellValue()).isEqualTo(52_000_000d);
            assertThat(rowStartingWith(sheet, "Beban").getCell(1).getNumericCellValue()).isEqualTo(-9_300_000d);
            assertThat(rowStartingWith(sheet, "Beban jamuan tanpa daftar nominatif (Beda Tetap, Koreksi Positif)")
                    .getCell(1).getNumericCellValue()).isEqualTo(1_500_000d);
            assertThat(rowStartingWith(sheet, "Penghasilan Kena Pajak (PKP)").getCell(1).getNumericCellValue()).isEqualTo(43_700_000d);
            assertThat(rowStartingWith(sheet, "PPh Badan Terutang").getCell(1).getNumericCellValue()).isEqualTo(4_807_000d);
            assertThat(rowStartingWith(sheet, "Kredit Pajak PPh 23").getCell(1).getNumericCellValue()).isEqualTo(-200_000d);
            assertThat(rowStartingWith(sheet, "Kredit Pajak PPh 25").getCell(1).getNumericCellValue()).isEqualTo(-300_000d);
            assertThat(rowStartingWith(sheet, "PPh Pasal 29 (Kurang/Lebih Bayar)").getCell(1).getNumericCellValue()).isEqualTo(4_307_000d);
        }
    }

    @Test
    @DisplayName("Adding fiscal adjustments through the form persists them and recomputes PKP and PPh; deleting one restores the totals")
    void shouldAddAndDeleteFiscalAdjustments() {
        navigateTo("/reports/rekonsiliasi-fiskal?year=" + ADJUSTMENT_YEAR);
        waitForPageLoad();
        assertThat(page.locator("#adjustment-items tr")).hasCount(1);
        assertThat(page.locator("#pkp")).hasText("0");

        addAdjustment("Biaya entertainment tanpa daftar nominatif", "PERMANENT", "POSITIVE", "5000000");
        assertThat(page.getByText("Koreksi fiskal berhasil ditambahkan")).isVisible();
        addAdjustment("Penyusutan fiskal lebih besar", "TEMPORARY", "NEGATIVE", "2000000");

        List<FiscalAdjustment> stored = fiscalAdjustmentRepository
                .findByYearOrderByAdjustmentCategoryAscDescriptionAsc(ADJUSTMENT_YEAR);
        assertThat(stored).extracting(FiscalAdjustment::getDescription, FiscalAdjustment::getAdjustmentCategory,
                        FiscalAdjustment::getAdjustmentDirection, a -> a.getAmount().stripTrailingZeros().toPlainString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Biaya entertainment tanpa daftar nominatif",
                                FiscalAdjustmentCategory.PERMANENT, FiscalAdjustmentDirection.POSITIVE, "5000000"),
                        org.assertj.core.groups.Tuple.tuple("Penyusutan fiskal lebih besar",
                                FiscalAdjustmentCategory.TEMPORARY, FiscalAdjustmentDirection.NEGATIVE, "2000000"));

        Locator rows = page.locator("#adjustment-items tr");
        assertThat(rows).hasCount(2);
        assertThat(rows.nth(1)).containsText("(2.000.000)");
        assertThat(page.locator("#total-positive")).hasText("5.000.000");
        assertThat(page.locator("#total-negative")).hasText("(2.000.000)");
        assertThat(page.locator("#net-adjustment")).hasText("3.000.000");
        assertThat(page.locator("#pkp")).hasText("3.000.000");
        assertThat(page.locator("#pph-terutang")).hasText("330.000");
        assertThat(page.locator("#pph29")).hasText("330.000");

        page.onceDialog(dialog -> dialog.accept());
        rows.nth(1).locator("button:has-text('Hapus')").click();
        page.waitForURL(url -> url.endsWith("/reports/rekonsiliasi-fiskal?year=" + ADJUSTMENT_YEAR));
        assertThat(page.getByText("Koreksi fiskal berhasil dihapus")).isVisible();
        assertThat(page.locator("#adjustment-items tr")).hasCount(1);
        assertThat(page.locator("#adjustment-items")).not().containsText("Penyusutan fiskal lebih besar");
        assertThat(page.locator("#net-adjustment")).hasText("5.000.000");
        assertThat(page.locator("#pkp")).hasText("5.000.000");
        assertThat(page.locator("#pph-terutang")).hasText("550.000");
        assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(ADJUSTMENT_YEAR))
                .extracting(FiscalAdjustment::getDescription)
                .containsExactly("Biaya entertainment tanpa daftar nominatif");
    }

    private void addAdjustment(String description, String category, String direction, String amount) {
        page.locator("#adj-description").fill(description);
        page.locator("#adj-category").selectOption(category);
        page.locator("#adj-direction").selectOption(direction);
        page.locator("#adj-amount").fill(amount);
        page.locator("#btn-add-adjustment").click();
        page.waitForURL(url -> url.endsWith("/reports/rekonsiliasi-fiskal?year=" + ADJUSTMENT_YEAR));
        waitForPageLoad();
    }

    private void submitRange(String path, String start, String end) {
        page.locator("#startDate").fill(start);
        page.locator("#endDate").fill(end);
        page.locator("#btn-generate").click();
        page.waitForURL(url -> url.contains(path + "?") && url.contains("startDate=" + start) && url.contains("endDate=" + end));
        waitForPageLoad();
    }

    private static void assertCells(Locator row, String... expected) {
        Locator cells = row.locator("td");
        assertThat(cells).hasCount(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(cells.nth(i)).hasText(expected[i]);
        }
    }

    private byte[] download(String buttonSelector, String contentType, String filename) {
        String href = page.locator(buttonSelector).getAttribute("href");
        APIResponse response = page.request().get(baseUrl() + href);
        assertThat(response.status()).as(href).isEqualTo(200);
        assertThat(response.headers().get("content-type")).isEqualTo(contentType);
        assertThat(response.headers().get("content-disposition")).isEqualTo("attachment; filename=\"" + filename + "\"");
        return response.body();
    }

    private static String pdfText(byte[] pdf) {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                text.append(extractor.getTextFromPage(p)).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable PDF", e);
        }
    }

    private static Workbook workbook(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static Row rowStartingWith(Sheet sheet, String firstCell) {
        for (Row row : sheet) {
            if (firstCell.equals(text(row, 0))) {
                return row;
            }
        }
        throw new AssertionError("No row starting with '" + firstCell + "' in sheet " + sheet.getSheetName());
    }

    private static int rowsStartingWith(Sheet sheet, String firstCell) {
        int count = 0;
        for (Row row : sheet) {
            if (firstCell.equals(text(row, 0))) {
                count++;
            }
        }
        return count;
    }

    private static String text(Row row, int column) {
        return row.getCell(column) == null ? "" : FORMATTER.formatCellValue(row.getCell(column)).trim();
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private Transaction post(LocalDate date, String templateName, TransactionStatus status, Line... lines) {
        String number = "TRD-" + UUID.randomUUID().toString().substring(0, 8);
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
            tx.setVoidReason(VoidReason.INPUT_ERROR);
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

    private static void faktur(TaxTransactionDetail d, String number, LocalDate date, String dpp, String ppn,
                               String npwp, String name) {
        d.setFakturNumber(number);
        d.setFakturDate(date);
        d.setTransactionCode("01");
        d.setDpp(new BigDecimal(dpp));
        d.setPpn(new BigDecimal(ppn));
        d.setCounterpartyIdType("TIN");
        d.setCounterpartyNpwp(npwp);
        d.setCounterpartyName(name);
    }

    private void adjustment(String description, FiscalAdjustmentCategory category,
                            FiscalAdjustmentDirection direction, String amount) {
        FiscalAdjustment adjustment = new FiscalAdjustment();
        adjustment.setYear(YEAR);
        adjustment.setDescription(description);
        adjustment.setAdjustmentCategory(category);
        adjustment.setAdjustmentDirection(direction);
        adjustment.setAmount(new BigDecimal(amount));
        fiscalAdjustmentRepository.save(adjustment);
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String faktur() {
        return "010." + digits(3) + "-92." + digits(8);
    }

    private static String digits(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
