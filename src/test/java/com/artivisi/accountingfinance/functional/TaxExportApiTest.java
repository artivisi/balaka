package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.taxdetail.TaxDetailTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tax export API on journals and tax details this class posts into {@link #YEAR}, a past year
 * reserved for it in the tax-detail test context (the journal-entry API rejects future dates).
 *
 * - 03-05 Pendapatan Jasa + PPN (PRJ-2024-001): Dr 1.1.02 22.2M / Cr 4.1.01 20M / Cr 2.1.03 2.2M; faktur keluaran
 * - 03-12 Pembelian dengan PPN: Dr 5.1.21 5M / Dr 1.1.25 0.55M / Cr 1.1.02 5.55M; faktur masukan
 * - 03-20 Pembayaran Jasa dengan PPh 23: Dr 5.1.20 8M / Cr 1.1.02 7.84M / Cr 2.1.21 0.16M; bupot issued
 * - 04-10 Pendapatan Jasa + PPh 23 Dipotong: Dr 1.1.02 9.8M / Dr 1.1.26 0.2M / Cr 4.1.02 10M; bupot received
 * - 05-02 Pendapatan Jasa + PPN: Dr 1.1.02 1.11M / Cr 4.1.01 1M / Cr 2.1.03 0.11M; faktur keluaran (outside Mar-Apr)
 * - fiscal adjustment: positive permanent 1,000,000, pasal 9(1)(a)
 * Revenue 31M, expense 13M, commercial net income 18M, PKP 19M.
 */
@DisplayName("Tax Export API - Functional Tests")
@Import(TaxDetailTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaxExportApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2005;
    private static final String ADJUSTMENT = "Beban Jamuan Tanpa Daftar Nominatif";
    private static final DataFormatter FORMATTER = new DataFormatter();

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private FiscalAdjustmentRepository fiscalAdjustmentRepository;
    @Autowired private CompanyConfigRepository companyConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private APIRequestContext apiContext;
    private String accessToken;

    private String fakturKeluaran;
    private String fakturKeluaranMay;
    private String fakturMasukan;
    private String bupotIssued;
    private String bupotReceived;
    private String salesNumber;
    private String serviceNumber;

    @BeforeAll
    void postTaxData() {
        assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .as("Another test posted into reserved tax export year " + YEAR)
                .isEmpty();

        fakturKeluaran = faktur();
        fakturKeluaranMay = faktur();
        fakturMasukan = faktur();
        bupotIssued = "BP23-" + YEAR + "-" + digits(6);
        bupotReceived = "BPR23-" + YEAR + "-" + digits(6);

        Transaction sales = post(LocalDate.of(YEAR, 3, 5), "Pendapatan Jasa + PPN", "PRJ-2024-001",
                line("1.1.02", "22200000", "0"), line("4.1.01", "0", "20000000"), line("2.1.03", "0", "2200000"));
        salesNumber = sales.getTransactionNumber();
        detail(sales, TaxType.PPN_KELUARAN, d -> {
            d.setFakturNumber(fakturKeluaran);
            d.setFakturDate(LocalDate.of(YEAR, 3, 5));
            d.setTransactionCode("01");
            d.setDpp(new BigDecimal("20000000"));
            d.setPpn(new BigDecimal("2200000"));
            d.setCounterpartyIdType("TIN");
            d.setCounterpartyNpwp("01.310.523.4-091.000");
            d.setCounterpartyName("PT Bank Mandiri Tbk");
        });

        Transaction purchase = post(LocalDate.of(YEAR, 3, 12), "Pembelian dengan PPN", null,
                line("5.1.21", "5000000", "0"), line("1.1.25", "550000", "0"), line("1.1.02", "0", "5550000"));
        detail(purchase, TaxType.PPN_MASUKAN, d -> {
            d.setFakturNumber(fakturMasukan);
            d.setFakturDate(LocalDate.of(YEAR, 3, 12));
            d.setTransactionCode("01");
            d.setDpp(new BigDecimal("5000000"));
            d.setPpn(new BigDecimal("550000"));
            d.setCounterpartyIdType("TIN");
            d.setCounterpartyNpwp("02.111.222.3-444.000");
            d.setCounterpartyName("PT Pemasok Lisensi");
        });

        Transaction service = post(LocalDate.of(YEAR, 3, 20), "Pembayaran Jasa dengan PPh 23", null,
                line("5.1.20", "8000000", "0"), line("1.1.02", "0", "7840000"), line("2.1.21", "0", "160000"));
        serviceNumber = service.getTransactionNumber();
        detail(service, TaxType.PPH_23, d -> {
            d.setBupotNumber(bupotIssued);
            d.setTaxObjectCode("24-104-01");
            d.setGrossAmount(new BigDecimal("8000000"));
            d.setTaxRate(new BigDecimal("2.00"));
            d.setTaxAmount(new BigDecimal("160000"));
            d.setCounterpartyIdType("TIN");
            d.setCounterpartyNpwp("03.555.666.7-888.000");
            d.setCounterpartyName("CV Konsultan Jaringan");
        });

        Transaction withheld = post(LocalDate.of(YEAR, 4, 10), "Pendapatan Jasa + PPh 23 Dipotong", "PRJ-2024-001",
                line("1.1.02", "9800000", "0"), line("1.1.26", "200000", "0"), line("4.1.02", "0", "10000000"));
        detail(withheld, TaxType.PPH_23, d -> {
            d.setBupotNumber(bupotReceived);
            d.setTaxObjectCode("24-104-03");
            d.setGrossAmount(new BigDecimal("10000000"));
            d.setTaxRate(new BigDecimal("2.00"));
            d.setTaxAmount(new BigDecimal("200000"));
            d.setCounterpartyIdType("TIN");
            d.setCounterpartyNpwp("01.310.523.4-091.000");
            d.setCounterpartyName("PT Bank Mandiri Tbk");
        });

        Transaction maySales = post(LocalDate.of(YEAR, 5, 2), "Pendapatan Jasa + PPN", "PRJ-2024-001",
                line("1.1.02", "1110000", "0"), line("4.1.01", "0", "1000000"), line("2.1.03", "0", "110000"));
        detail(maySales, TaxType.PPN_KELUARAN, d -> {
            d.setFakturNumber(fakturKeluaranMay);
            d.setFakturDate(LocalDate.of(YEAR, 5, 2));
            d.setTransactionCode("01");
            d.setDpp(new BigDecimal("1000000"));
            d.setPpn(new BigDecimal("110000"));
            d.setCounterpartyIdType("TIN");
            d.setCounterpartyNpwp("01.310.523.4-091.000");
            d.setCounterpartyName("PT Bank Mandiri Tbk");
        });

        FiscalAdjustment adjustment = new FiscalAdjustment();
        adjustment.setYear(YEAR);
        adjustment.setDescription(ADJUSTMENT);
        adjustment.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
        adjustment.setAdjustmentDirection(FiscalAdjustmentDirection.POSITIVE);
        adjustment.setAmount(new BigDecimal("1000000"));
        adjustment.setAccountCode("5.9.99");
        adjustment.setPasal("9(1)(a)");
        fiscalAdjustmentRepository.save(adjustment);
    }

    @BeforeEach
    void setUp() throws Exception {
        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        accessToken = authenticateViaDeviceFlow();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    // ==================== CORETAX EXCEL EXPORTS ====================

    @Test
    @DisplayName("e-Faktur Keluaran export for March-April holds only the March output faktur")
    void testExportEfakturKeluaran() throws Exception {
        APIResponse response = getOk("/api/tax-export/efaktur-keluaran?startMonth=" + YEAR + "-03&endMonth=" + YEAR + "-04");
        assertXlsxAttachment(response, "efaktur-keluaran-" + YEAR + "-03-" + YEAR + "-04.xlsx");

        try (Workbook workbook = workbook(response)) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(data.getLastRowNum()).isEqualTo(1);
            Row row = data.getRow(1);
            assertThat(text(row, 0)).isEqualTo("01");
            assertThat(text(row, 1)).isEqualTo(fakturKeluaran);
            assertThat(text(row, 2)).isEqualTo("05/03/" + YEAR);
            assertThat(text(row, 3)).isEqualTo(company().getNpwp());
            assertThat(text(row, 6)).isEqualTo("01.310.523.4-091.000");
            assertThat(text(row, 8)).isEqualTo("PT Bank Mandiri Tbk");
            assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(22_200_000d);
            assertThat(row.getCell(12).getNumericCellValue()).isEqualTo(20_000_000d);
            assertThat(row.getCell(13).getNumericCellValue()).isEqualTo(2_200_000d);
        }
    }

    @Test
    @DisplayName("e-Faktur Masukan export holds the input-VAT faktur")
    void testExportEfakturMasukan() throws Exception {
        APIResponse response = getOk("/api/tax-export/efaktur-masukan?startMonth=" + YEAR + "-03&endMonth=" + YEAR + "-04");
        assertXlsxAttachment(response, "efaktur-masukan-" + YEAR + "-03-" + YEAR + "-04.xlsx");

        try (Workbook workbook = workbook(response)) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(data.getLastRowNum()).isEqualTo(1);
            assertThat(text(data.getRow(1), 1)).isEqualTo(fakturMasukan);
            assertThat(text(data.getRow(1), 8)).isEqualTo("PT Pemasok Lisensi");
            assertThat(data.getRow(1).getCell(13).getNumericCellValue()).isEqualTo(550_000d);
        }
    }

    @Test
    @DisplayName("e-Bupot Unifikasi export holds both PPh 23 withholdings of March-April ordered by date")
    void testExportBupotUnifikasi() throws Exception {
        APIResponse response = getOk("/api/tax-export/bupot-unifikasi?startMonth=" + YEAR + "-03&endMonth=" + YEAR + "-04");
        assertXlsxAttachment(response, "bupot-unifikasi-" + YEAR + "-03-" + YEAR + "-04.xlsx");

        try (Workbook workbook = workbook(response)) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(data.getLastRowNum()).isEqualTo(2);
            assertThat(text(data.getRow(1), 0)).isEqualTo(bupotIssued);
            assertThat(text(data.getRow(1), 1)).isEqualTo("20/03/" + YEAR);
            assertThat(text(data.getRow(1), 8)).isEqualTo("24-104-01");
            assertThat(data.getRow(1).getCell(11).getNumericCellValue()).isEqualTo(160_000d);
            assertThat(text(data.getRow(2), 0)).isEqualTo(bupotReceived);
            assertThat(data.getRow(2).getCell(9).getNumericCellValue()).isEqualTo(10_000_000d);
        }
    }

    // ==================== DETAIL REPORTS ====================

    @Test
    @DisplayName("PPN detail JSON lists the period's output and input faktur with totals")
    void testPpnDetailJson() throws Exception {
        JsonNode body = json("/api/tax-export/ppn-detail?startDate=" + YEAR + "-03-01&endDate=" + YEAR + "-04-30");
        assertThat(body.get("reportType").asText()).isEqualTo("ppn-detail");

        JsonNode data = body.get("data");
        assertThat(data.get("keluaranItems")).hasSize(1);
        JsonNode keluaran = data.get("keluaranItems").get(0);
        assertThat(keluaran.get("transactionNumber").asText()).isEqualTo(salesNumber);
        assertThat(keluaran.get("transactionDate").asText()).isEqualTo(YEAR + "-03-05");
        assertThat(keluaran.get("fakturNumber").asText()).isEqualTo(fakturKeluaran);
        assertThat(keluaran.get("counterpartyName").asText()).isEqualTo("PT Bank Mandiri Tbk");
        assertThat(keluaran.get("dpp").decimalValue()).isEqualByComparingTo("20000000");
        assertThat(keluaran.get("ppn").decimalValue()).isEqualByComparingTo("2200000");
        assertThat(data.get("masukanItems")).hasSize(1);
        assertThat(data.get("masukanItems").get(0).get("fakturNumber").asText()).isEqualTo(fakturMasukan);

        JsonNode totals = data.get("totals");
        assertThat(totals.get("totalDppKeluaran").decimalValue()).isEqualByComparingTo("20000000");
        assertThat(totals.get("totalPpnKeluaran").decimalValue()).isEqualByComparingTo("2200000");
        assertThat(totals.get("totalDppMasukan").decimalValue()).isEqualByComparingTo("5000000");
        assertThat(totals.get("totalPpnMasukan").decimalValue()).isEqualByComparingTo("550000");

        JsonNode wholeYear = json("/api/tax-export/ppn-detail?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31")
                .get("data");
        assertThat(wholeYear.get("keluaranItems")).hasSize(2);
        assertThat(wholeYear.get("totals").get("totalPpnKeluaran").decimalValue()).isEqualByComparingTo("2310000");
    }

    @Test
    @DisplayName("PPh 23 detail JSON lists both withholdings with gross and tax totals")
    void testPph23DetailJson() throws Exception {
        JsonNode body = json("/api/tax-export/pph23-detail?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31");
        assertThat(body.get("reportType").asText()).isEqualTo("pph23-detail");

        JsonNode data = body.get("data");
        assertThat(data.get("items")).hasSize(2);
        JsonNode issued = data.get("items").get(0);
        assertThat(issued.get("transactionNumber").asText()).isEqualTo(serviceNumber);
        assertThat(issued.get("bupotNumber").asText()).isEqualTo(bupotIssued);
        assertThat(issued.get("taxObjectCode").asText()).isEqualTo("24-104-01");
        assertThat(issued.get("grossAmount").decimalValue()).isEqualByComparingTo("8000000");
        assertThat(issued.get("taxRate").decimalValue()).isEqualByComparingTo("2");
        assertThat(issued.get("taxAmount").decimalValue()).isEqualByComparingTo("160000");
        assertThat(data.get("items").get(1).get("bupotNumber").asText()).isEqualTo(bupotReceived);
        assertThat(data.get("totals").get("totalGross").decimalValue()).isEqualByComparingTo("18000000");
        assertThat(data.get("totals").get("totalTax").decimalValue()).isEqualByComparingTo("360000");
    }

    @Test
    @DisplayName("PPN and PPh 23 detail Excel carry the same rows and totals as the JSON")
    void testDetailExcel() throws Exception {
        APIResponse ppn = getOk("/api/tax-export/ppn-detail?startDate=" + YEAR + "-03-01&endDate=" + YEAR + "-04-30&format=excel");
        assertXlsxAttachment(ppn, "ppn-detail-" + YEAR + "-03-01-" + YEAR + "-04-30.xlsx");
        try (Workbook workbook = workbook(ppn)) {
            Sheet sheet = workbook.getSheet("Rincian PPN");
            assertThat(number(rowStartingWith(sheet, fakturKeluaran), 6)).isEqualTo(2_200_000d);
            assertThat(number(rowStartingWith(sheet, fakturMasukan), 6)).isEqualTo(550_000d);
            assertThat(number(rowStartingWith(sheet, "Total PPN Keluaran"), 5)).isEqualTo(20_000_000d);
            assertThat(number(rowStartingWith(sheet, "Total PPN Masukan"), 6)).isEqualTo(550_000d);
        }

        APIResponse pph23 = getOk("/api/tax-export/pph23-detail?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31&format=excel");
        assertXlsxAttachment(pph23, "pph23-detail-" + YEAR + "-01-01-" + YEAR + "-12-31.xlsx");
        try (Workbook workbook = workbook(pph23)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row issued = rowStartingWith(sheet, bupotIssued);
            assertThat(text(issued, 1)).isEqualTo("24-104-01");
            assertThat(number(issued, 4)).isEqualTo(8_000_000d);
            assertThat(text(issued, 5)).isEqualTo("2.00%");
            assertThat(number(issued, 6)).isEqualTo(160_000d);
            assertThat(number(rowStartingWith(sheet, "TOTAL"), 6)).isEqualTo(360_000d);
        }
    }

    // ==================== FISCAL RECONCILIATION AND PPh BADAN ====================

    @Test
    @DisplayName("Rekonsiliasi fiskal adds the positive adjustment to 18M commercial income: PKP 19M, PPh Badan 2.09M")
    void testRekonsiliasiFiskal() throws Exception {
        JsonNode data = json("/api/tax-export/rekonsiliasi-fiskal?year=" + YEAR).get("data");

        assertThat(data.get("year").asInt()).isEqualTo(YEAR);
        assertThat(data.get("commercialNetIncome").decimalValue()).isEqualByComparingTo("18000000");
        assertThat(data.get("totalPositiveAdjustment").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(data.get("totalNegativeAdjustment").decimalValue()).isEqualByComparingTo("0");
        assertThat(data.get("netAdjustment").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(data.get("adjustments")).hasSize(1);
        JsonNode adjustment = data.get("adjustments").get(0);
        assertThat(adjustment.get("description").asText()).isEqualTo(ADJUSTMENT);
        assertThat(adjustment.get("category").asText()).isEqualTo("PERMANENT");
        assertThat(adjustment.get("direction").asText()).isEqualTo("POSITIVE");
        assertThat(data.get("pkp").decimalValue()).isEqualByComparingTo("19000000");
        assertPphBadan(data.get("pphBadan"));
    }

    @Test
    @DisplayName("PPh Badan applies the full Pasal 31E facility and credits the 200k PPh 23 withheld by the customer")
    void testPphBadan() throws Exception {
        JsonNode body = json("/api/tax-export/pph-badan?year=" + YEAR);
        assertThat(body.get("reportType").asText()).isEqualTo("pph-badan");
        assertPphBadan(body.get("data"));
    }

    @Test
    @DisplayName("Rekonsiliasi fiskal Excel shows commercial income, the adjustment, PKP and PPh 29")
    void testRekonsiliasiFiskalExcel() throws Exception {
        APIResponse response = getOk("/api/tax-export/rekonsiliasi-fiskal?year=" + YEAR + "&format=excel");
        assertXlsxAttachment(response, "rekonsiliasi-fiskal-" + YEAR + ".xlsx");
        try (Workbook workbook = workbook(response)) {
            Sheet sheet = workbook.getSheet("Rekonsiliasi Fiskal");
            assertThat(number(rowStartingWith(sheet, "Pendapatan"), 1)).isEqualTo(31_000_000d);
            assertThat(number(rowStartingWith(sheet, "Beban"), 1)).isEqualTo(-13_000_000d);
            assertThat(number(rowStartingWith(sheet, "Laba Bersih Komersial"), 1)).isEqualTo(18_000_000d);
            assertThat(number(rowStartingWith(sheet, ADJUSTMENT), 1)).isEqualTo(1_000_000d);
            assertThat(number(rowStartingWith(sheet, "Penghasilan Kena Pajak (PKP)"), 1)).isEqualTo(19_000_000d);
            assertThat(number(rowStartingWith(sheet, "PPh Badan Terutang"), 1)).isEqualTo(2_090_000d);
            assertThat(number(rowStartingWith(sheet, "Kredit Pajak PPh 23"), 1)).isEqualTo(-200_000d);
            assertThat(number(rowStartingWith(sheet, "PPh Pasal 29"), 1)).isEqualTo(1_890_000d);
        }
    }

    // ==================== SPT TAHUNAN LAMPIRAN ====================

    @Test
    @DisplayName("Consolidated lampiran carries taxpayer, P&L, fiscal reconciliation, expense breakdown and PPh Badan")
    void testConsolidatedLampiran() throws Exception {
        JsonNode body = json("/api/tax-export/spt-tahunan/lampiran?year=" + YEAR);
        assertThat(body.get("reportType").asText()).isEqualTo("spt-tahunan-lampiran");
        JsonNode data = body.get("data");
        assertThat(data.get("year").asInt()).isEqualTo(YEAR);

        CompanyConfig company = company();
        assertThat(data.get("taxpayer").get("name").asText()).isEqualTo(company.getCompanyName());
        assertThat(data.get("taxpayer").get("npwp").asText()).isEqualTo(company.getNpwp());

        JsonNode labaRugi = data.get("transkrip8A").get("labaRugi");
        assertThat(labaRugi.get("pendapatanUsaha").decimalValue()).isEqualByComparingTo("31000000");
        assertThat(labaRugi.get("bebanUsaha").decimalValue()).isEqualByComparingTo("13000000");
        assertThat(labaRugi.get("labaBersih").decimalValue()).isEqualByComparingTo("18000000");

        JsonNode lampiranI = data.get("lampiranI");
        assertThat(lampiranI.get("description").asText()).isEqualTo("Perhitungan Penghasilan Neto Fiskal");
        assertThat(lampiranI.get("pendapatanNeto").decimalValue()).isEqualByComparingTo("18000000");
        assertThat(lampiranI.get("totalKoreksiPositif").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(lampiranI.get("penghasilanKenaPajak").decimalValue()).isEqualByComparingTo("19000000");

        Map<String, BigDecimal> bebanUsaha = new HashMap<>();
        data.get("lampiranII").get("bebanUsaha").forEach(item ->
                bebanUsaha.put(item.get("code").asText(), item.get("amount").decimalValue()));
        assertThat(bebanUsaha).containsOnlyKeys("5.1.20", "5.1.21");
        assertThat(bebanUsaha.get("5.1.20")).isEqualByComparingTo("8000000");
        assertThat(bebanUsaha.get("5.1.21")).isEqualByComparingTo("5000000");

        JsonNode pph = data.get("pphBadan");
        assertThat(pph.get("penghasilanKenaPajak").decimalValue()).isEqualByComparingTo("19000000");
        assertThat(pph.get("pphTerutang").decimalValue()).isEqualByComparingTo("2090000");
        assertThat(pph.get("kreditPajak").decimalValue()).isEqualByComparingTo("200000");
        assertThat(pph.get("pph29KurangBayar").decimalValue()).isEqualByComparingTo("1890000");
    }

    @Test
    @DisplayName("BUG-015: Lampiran III lists the PPh 23 bupot received from the customer")
    void testLampiranIIIPph23Data() throws Exception {
        JsonNode lampiranIII = json("/api/tax-export/spt-tahunan/lampiran?year=" + YEAR).get("data").get("lampiranIII");

        JsonNode received = null;
        for (JsonNode item : lampiranIII.get("kreditPPh23")) {
            if (bupotReceived.equals(item.get("bupotNumber").asText())) {
                received = item;
            }
        }
        assertThat(received).as("Bupot " + bupotReceived + " missing from Lampiran III").isNotNull();
        assertThat(received.get("pemotong").asText()).isEqualTo("PT Bank Mandiri Tbk");
        assertThat(received.get("npwp").asText()).isEqualTo("01.310.523.4-091.000");
        assertThat(received.get("date").asText()).isEqualTo(YEAR + "-04-10");
        assertThat(received.get("dpp").decimalValue()).isEqualByComparingTo("10000000");
        assertThat(received.get("pph23").decimalValue()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("BUG-016: Transkrip 8A puts PPN Masukan and Kredit Pajak PPh 23 in 8A.I.7, nothing in 8A.I.6")
    void testTranskrip8AAssetMapping() throws Exception {
        Map<String, BigDecimal> before = neracaAktiva(YEAR - 1);
        Map<String, BigDecimal> after = neracaAktiva(YEAR);

        assertThat(after.get("8A.I.7").subtract(before.get("8A.I.7"))).isEqualByComparingTo("750000");
        assertThat(after.get("8A.I.6").subtract(before.get("8A.I.6"))).isEqualByComparingTo("0");
        assertThat(after.get("8A.I.1").subtract(before.get("8A.I.1"))).isEqualByComparingTo("19720000");
    }

    @Test
    @DisplayName("BUG-017: Lampiran I koreksi positif shows the tax article, not the account code")
    void testLampiranIPasalNotAccountCode() throws Exception {
        JsonNode koreksiPositif = json("/api/tax-export/spt-tahunan/lampiran?year=" + YEAR)
                .get("data").get("lampiranI").get("koreksiPositif");

        assertThat(koreksiPositif).hasSize(1);
        assertThat(koreksiPositif.get(0).get("description").asText()).isEqualTo(ADJUSTMENT);
        assertThat(koreksiPositif.get(0).get("pasal").asText()).isEqualTo("9(1)(a)");
        assertThat(koreksiPositif.get(0).get("amount").decimalValue()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("BUG-014: a posted CLOSING journal leaves the fiscal P&L, PKP and transkrip revenue unchanged")
    void testClosingJournalExcludedFromTaxExport() throws Exception {
        Map<String, String> accounts = new HashMap<>();
        for (JsonNode account : json("/api/drafts/accounts")) {
            accounts.put(account.get("code").asText(), account.get("id").asText());
        }
        Map<String, Object> closing = new HashMap<>();
        closing.put("transactionDate", YEAR + "-12-31");
        closing.put("description", "Tutup pendapatan konsultasi " + YEAR);
        closing.put("category", "CLOSING");
        closing.put("lines", List.of(
                Map.of("accountId", accounts.get("4.1.02"), "debit", 10000000, "credit", 0),
                Map.of("accountId", accounts.get("3.2.02"), "debit", 0, "credit", 10000000)));

        APIResponse created = post("/api/transactions/journal-entry", closing);
        assertThat(created.status()).as("Create closing journal: " + created.text()).isEqualTo(201);
        String closingId = objectMapper.readTree(created.text()).get("transactionId").asText();
        APIResponse posted = post("/api/transactions/" + closingId + "/post", Map.of());
        assertThat(posted.status()).as("Post closing journal: " + posted.text()).isEqualTo(200);
        assertThat(transactionRepository.findById(UUID.fromString(closingId)).orElseThrow().getClosingEntry()).isTrue();

        JsonNode rekonsiliasi = json("/api/tax-export/rekonsiliasi-fiskal?year=" + YEAR).get("data");
        assertThat(rekonsiliasi.get("commercialNetIncome").decimalValue()).isEqualByComparingTo("18000000");
        assertThat(rekonsiliasi.get("pkp").decimalValue()).isEqualByComparingTo("19000000");
        JsonNode transkrip = json("/api/tax-export/spt-tahunan/transkrip-8a?year=" + YEAR).get("data");
        assertThat(transkrip.get("totalRevenue").decimalValue()).isEqualByComparingTo("31000000");
        assertThat(transkrip.get("totalExpense").decimalValue()).isEqualByComparingTo("13000000");
    }

    // ==================== ERROR HANDLING ====================

    @Test
    @DisplayName("Tax export endpoints reject unauthenticated requests with 401")
    void testUnauthenticated() {
        assertThat(apiContext.get("/api/tax-export/ppn-detail?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31")
                .status()).isEqualTo(401);
        assertThat(apiContext.get("/api/tax-export/efaktur-keluaran?startMonth=" + YEAR + "-01&endMonth=" + YEAR + "-12")
                .status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Malformed dates and months are rejected with 400")
    void testInvalidDateParams() {
        assertThat(get("/api/tax-export/ppn-detail?startDate=invalid&endDate=" + YEAR + "-12-31").status()).isEqualTo(400);
        assertThat(get("/api/tax-export/efaktur-keluaran?startMonth=bad&endMonth=" + YEAR + "-12").status()).isEqualTo(400);
    }

    // ==================== HELPERS ====================

    private void assertPphBadan(JsonNode pph) {
        assertThat(pph.get("pkp").decimalValue()).isEqualByComparingTo("19000000");
        assertThat(pph.get("pkpRounded").decimalValue()).isEqualByComparingTo("19000000");
        assertThat(pph.get("totalRevenue").decimalValue()).isEqualByComparingTo("31000000");
        assertThat(pph.get("pphTerutang").decimalValue()).isEqualByComparingTo("2090000");
        assertThat(pph.get("calculationMethod").asText()).isEqualTo("Fasilitas Pasal 31E penuh (omset < 4,8M)");
        assertThat(pph.get("kreditPajakPPh23").decimalValue()).isEqualByComparingTo("200000");
        assertThat(pph.get("kreditPajakPPh25").decimalValue()).isEqualByComparingTo("0");
        assertThat(pph.get("totalKreditPajak").decimalValue()).isEqualByComparingTo("200000");
        assertThat(pph.get("pph29").decimalValue()).isEqualByComparingTo("1890000");
    }

    private Map<String, BigDecimal> neracaAktiva(int year) throws Exception {
        Map<String, BigDecimal> fields = new HashMap<>();
        json("/api/tax-export/spt-tahunan/lampiran?year=" + year).get("data").get("transkrip8A").get("neracaAktiva")
                .forEach(item -> fields.put(item.get("field").asText(), item.get("amount").decimalValue()));
        return fields;
    }

    private CompanyConfig company() {
        return companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Company config missing from test seed"));
    }

    private static void assertXlsxAttachment(APIResponse response, String filename) {
        assertThat(response.headers().get("content-type")).isEqualTo(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.headers().get("content-disposition")).isEqualTo("attachment; filename=" + filename);
    }

    private static Workbook workbook(APIResponse response) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(response.body()));
    }

    private static Row rowStartingWith(Sheet sheet, String firstCell) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && FORMATTER.formatCellValue(row.getCell(0)).startsWith(firstCell)) {
                return row;
            }
        }
        throw new AssertionError("No row starting with '" + firstCell + "' in sheet " + sheet.getSheetName());
    }

    private static String text(Row row, int column) {
        return FORMATTER.formatCellValue(row.getCell(column));
    }

    private static double number(Row row, int column) {
        return row.getCell(column).getNumericCellValue();
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private Transaction post(LocalDate date, String templateName, String projectCode, Line... lines) {
        String number = "TXP-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(date);
        tx.setDescription(templateName + " " + number);
        tx.setAmount(lines[0].debit());
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        if (projectCode != null) {
            tx.setProject(projectRepository.findByCode(projectCode)
                    .orElseThrow(() -> new IllegalStateException("Seeded project missing: " + projectCode)));
        }
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
        return saved;
    }

    private void detail(Transaction tx, TaxType type, java.util.function.Consumer<TaxTransactionDetail> fields) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(tx);
        detail.setTaxType(type);
        fields.accept(detail);
        taxDetailRepository.save(detail);
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String faktur() {
        return "010." + digits(3) + "-05." + digits(8);
    }

    private static String digits(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private JsonNode json(String path) throws Exception {
        APIResponse response = getOk(path);
        return objectMapper.readTree(response.text());
    }

    private APIResponse getOk(String path) {
        APIResponse response = get(path);
        assertThat(response.status()).as(path + ": " + response.text()).isEqualTo(200);
        return response;
    }

    private APIResponse get(String path) {
        return apiContext.get(path, RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
    }

    private APIResponse post(String path, Object data) {
        return apiContext.post(path, RequestOptions.create()
                .setHeader("Authorization", "Bearer " + accessToken)
                .setHeader("Content-Type", "application/json")
                .setData(data));
    }

    private String authenticateViaDeviceFlow() throws Exception {
        APIResponse codeResponse = apiContext.post("/api/device/code", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(Map.of("clientId", "tax-export-api-test")));
        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();

        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Tax Export API Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token", RequestOptions.create()
                    .setHeader("Content-Type", "application/json")
                    .setData(Map.of("deviceCode", deviceCode)));
            if (tokenResponse.ok()) {
                tokenRef.set(objectMapper.readTree(tokenResponse.text()).get("accessToken").asText());
                return true;
            }
            return false;
        });
        return tokenRef.get();
    }
}
