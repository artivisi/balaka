package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CoretaxExportService. Each test creates its own posted transactions with
 * tax details in March {@link #YEAR} (reserved for this class) and rolls back; the export range
 * covers only that month.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Coretax Export Service Integration Tests")
class CoretaxExportServiceTest {

    private static final int YEAR = 2073;
    private static final LocalDate START = LocalDate.of(YEAR, 3, 1);
    private static final LocalDate END = LocalDate.of(YEAR, 3, 31);
    private static final UUID TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final DataFormatter FORMATTER = new DataFormatter();

    @Autowired private CoretaxExportService coretaxExportService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TaxTransactionDetailRepository taxTransactionDetailRepository;
    @Autowired private CompanyConfigRepository companyConfigRepository;

    private CompanyConfig company;
    private String fakturTin;
    private String fakturNik;
    private String fakturMasukan;
    private String bupot23;
    private String bupot42;

    @BeforeEach
    void createTaxDetails() {
        company = companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Company config missing from integration seed"));
        fakturTin = faktur();
        fakturNik = faktur();
        fakturMasukan = faktur();
        bupot23 = "BP23-" + YEAR + "-" + digits(6);
        bupot42 = "BP42-" + YEAR + "-" + digits(6);

        TaxTransactionDetail tin = efaktur(posted(LocalDate.of(YEAR, 3, 6)), TaxType.PPN_KELUARAN, fakturTin,
                LocalDate.of(YEAR, 3, 6), "01", "10000000", "1100000");
        tin.setCounterpartyIdType("TIN");
        tin.setCounterpartyNpwp("01.310.523.4-091.000");
        tin.setCounterpartyNitku("0131052340910000000001");
        tin.setCounterpartyName("PT Pembeli Satu");
        tin.setCounterpartyAddress("Jl. Sudirman 1, Jakarta");
        taxTransactionDetailRepository.save(tin);

        TaxTransactionDetail nik = efaktur(posted(LocalDate.of(YEAR, 3, 2)), TaxType.PPN_KELUARAN, fakturNik,
                LocalDate.of(YEAR, 3, 2), "04", "5000000", "550000");
        nik.setCounterpartyIdType("NIK");
        nik.setCounterpartyNik("3171234567890001");
        nik.setCounterpartyName("Budi Perorangan");
        taxTransactionDetailRepository.save(nik);

        TaxTransactionDetail masukan = efaktur(posted(LocalDate.of(YEAR, 3, 12)), TaxType.PPN_MASUKAN, fakturMasukan,
                LocalDate.of(YEAR, 3, 11), "01", "2000000", "220000");
        masukan.setCounterpartyIdType("TIN");
        masukan.setCounterpartyNpwp("02.111.222.3-444.000");
        masukan.setCounterpartyName("PT Pemasok Server");
        taxTransactionDetailRepository.save(masukan);

        taxTransactionDetailRepository.save(bupot(posted(LocalDate.of(YEAR, 3, 20)), TaxType.PPH_42, bupot42,
                "28-409-01", "20000000", "10", "2000000", "PT Pemilik Gedung"));
        taxTransactionDetailRepository.save(bupot(posted(LocalDate.of(YEAR, 3, 10)), TaxType.PPH_23, bupot23,
                "24-104-01", "5000000", "2", "100000", "CV Konsultan Teknik"));

        Transaction voided = posted(LocalDate.of(YEAR, 3, 15));
        voided.setStatus(TransactionStatus.VOID);
        transactionRepository.save(voided);
        taxTransactionDetailRepository.save(efaktur(voided, TaxType.PPN_KELUARAN, faktur(),
                LocalDate.of(YEAR, 3, 15), "01", "7000000", "770000"));
        taxTransactionDetailRepository.save(efaktur(posted(LocalDate.of(YEAR, 4, 1)), TaxType.PPN_KELUARAN, faktur(),
                LocalDate.of(YEAR, 3, 31), "01", "3000000", "330000"));
        taxTransactionDetailRepository.save(bupot(posted(LocalDate.of(YEAR, 2, 28)), TaxType.PPH_23, "BP23-OUT-" + digits(6),
                "24-104-01", "1000000", "2", "20000", "CV Di Luar Periode"));
    }

    @Test
    @DisplayName("e-Faktur Keluaran exports the posted output-VAT invoices of the period ordered by faktur date")
    void shouldExportEFakturKeluaran() throws IOException {
        try (Workbook workbook = open(coretaxExportService.exportEFakturKeluaran(START, END))) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(cells(data.getRow(0))).containsExactly("TrxCode", "TrxNumber", "TrxDate", "SellerTaxId",
                    "SellerNitku", "BuyerIdOpt", "BuyerIdNumber", "BuyerNitku", "BuyerName", "BuyerAddress",
                    "GoodServiceOpt", "TaxBaseSellingPrice", "OtherTaxBaseSellingPrice", "VAT", "STLG");
            assertThat(data.getLastRowNum()).isEqualTo(2);

            Row nik = data.getRow(1);
            assertThat(cells(nik).subList(0, 11)).containsExactly("04", fakturNik, "02/03/" + YEAR, company.getNpwp(),
                    company.getNitku(), "NIK", "3171234567890001", "", "Budi Perorangan", "", "B");
            assertAmounts(nik, 11, 5_550_000, 5_000_000, 550_000, 0);

            Row tin = data.getRow(2);
            assertThat(cells(tin).subList(0, 11)).containsExactly("01", fakturTin, "06/03/" + YEAR, company.getNpwp(),
                    company.getNitku(), "TIN", "01.310.523.4-091.000", "0131052340910000000001", "PT Pembeli Satu",
                    "Jl. Sudirman 1, Jakarta", "B");
            assertAmounts(tin, 11, 11_100_000, 10_000_000, 1_100_000, 0);
        }
    }

    @Test
    @DisplayName("e-Faktur Masukan exports only the input-VAT invoices of the period")
    void shouldExportEFakturMasukan() throws IOException {
        try (Workbook workbook = open(coretaxExportService.exportEFakturMasukan(START, END))) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(data.getLastRowNum()).isEqualTo(1);
            Row row = data.getRow(1);
            assertThat(cells(row).subList(0, 11)).containsExactly("01", fakturMasukan, "11/03/" + YEAR,
                    company.getNpwp(), company.getNitku(), "TIN", "02.111.222.3-444.000", "", "PT Pemasok Server", "", "B");
            assertAmounts(row, 11, 2_220_000, 2_000_000, 220_000, 0);
        }
    }

    @Test
    @DisplayName("e-Bupot Unifikasi exports PPh 23 and 4(2) withholdings of the period ordered by transaction date")
    void shouldExportBupotUnifikasi() throws IOException {
        try (Workbook workbook = open(coretaxExportService.exportBupotUnifikasi(START, END))) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(cells(data.getRow(0))).containsExactly("BupotNumber", "BupotDate", "CutterTaxId", "CutterNitku",
                    "RecipientIdType", "RecipientIdNumber", "RecipientNitku", "RecipientName", "TaxObjectCode",
                    "GrossAmount", "TaxRate", "TaxAmount", "FacilityType");
            assertThat(data.getLastRowNum()).isEqualTo(2);

            Row pph23 = data.getRow(1);
            assertThat(cells(pph23).subList(0, 9)).containsExactly(bupot23, "10/03/" + YEAR, company.getNpwp(),
                    company.getNitku(), "TIN", "03.555.666.7-888.000", "", "CV Konsultan Teknik", "24-104-01");
            assertThat(pph23.getCell(9).getNumericCellValue()).isEqualTo(5_000_000d);
            assertThat(pph23.getCell(10).getNumericCellValue()).isEqualTo(2d);
            assertThat(pph23.getCell(11).getNumericCellValue()).isEqualTo(100_000d);
            assertThat(FORMATTER.formatCellValue(pph23.getCell(12))).isEmpty();

            Row pph42 = data.getRow(2);
            assertThat(FORMATTER.formatCellValue(pph42.getCell(0))).isEqualTo(bupot42);
            assertThat(FORMATTER.formatCellValue(pph42.getCell(1))).isEqualTo("20/03/" + YEAR);
            assertThat(FORMATTER.formatCellValue(pph42.getCell(8))).isEqualTo("28-409-01");
            assertThat(pph42.getCell(9).getNumericCellValue()).isEqualTo(20_000_000d);
            assertThat(pph42.getCell(10).getNumericCellValue()).isEqualTo(10d);
            assertThat(pph42.getCell(11).getNumericCellValue()).isEqualTo(2_000_000d);
        }
    }

    @Test
    @DisplayName("Statistics count and sum only the posted details of the period")
    void shouldComputeStatistics() {
        CoretaxExportService.ExportStatistics stats = coretaxExportService.getExportStatistics(START, END);

        assertThat(stats.fakturKeluaranCount()).isEqualTo(2);
        assertThat(stats.fakturMasukanCount()).isEqualTo(1);
        assertThat(stats.bupotUnifikasiCount()).isEqualTo(2);
        assertThat(stats.totalPPNKeluaran()).isEqualByComparingTo("1650000");
        assertThat(stats.totalPPNMasukan()).isEqualByComparingTo("220000");
        assertThat(stats.totalPPh()).isEqualByComparingTo("2100000");
    }

    @Test
    @DisplayName("A period without tax details exports header-only sheets and zero statistics")
    void shouldExportEmptyPeriod() throws IOException {
        LocalDate start = LocalDate.of(YEAR, 5, 1);
        LocalDate end = LocalDate.of(YEAR, 5, 31);

        for (byte[] export : List.of(coretaxExportService.exportEFakturKeluaran(start, end),
                coretaxExportService.exportEFakturMasukan(start, end),
                coretaxExportService.exportBupotUnifikasi(start, end))) {
            try (Workbook workbook = open(export)) {
                assertThat(workbook.getSheet("DATA").getLastRowNum()).isZero();
                assertThat(workbook.getSheet("REF")).isNotNull();
            }
        }
        CoretaxExportService.ExportStatistics stats = coretaxExportService.getExportStatistics(start, end);
        assertThat(stats.fakturKeluaranCount()).isZero();
        assertThat(stats.fakturMasukanCount()).isZero();
        assertThat(stats.bupotUnifikasiCount()).isZero();
        assertThat(stats.totalPPNKeluaran()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.totalPPNMasukan()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.totalPPh()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("e-Faktur REF sheet lists the transaction codes, ID types and goods/services options")
    void shouldIncludeEFakturReferenceSheet() throws IOException {
        try (Workbook workbook = open(coretaxExportService.exportEFakturKeluaran(START, END))) {
            Sheet ref = workbook.getSheet("REF");
            assertThat(cells(ref.getRow(0))).containsExactly("Kode", "Keterangan");
            assertThat(column(ref, 0)).containsExactly("01", "02", "03", "04", "07", "08", "TIN", "NIK", "A", "B");
            assertThat(FORMATTER.formatCellValue(ref.getRow(4).getCell(1))).isEqualTo("DPP Nilai Lain (Pasal 8A ayat 1)");
        }
    }

    @Test
    @DisplayName("e-Bupot REF sheet lists the tax object codes with their default rates")
    void shouldIncludeBupotReferenceSheet() throws IOException {
        try (Workbook workbook = open(coretaxExportService.exportBupotUnifikasi(START, END))) {
            Sheet ref = workbook.getSheet("REF");
            assertThat(cells(ref.getRow(0))).containsExactly("Kode Objek Pajak", "Keterangan", "Tarif Default (%)");
            assertThat(column(ref, 0)).containsExactly("24-104-01", "24-104-02", "24-104-03", "24-104-14", "24-104-21",
                    "24-104-22", "24-104-99", "24-100-02", "28-409-01", "28-409-07", "28-409-08", "28-423-01");
            assertThat(column(ref, 2)).containsExactly("2", "2", "2", "2", "2", "2", "2", "2", "10", "1.75", "2.65", "0.5");
        }
    }

    private Transaction posted(LocalDate date) {
        Transaction tx = new Transaction();
        tx.setTransactionNumber("CTX-" + UUID.randomUUID().toString().substring(0, 8));
        tx.setTransactionDate(date);
        tx.setDescription("Uji ekspor Coretax");
        tx.setAmount(new BigDecimal("1000000"));
        tx.setJournalTemplate(templateRepository.findById(TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + TEMPLATE_ID)));
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        return transactionRepository.save(tx);
    }

    private static TaxTransactionDetail efaktur(Transaction tx, TaxType type, String fakturNumber, LocalDate fakturDate,
                                                String code, String dpp, String ppn) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(tx);
        detail.setTaxType(type);
        detail.setFakturNumber(fakturNumber);
        detail.setFakturDate(fakturDate);
        detail.setTransactionCode(code);
        detail.setDpp(new BigDecimal(dpp));
        detail.setPpn(new BigDecimal(ppn));
        return detail;
    }

    private static TaxTransactionDetail bupot(Transaction tx, TaxType type, String number, String objectCode,
                                              String gross, String rate, String amount, String recipient) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(tx);
        detail.setTaxType(type);
        detail.setBupotNumber(number);
        detail.setTaxObjectCode(objectCode);
        detail.setGrossAmount(new BigDecimal(gross));
        detail.setTaxRate(new BigDecimal(rate));
        detail.setTaxAmount(new BigDecimal(amount));
        detail.setCounterpartyIdType("TIN");
        detail.setCounterpartyNpwp("03.555.666.7-888.000");
        detail.setCounterpartyName(recipient);
        return detail;
    }

    private static void assertAmounts(Row row, int firstColumn, double gross, double dpp, double vat, double ppnbm) {
        assertThat(row.getCell(firstColumn).getNumericCellValue()).isEqualTo(gross);
        assertThat(row.getCell(firstColumn + 1).getNumericCellValue()).isEqualTo(dpp);
        assertThat(row.getCell(firstColumn + 2).getNumericCellValue()).isEqualTo(vat);
        assertThat(row.getCell(firstColumn + 3).getNumericCellValue()).isEqualTo(ppnbm);
    }

    private static List<String> cells(Row row) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            values.add(FORMATTER.formatCellValue(row.getCell(i)));
        }
        return values;
    }

    private static List<String> column(Sheet sheet, int column) {
        List<String> values = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            values.add(FORMATTER.formatCellValue(sheet.getRow(r).getCell(column)));
        }
        return values;
    }

    private static Workbook open(byte[] excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel));
    }

    private static String faktur() {
        return "010." + digits(3) + "-" + (YEAR % 100) + "." + digits(8);
    }

    private static String digits(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
