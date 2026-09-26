package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.FiscalLossCarryforward;
import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.taxdetail.TaxDetailTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.FiscalLossCarryforwardRepository;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.SptTahunanExportService;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
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
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPT Tahunan Badan checklist page on data this class creates for {@link #YEAR} (reserved for it):
 *
 * - 02-10 Pendapatan Jasa + PPN: Dr 1.1.02 55.5M / Cr 4.1.01 50M / Cr 2.1.03 5.5M
 * - 05-10 Pembayaran Jasa dengan PPh 23: Dr 5.1.20 20M / Cr 1.1.02 19.6M / Cr 2.1.21 0.4M
 * - fiscal adjustments: +2M permanent, (1M) temporary -> penghasilan neto fiskal 31M
 * - fiscal loss from {@link #LOSS_YEAR} of 7.5M, compensable from the following year -> PKP 23.5M
 * - PPh Badan on PKP after loss compensation: 23,500,000 x 22% x 50% (Pasal 31E, revenue < 4.8B) = 2,585,000;
 *   no PPh 23/25 credits, so PPh 29 = 2,585,000
 * - fiscal periods: January-October closed/filed, November-December open
 * {@link #LOSS_YEAR} itself has no journals, adjustments or periods in this context.
 */
@DisplayName("SPT Tahunan Badan Checklist")
@Import(TaxDetailTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SptChecklistFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2095;
    private static final int LOSS_YEAR = 2094;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private FiscalAdjustmentRepository fiscalAdjustmentRepository;
    @Autowired private FiscalPeriodRepository fiscalPeriodRepository;
    @Autowired private FiscalLossCarryforwardRepository lossRepository;
    @Autowired private FixedAssetRepository fixedAssetRepository;
    @Autowired private SptTahunanExportService sptTahunanExportService;
    @Autowired private TaxReportDetailService taxReportDetailService;

    private UUID lossId;

    @BeforeAll
    void createYearData() {
        for (int year : List.of(YEAR, LOSS_YEAR)) {
            assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                    .as("Another test posted into reserved SPT year " + year).isEmpty();
            assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(year))
                    .as("Another test added fiscal adjustments to reserved SPT year " + year).isEmpty();
            assertThat(fiscalPeriodRepository.findByYear(year))
                    .as("Another test created fiscal periods in reserved SPT year " + year).isEmpty();
        }
        assertThat(lossRepository.findAllByOrderByOriginYearDesc())
                .as("Another test left a fiscal loss that is compensable in " + YEAR)
                .noneMatch(l -> l.getOriginYear() < YEAR && l.getExpiryYear() >= YEAR);

        post(LocalDate.of(YEAR, 2, 10), "Pendapatan Jasa + PPN",
                line("1.1.02", "55500000", "0"), line("4.1.01", "0", "50000000"), line("2.1.03", "0", "5500000"));
        post(LocalDate.of(YEAR, 5, 10), "Pembayaran Jasa dengan PPh 23",
                line("5.1.20", "20000000", "0"), line("1.1.02", "0", "19600000"), line("2.1.21", "0", "400000"));

        adjustment("Beban sumbangan", FiscalAdjustmentCategory.PERMANENT, FiscalAdjustmentDirection.POSITIVE, "2000000");
        adjustment("Selisih penyusutan", FiscalAdjustmentCategory.TEMPORARY, FiscalAdjustmentDirection.NEGATIVE, "1000000");

        for (int month = 1; month <= 12; month++) {
            FiscalPeriod period = new FiscalPeriod();
            period.setYear(YEAR);
            period.setMonth(month);
            if (month <= 10) {
                period.setStatus(month <= 6 ? FiscalPeriodStatus.TAX_FILED : FiscalPeriodStatus.MONTH_CLOSED);
                period.setMonthClosedAt(LocalDateTime.now());
                period.setMonthClosedBy("admin");
                if (month <= 6) {
                    period.setTaxFiledAt(LocalDateTime.now());
                    period.setTaxFiledBy("admin");
                }
            }
            fiscalPeriodRepository.save(period);
        }

        FiscalLossCarryforward loss = new FiscalLossCarryforward();
        loss.setOriginYear(LOSS_YEAR);
        loss.setOriginalAmount(new BigDecimal("7500000"));
        loss.setNotes("Rugi fiskal " + LOSS_YEAR);
        lossId = lossRepository.save(loss).getId();
    }

    @AfterAll
    void removeLoss() {
        lossRepository.deleteById(lossId);
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Without a year the checklist shows the previous year, selectable among the last three")
    void shouldDefaultToPreviousYear() {
        navigateTo("/reports/spt-checklist");
        waitForPageLoad();

        int current = Year.now().getValue();
        assertThat(page.locator("#page-title")).hasText("Checklist SPT Tahunan Badan");
        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + (current - 1));
        assertThat(page.locator("#year option").evaluateAll("os => os.map(o => o.value)"))
                .isEqualTo(List.of(String.valueOf(current - 1), String.valueOf(current - 2), String.valueOf(current - 3)));
        assertThat(page.locator("#year")).hasValue(String.valueOf(current - 1));
    }

    @Test
    @DisplayName("Checklist of a year reports revenue, net income, adjustments, PPh Badan, closed periods, assets, payroll and compensable losses")
    void shouldReportYearReadiness() {
        navigateTo("/reports/spt-checklist?year=" + YEAR);
        waitForPageLoad();

        assertThat(page.locator("#year")).hasValue(String.valueOf(YEAR));
        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + YEAR);
        assertThat(page.locator("[data-testid='checklist-errors']")).hasCount(0);
        assertItem("#check-financial-statements", "bg-green-50", "Laporan Keuangan",
                "Pendapatan: Rp 50.000.000 — Laba Bersih: Rp 30.000.000");
        assertItem("#check-fiscal-adjustments", "bg-green-50", "Koreksi Fiskal", "2 item koreksi fiskal");
        assertItem("#check-pph-badan", "bg-green-50", "PPh Badan Terutang",
                "PPh Terutang: Rp 2.585.000 — PPh 29: Rp 2.585.000");
        assertItem("#check-fiscal-periods", "bg-yellow-50", "Periode Fiskal", "10/12 bulan ditutup");
        assertItem("#check-payroll", "bg-gray-50", "Payroll & PPh 21", "Tidak ada data payroll");
        assertItem("#check-loss-carryforward", "bg-blue-50", "Kompensasi Kerugian", "Sisa rugi fiskal: Rp 7.500.000");

        LocalDate yearEnd = LocalDate.of(YEAR, 12, 31);
        long assets = fixedAssetRepository.findAll().stream()
                .filter(a -> a.getStatus() != AssetStatus.DISPOSED
                        || (a.getDisposalDate() != null && a.getDisposalDate().getYear() == YEAR))
                .filter(a -> !a.getPurchaseDate().isAfter(yearEnd))
                .count();
        assertItem("#check-depreciation", assets > 0 ? "bg-green-50" : "bg-gray-50", "Penyusutan Aset Tetap",
                assets > 0 ? assets + " aset tercatat" : "Tidak ada aset tetap");

        takeManualScreenshot("spt-checklist/spt-checklist-page");
        page.locator("text=Download Lampiran SPT").scrollIntoViewIfNeeded();
        takeManualScreenshot("spt-checklist/spt-checklist-downloads");
    }

    @Test
    @DisplayName("The six Download Lampiran SPT links return the year's workbooks to a browser session")
    void shouldDownloadSptAttachments() throws IOException {
        navigateTo("/reports/spt-checklist?year=" + YEAR);
        waitForPageLoad();

        try (Workbook l1 = download("#download-spt-l1", "spt-l1-rekonsiliasi-fiskal-" + YEAR + ".xlsx")) {
            assertThat(rows(l1.getSheet("L1 Rekonsiliasi Fiskal"))).containsExactly(
                    "LAMPIRAN I - PENGHITUNGAN PENGHASILAN NETO FISKAL",
                    "Tahun Pajak: " + YEAR,
                    "Kode Akun|Uraian|Jumlah (Rp)",
                    "|I. PEREDARAN USAHA",
                    "4.1.01|" + account("4.1.01").getAccountName() + "|50000000",
                    "|Total I. PEREDARAN USAHA|50000000",
                    "|Harga Pokok Penjualan|0",
                    "|LABA BRUTO (I - HPP)|50000000",
                    "|II. BIAYA USAHA",
                    "5.1.20|" + account("5.1.20").getAccountName() + "|20000000",
                    "|Total II. BIAYA USAHA|20000000",
                    "|PENGHASILAN NETO DARI USAHA (I - II)|30000000",
                    "|III. PENGHASILAN DARI LUAR USAHA",
                    "|Total III. PENGHASILAN DARI LUAR USAHA|0",
                    "|IV. BIAYA DARI LUAR USAHA",
                    "|Total IV. BIAYA DARI LUAR USAHA|0",
                    "|PENGHASILAN NETO DARI LUAR USAHA (III - IV)|0",
                    "|PENGHASILAN NETO KOMERSIAL|30000000",
                    "|V. PENYESUAIAN FISKAL POSITIF",
                    "|Beban sumbangan (PERMANENT)|2000000",
                    "|Total V. PENYESUAIAN FISKAL POSITIF|2000000",
                    "|VI. PENYESUAIAN FISKAL NEGATIF",
                    "|Selisih penyusutan (TEMPORARY)|1000000",
                    "|Total VI. PENYESUAIAN FISKAL NEGATIF|1000000",
                    "|PENGHASILAN NETO FISKAL|31000000",
                    "|KOMPENSASI KERUGIAN FISKAL",
                    "|Rugi Fiskal Tahun " + LOSS_YEAR + " (kadaluarsa " + (LOSS_YEAR + 5) + ")|7500000",
                    "|Total Kompensasi Kerugian|7500000",
                    "|PENGHASILAN KENA PAJAK (PKP)|23500000",
                    "|VII. PPh BADAN TERUTANG",
                    "|Metode: Fasilitas Pasal 31E penuh (omset < 4,8M)",
                    "|PPh Terutang|2585000",
                    "|VIII. KREDIT PAJAK",
                    "|PPh 23 yang dipotong pihak lain|0",
                    "|PPh 25 yang sudah dibayar|0",
                    "|Total Kredit Pajak|0",
                    "|PPh KURANG / (LEBIH) BAYAR (PPh 29)|2585000");
        }
        try (Workbook l4 = download("#download-spt-l4", "spt-l4-penghasilan-final-" + YEAR + ".xlsx")) {
            assertThat(rows(l4.getSheet("L4 Penghasilan Final"))).containsExactly(
                    "LAMPIRAN IV - PENGHASILAN YANG DIKENAKAN PPh FINAL",
                    "Tahun Pajak: " + YEAR,
                    "No|Kode Objek Pajak|Uraian|Jumlah Bruto (Rp)|Tarif (%)|PPh Final (Rp)",
                    "||TOTAL|0||0");
        }
        try (Workbook l9 = download("#download-spt-l9", "spt-l9-penyusutan-" + YEAR + ".xlsx")) {
            List<String> expected = new ArrayList<>();
            expected.add("NamaHarta|KelompokHarta|TanggalPerolehan|HargaPerolehan|MetodePenyusutan|MasaManfaat"
                    + "|PenyusutanTahunIni|AkumulasiPenyusutan|NilaiBuku");
            sptTahunanExportService.generateL9(YEAR).items().forEach(item -> expected.add(item.assetName() + "|"
                    + item.fiscalGroup() + "|" + item.acquisitionDate().format(DMY) + "|"
                    + plain(item.acquisitionCost()) + "|" + ("Garis Lurus".equals(item.depreciationMethod()) ? "GL" : "SM")
                    + "|" + item.usefulLifeYears() + "|" + plain(item.depreciationThisYear()) + "|"
                    + plain(item.accumulatedDepreciation()) + "|" + plain(item.bookValue())));
            assertThat(rows(l9.getSheet("DATA"))).containsExactlyElementsOf(expected);
            assertThat(rows(l9.getSheet("REF"))).contains("Kelompok I|Bukan Bangunan - Kelompok I|4", "GL|Garis Lurus (Straight-Line)");
        }
        try (Workbook transkrip = download("#download-spt-transkrip-8a", "spt-transkrip-8a-" + YEAR + ".xlsx")) {
            assertThat(transkrip.getSheetAt(0).getSheetName()).isEqualTo("Neraca");
            assertThat(rows(transkrip.getSheet("Laba Rugi"))).contains(
                    "Periode 1 Januari - 31 Desember " + YEAR,
                    "4.1.01|" + account("4.1.01").getAccountName() + "|50000000",
                    "|Total PENDAPATAN|50000000",
                    "5.1.20|" + account("5.1.20").getAccountName() + "|20000000",
                    "|Total BEBAN|20000000",
                    "|LABA (RUGI) BERSIH|30000000");
        }
        try (Workbook bpa1 = download("#download-spt-ebupot-pph21", "ebupot-pph21-1721a1-" + YEAR + ".xlsx")) {
            assertThat(rows(bpa1.getSheet("DATA"))).containsExactly(
                    "NPWP|NIK|NamaPegawai|StatusPTKP|MasaKerja|PenghasilanBruto|BiayaJabatan|IuranBPJS|PenghasilanNeto"
                            + "|PTKP|PKP|PPh21Terutang|PPh21Dipotong|PPh21KurangLebihBayar",
                    "||TOTAL|||0||||||0|0|0");
        }
        try (Workbook rekon = download("#download-spt-rekonsiliasi-fiskal", "rekonsiliasi-fiskal-" + YEAR + ".xlsx")) {
            assertThat(rows(rekon.getSheet("Rekonsiliasi Fiskal"))).containsExactly(
                    "PT ArtiVisi Intermedia",
                    "REKONSILIASI FISKAL",
                    "Tahun Pajak " + YEAR,
                    "A. Laba Rugi Komersial",
                    "Pendapatan|50000000",
                    "Beban|-20000000",
                    "Laba Bersih Komersial|30000000",
                    "B. Koreksi Fiskal",
                    "Beban sumbangan (Beda Tetap, Koreksi Positif)|2000000",
                    "Selisih penyusutan (Beda Waktu, Koreksi Negatif)|-1000000",
                    "Koreksi Fiskal Neto|1000000",
                    "C. PKP & PPh Badan",
                    "Penghasilan Neto Fiskal|31000000",
                    "Kompensasi Kerugian Fiskal|-7500000",
                    "Penghasilan Kena Pajak (PKP)|23500000",
                    "PPh Badan Terutang|2585000",
                    "Kredit Pajak PPh 23",
                    "Kredit Pajak PPh 25",
                    "PPh Pasal 29 (Kurang/Lebih Bayar)|2585000");
        }
    }

    @Test
    @DisplayName("Rekonsiliasi fiskal page, PPh Badan API data, lampiran and Coretax export compute PPh on PKP after loss compensation")
    void shouldComputePphBadanAfterLossCompensation() {
        navigateTo("/reports/rekonsiliasi-fiskal?year=" + YEAR);
        waitForPageLoad();

        assertThat(page.locator("#penghasilan-neto-fiskal")).hasText("31.000.000");
        assertThat(page.locator("#loss-compensation")).hasText("(7.500.000)");
        assertThat(page.locator("#pkp")).hasText("23.500.000");
        assertThat(page.locator("#calculation-method")).hasText("Fasilitas Pasal 31E penuh (omset < 4,8M)");
        assertThat(page.locator("#pph-terutang")).hasText("2.585.000");
        assertThat(page.locator("#total-kredit")).hasText("(0)");
        assertThat(page.locator("#pph29")).hasText("2.585.000");

        var rekon = taxReportDetailService.generateRekonsiliasiFiskal(YEAR);
        assertThat(rekon.pkp()).isEqualByComparingTo("31000000");
        assertThat(rekon.lossCompensation()).isEqualByComparingTo("7500000");
        assertThat(rekon.pkpAfterLoss()).isEqualByComparingTo("23500000");
        assertThat(rekon.pphBadan().pkp()).isEqualByComparingTo("23500000");
        assertThat(rekon.pphBadan().pkpRounded()).isEqualByComparingTo("23500000");
        assertThat(rekon.pphBadan().pphTerutang()).isEqualByComparingTo("2585000");
        assertThat(rekon.pphBadan().pph29()).isEqualByComparingTo("2585000");

        var lampiran = sptTahunanExportService.generateConsolidatedLampiran(YEAR);
        assertThat(lampiran.lampiranI().penghasilanNetoFiskal()).isEqualByComparingTo("31000000");
        assertThat(lampiran.lampiranI().totalKompensasi()).isEqualByComparingTo("7500000");
        assertThat(lampiran.lampiranI().penghasilanKenaPajak()).isEqualByComparingTo("23500000");
        assertThat(lampiran.pphBadan().penghasilanKenaPajak()).isEqualByComparingTo("23500000");
        assertThat(lampiran.pphBadan().pphTerutang()).isEqualByComparingTo("2585000");
        assertThat(lampiran.pphBadan().pph29KurangBayar()).isEqualByComparingTo("2585000");

        var induk = sptTahunanExportService.generateCoretaxExport(YEAR).induk();
        assertThat(induk.penghasilanNetoFiskal()).isEqualByComparingTo("31000000");
        assertThat(induk.kompensasiKerugian()).isEqualByComparingTo("7500000");
        assertThat(induk.penghasilanKenaPajak()).isEqualByComparingTo("23500000");
        assertThat(induk.penghasilanKenaPajakPembulatan()).isEqualByComparingTo("23500000");
        assertThat(induk.pphTerutang()).isEqualByComparingTo("2585000");
        assertThat(induk.pph29KurangBayar()).isEqualByComparingTo("2585000");
    }

    @Test
    @DisplayName("A year without activity shows every item as missing, and a loss is not compensable in its own year")
    void shouldReportMissingDataForEmptyYear() {
        navigateTo("/reports/spt-checklist?year=" + LOSS_YEAR);
        waitForPageLoad();

        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + LOSS_YEAR);
        assertItem("#check-financial-statements", "bg-yellow-50", "Laporan Keuangan", "Belum ada transaksi di tahun ini");
        assertItem("#check-fiscal-adjustments", "bg-yellow-50", "Koreksi Fiskal", "Belum ada koreksi fiskal");
        assertItem("#check-pph-badan", "bg-green-50", "PPh Badan Terutang", "PPh Terutang: Rp 0 — PPh 29: Rp 0");
        assertItem("#check-fiscal-periods", "bg-yellow-50", "Periode Fiskal", "0/12 bulan ditutup");
        assertItem("#check-payroll", "bg-gray-50", "Payroll & PPh 21", "Tidak ada data payroll");
        assertItem("#check-loss-carryforward", "bg-gray-50", "Kompensasi Kerugian",
                "Tidak ada rugi fiskal yang dapat dikompensasi");
    }

    @Test
    @DisplayName("Choosing another year in the form reloads the checklist for that year")
    void shouldChangeYearThroughForm() {
        navigateTo("/reports/spt-checklist?year=" + YEAR);
        waitForPageLoad();

        String twoYearsAgo = String.valueOf(Year.now().getValue() - 2);
        page.locator("#year").selectOption(twoYearsAgo);
        page.locator("button:has-text('Tampilkan')").click();
        page.waitForURL(url -> url.endsWith("/reports/spt-checklist?year=" + twoYearsAgo));

        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + twoYearsAgo);
        assertThat(page.locator("#year")).hasValue(twoYearsAgo);
        assertThat(page.locator("#year option[value='" + YEAR + "']")).hasCount(0);
    }

    @Test
    @DisplayName("Sidebar entry under Laporan opens the checklist")
    void shouldNavigateFromSidebar() {
        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-laporan").click();
        page.locator("#nav-spt-checklist").click();
        page.waitForURL(url -> url.endsWith("/reports/spt-checklist"));

        assertThat(page.locator("#page-title")).hasText("Checklist SPT Tahunan Badan");
        assertThat(page.locator("#nav-spt-checklist")).hasClass(Pattern.compile(".*bg-primary-600.*"));
    }

    private Workbook download(String linkSelector, String filename) throws IOException {
        String href = page.locator(linkSelector).getAttribute("href");
        assertThat(href).isEqualTo(expectedHref(linkSelector));
        APIResponse response = page.request().get(baseUrl() + href);
        assertThat(response.status()).as(href).isEqualTo(200);
        assertThat(response.headers().get("content-type")).isEqualTo(XLSX);
        assertThat(response.headers().get("content-disposition"))
                .isEqualTo("attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);
        return new XSSFWorkbook(new ByteArrayInputStream(response.body()));
    }

    private static String expectedHref(String linkSelector) {
        return "/reports/spt-tahunan/" + linkSelector.substring("#download-spt-".length()) + "?year=" + YEAR;
    }

    private static List<String> rows(Sheet sheet) {
        List<String> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cells.add("");
                } else if (cell.getCellType() == CellType.NUMERIC) {
                    cells.add(plain(BigDecimal.valueOf(cell.getNumericCellValue())));
                } else {
                    cells.add(cell.getStringCellValue());
                }
            }
            String joined = String.join("|", cells).replaceAll("\\|+$", "");
            if (!joined.isEmpty()) {
                rows.add(joined);
            }
        }
        return rows;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private void assertItem(String selector, String background, String title, String detail) {
        Locator item = page.locator(selector);
        assertThat(item).hasClass(Pattern.compile(".*\\b" + background + "\\b.*"));
        assertThat(item.locator("p.font-medium")).hasText(title);
        assertThat(item.locator("p.text-sm")).hasText(detail);
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private void post(LocalDate date, String templateName, Line... lines) {
        String number = "SPT-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(date);
        tx.setDescription(templateName + " " + number);
        tx.setAmount(lines[0].debit());
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
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
}
