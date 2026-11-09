package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.TaxObjectCode;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.service.SptTahunanExportService.Bpa1LineItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.Bpa1Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L1AdjustmentItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L1LineItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L1LossItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L1Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L4LineItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L4Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L9LineItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L9Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.Transkrip8ALineItem;
import com.artivisi.accountingfinance.service.SptTahunanExportService.Transkrip8AReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService.PPhBadanCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting tax transaction data to Coretax-compatible Excel format.
 * The exported Excel files can be converted to XML using DJP's official converter
 * and then imported into the Coretax system.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CoretaxExportService {

    private final TaxTransactionDetailRepository taxTransactionDetailRepository;
    private final CompanyConfigRepository companyConfigRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String COL_KETERANGAN = "Keterangan";
    private static final String COL_KODE_AKUN = "Kode Akun";
    private static final String COL_JUMLAH_RP = "Jumlah (Rp)";
    private static final String PREFIX_TOTAL = "Total ";

    // Helper methods for cell creation with null handling
    private void setStringCell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void setStringCell(Row row, int col, String value, String defaultValue) {
        row.createCell(col).setCellValue(value != null ? value : defaultValue);
    }

    private void setNumberCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }

    private void setDateCell(Row row, int col, LocalDate date) {
        Cell cell = row.createCell(col);
        if (date != null) {
            cell.setCellValue(date.format(DATE_FORMATTER));
        }
    }

    /**
     * Export e-Faktur Keluaran (Output VAT) data to Excel format.
     * Format matches DJP's "Sample Faktur PK Template" converter.
     */
    public byte[] exportEFakturKeluaran(LocalDate startDate, LocalDate endDate) throws IOException {
        List<TaxTransactionDetail> details = taxTransactionDetailRepository.findEFakturKeluaranByDateRange(startDate, endDate);
        CompanyConfig config = getCompanyConfig();

        try (Workbook workbook = new XSSFWorkbook()) {
            createEFakturSheet(workbook, details, config);
            createEFakturReferenceSheet(workbook);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Export e-Faktur Masukan (Input VAT) data to Excel format.
     */
    public byte[] exportEFakturMasukan(LocalDate startDate, LocalDate endDate) throws IOException {
        List<TaxTransactionDetail> details = taxTransactionDetailRepository.findEFakturMasukanByDateRange(startDate, endDate);
        CompanyConfig config = getCompanyConfig();

        try (Workbook workbook = new XSSFWorkbook()) {
            createEFakturSheet(workbook, details, config);
            createEFakturReferenceSheet(workbook);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Export e-Bupot Unifikasi (PPh Withholding) data to Excel format.
     * Format matches DJP's "Bupot Unifikasi" converter template.
     */
    public byte[] exportBupotUnifikasi(LocalDate startDate, LocalDate endDate) throws IOException {
        List<TaxTransactionDetail> details = taxTransactionDetailRepository.findEBupotUnifikasiByDateRange(startDate, endDate);
        CompanyConfig config = getCompanyConfig();

        try (Workbook workbook = new XSSFWorkbook()) {
            createBupotSheet(workbook, details, config);
            createPph26Sheet(workbook, details);
            createBupotReferenceSheet(workbook);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Export BP21 data: PPh 21 bukan pegawai and other PPh 21 tax details. These rows are not in Bupot Unifikasi,
     * so a masa is never uploaded twice. Column layout unverified against the Coretax BP21 converter.
     */
    public byte[] exportBp21(LocalDate startDate, LocalDate endDate) throws IOException {
        List<TaxTransactionDetail> details = taxTransactionDetailRepository.findByTaxTypeAndDateRange(TaxType.PPH_21, startDate, endDate);
        CompanyConfig config = getCompanyConfig();

        try (Workbook workbook = new XSSFWorkbook()) {
            createBp21Sheet(workbook, details, config);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Get export statistics for the given period.
     */
    public ExportStatistics getExportStatistics(LocalDate startDate, LocalDate endDate) {
        List<TaxTransactionDetail> fakturKeluaran = taxTransactionDetailRepository.findEFakturKeluaranByDateRange(startDate, endDate);
        List<TaxTransactionDetail> fakturMasukan = taxTransactionDetailRepository.findEFakturMasukanByDateRange(startDate, endDate);
        List<TaxTransactionDetail> bupot = taxTransactionDetailRepository.findEBupotUnifikasiByDateRange(startDate, endDate);

        return new ExportStatistics(
                fakturKeluaran.size(),
                fakturMasukan.size(),
                bupot.size(),
                sumPPN(fakturKeluaran),
                sumPPN(fakturMasukan),
                sumTaxAmount(bupot)
        );
    }

    private void createEFakturSheet(Workbook workbook, List<TaxTransactionDetail> details, CompanyConfig config) {
        Sheet sheet = workbook.createSheet("DATA");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "TrxCode",           // Transaction code (01, 02, 04, 07, 08)
                "TrxNumber",         // Faktur number
                "TrxDate",           // Transaction date (DD/MM/YYYY)
                "SellerTaxId",       // Seller NPWP
                "SellerNitku",       // Seller NITKU
                "BuyerIdOpt",        // TIN or NIK
                "BuyerIdNumber",     // Buyer NPWP or NIK
                "BuyerNitku",        // Buyer NITKU
                "BuyerName",         // Buyer name
                "BuyerAddress",      // Buyer address
                "GoodServiceOpt",    // A (goods) or B (services)
                "TaxBaseSellingPrice", // Harga Jual (gross)
                "OtherTaxBaseSellingPrice", // DPP
                "VAT",               // PPN amount
                "STLG"               // PPnBM
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowNum = 1;
        for (TaxTransactionDetail detail : details) {
            Row row = sheet.createRow(rowNum++);
            populateEFakturRow(row, detail, config, numberStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void populateEFakturRow(Row row, TaxTransactionDetail detail, CompanyConfig config, CellStyle numberStyle) {
        setStringCell(row, 0, detail.getTransactionCode(), "01");
        setStringCell(row, 1, detail.getFakturNumber());
        setDateCell(row, 2, detail.getFakturDate());
        setStringCell(row, 3, config.getNpwp());
        setStringCell(row, 4, config.getNitku());
        setStringCell(row, 5, detail.getCounterpartyIdType(), "TIN");
        row.createCell(6).setCellValue(detail.getCounterpartyIdNumber());
        setStringCell(row, 7, detail.getCounterpartyNitku());
        setStringCell(row, 8, detail.getCounterpartyName());
        setStringCell(row, 9, detail.getCounterpartyAddress());
        row.createCell(10).setCellValue("B"); // B = Jasa (services)

        setNumberCell(row, 11, calculateGross(detail.getDpp(), detail.getPpn()), numberStyle);
        setNumberCell(row, 12, detail.getDpp(), numberStyle);
        setNumberCell(row, 13, detail.getPpn(), numberStyle);
        setNumberCell(row, 14, detail.getPpnbm(), numberStyle);
    }

    private void createEFakturReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("REF");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Kode");
        headerRow.createCell(1).setCellValue(COL_KETERANGAN);
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        // Transaction codes
        String[][] refs = {
                {"01", "Penyerahan BKP/JKP kepada pembeli dalam negeri"},
                {"02", "Penyerahan BKP/JKP kepada Pemungut PPN"},
                {"03", "Penyerahan kepada Pemungut PPN Lainnya"},
                {"04", "DPP Nilai Lain (Pasal 8A ayat 1)"},
                {"07", "Penyerahan yang PPN-nya tidak dipungut"},
                {"08", "Penyerahan yang dibebaskan dari PPN"},
                {"TIN", "Menggunakan NPWP"},
                {"NIK", "Menggunakan NIK (pembeli non-PKP)"},
                {"A", "Barang"},
                {"B", "Jasa"}
        };

        int rowNum = 1;
        for (String[] ref : refs) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(ref[0]);
            row.createCell(1).setCellValue(ref[1]);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    @SuppressWarnings("java:S125") // Inline comments document Coretax export field names, not commented-out code
    private void createBupotSheet(Workbook workbook, List<TaxTransactionDetail> details, CompanyConfig config) {
        Sheet sheet = workbook.createSheet("DATA");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "BupotNumber",       // Bukti potong number
                "BupotDate",         // Date (DD/MM/YYYY)
                "CutterTaxId",       // Company NPWP (pemotong)
                "CutterNitku",       // Company NITKU
                "RecipientIdType",   // TIN or NIK
                "RecipientIdNumber", // Vendor NPWP
                "RecipientNitku",    // Vendor NITKU
                "RecipientName",     // Vendor name
                "TaxObjectCode",     // e.g., 24-104-01
                "GrossAmount",       // Jumlah Bruto
                "TaxRate",           // Tarif (%)
                "TaxAmount",         // PPh dipotong
                "FacilityType"       // SKB/DTP/None
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowNum = 1;
        for (TaxTransactionDetail detail : details) {
            Row row = sheet.createRow(rowNum++);
            populateBupotRow(row, detail, config, numberStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void populateBupotRow(Row row, TaxTransactionDetail detail, CompanyConfig config, CellStyle numberStyle) {
        setStringCell(row, 0, detail.getBupotNumber());
        LocalDate txDate = detail.getTransaction() != null ? detail.getTransaction().getTransactionDate() : null;
        setDateCell(row, 1, txDate);
        setStringCell(row, 2, config.getNpwp());
        setStringCell(row, 3, config.getNitku());
        setStringCell(row, 4, detail.getCounterpartyIdType(), "TIN");
        row.createCell(5).setCellValue(detail.getCounterpartyIdNumber());
        setStringCell(row, 6, detail.getCounterpartyNitku());
        setStringCell(row, 7, detail.getCounterpartyName());
        setStringCell(row, 8, detail.getTaxObjectCode());
        setNumberCell(row, 9, detail.getGrossAmount(), numberStyle);
        setNumberCell(row, 10, detail.getTaxRate(), numberStyle);
        setNumberCell(row, 11, detail.getTaxAmount(), numberStyle);
        setStringCell(row, 12, detail.getDgtFormNumber() != null ? "DGT" : ""); // FacilityType - empty for normal
    }

    private void createPph26Sheet(Workbook workbook, List<TaxTransactionDetail> details) {
        Sheet sheet = workbook.createSheet("PPH26");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        String[] headers = {"RecipientName", "Country", "ForeignTin", "DgtFormNumber", "TaxObjectCode", "GrossAmount", "TaxRate", "TaxAmount"};
        writeHeader(sheet, headers, headerStyle);

        int rowNum = 1;
        for (TaxTransactionDetail detail : details) {
            if (detail.getTaxType() != TaxType.PPH_26) {
                continue;
            }
            Row row = sheet.createRow(rowNum++);
            setStringCell(row, 0, detail.getCounterpartyName());
            setStringCell(row, 1, detail.getCounterpartyCountry());
            setStringCell(row, 2, detail.getCounterpartyForeignTin());
            setStringCell(row, 3, detail.getDgtFormNumber());
            setStringCell(row, 4, detail.getTaxObjectCode());
            setNumberCell(row, 5, detail.getGrossAmount(), numberStyle);
            setNumberCell(row, 6, detail.getTaxRate(), numberStyle);
            setNumberCell(row, 7, detail.getTaxAmount(), numberStyle);
        }
    }

    private void createBp21Sheet(Workbook workbook, List<TaxTransactionDetail> details, CompanyConfig config) {
        Sheet sheet = workbook.createSheet("DATA");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        String[] headers = {"BupotNumber", "BupotDate", "CutterTaxId", "CutterNitku", "RecipientIdType", "RecipientIdNumber",
                "RecipientName", "RecipientAddress", "TaxObjectCode", "GrossAmount", "Dpp", "TaxRate", "TaxAmount"};
        writeHeader(sheet, headers, headerStyle);

        int rowNum = 1;
        for (TaxTransactionDetail detail : details) {
            Row row = sheet.createRow(rowNum++);
            setStringCell(row, 0, detail.getBupotNumber());
            setDateCell(row, 1, detail.getTransaction().getTransactionDate());
            setStringCell(row, 2, config.getNpwp());
            setStringCell(row, 3, config.getNitku());
            setStringCell(row, 4, detail.getCounterpartyIdType());
            setStringCell(row, 5, detail.getCounterpartyIdNumber());
            setStringCell(row, 6, detail.getCounterpartyName());
            setStringCell(row, 7, detail.getCounterpartyAddress());
            setStringCell(row, 8, detail.getTaxObjectCode());
            setNumberCell(row, 9, detail.getGrossAmount(), numberStyle);
            setNumberCell(row, 10, detail.getDpp(), numberStyle);
            setNumberCell(row, 11, detail.getTaxRate(), numberStyle);
            setNumberCell(row, 12, detail.getTaxAmount(), numberStyle);
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeHeader(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createBupotReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("REF");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Kode Objek Pajak");
        headerRow.createCell(1).setCellValue(COL_KETERANGAN);
        headerRow.createCell(2).setCellValue("Tarif Default (%)");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);
        headerRow.getCell(2).setCellStyle(headerStyle);

        // Bukti potong unifikasi codes (PPh 23, 4(2), 26); PPh 21 codes go on BP21
        int rowNum = 1;
        for (TaxObjectCode code : TaxObjectCode.values()) {
            if (code.getTaxType() == TaxType.PPH_21) {
                continue;
            }
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(code.getCode());
            row.createCell(1).setCellValue(code.getDescription());
            row.createCell(2).setCellValue(code.getDefaultRate().stripTrailingZeros().toPlainString());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CompanyConfig getCompanyConfig() {
        return companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Company configuration not found. Please configure company settings first."));
    }

    private BigDecimal calculateGross(BigDecimal dpp, BigDecimal ppn) {
        BigDecimal dppValue = dpp != null ? dpp : BigDecimal.ZERO;
        BigDecimal ppnValue = ppn != null ? ppn : BigDecimal.ZERO;
        return dppValue.add(ppnValue);
    }

    private BigDecimal sumPPN(List<TaxTransactionDetail> details) {
        return details.stream()
                .map(d -> d.getPpn() != null ? d.getPpn() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumTaxAmount(List<TaxTransactionDetail> details) {
        return details.stream()
                .map(d -> d.getTaxAmount() != null ? d.getTaxAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== SPT TAHUNAN EXPORTS ====================

    /**
     * Export L1 (Rekonsiliasi Fiskal) to Excel matching Coretax key-in layout.
     * Sections: Revenue, Expenses, Commercial Net Income, Fiscal Adjustments, PKP, PPh Badan.
     */
    public byte[] exportL1ToExcel(L1Report report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("L1 Rekonsiliasi Fiskal");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            int rowNum = 0;

            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LAMPIRAN I - PENGHITUNGAN PENGHASILAN NETO FISKAL");
            titleCell.setCellStyle(boldStyle);

            Row yearRow = sheet.createRow(rowNum++);
            yearRow.createCell(0).setCellValue("Tahun Pajak: " + report.year());
            rowNum++; // blank row

            // Column headers
            Row colHeader = sheet.createRow(rowNum++);
            String[] headers = {COL_KODE_AKUN, "Uraian", COL_JUMLAH_RP};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = colHeader.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Section I-A: Peredaran Usaha
            rowNum = writeL1Section(sheet, rowNum, "I. PEREDARAN USAHA", report.operatingRevenue(),
                    report.totalOperatingRevenue(), numberStyle, boldStyle);

            // COGS
            Row cogsRow = sheet.createRow(rowNum++);
            cogsRow.createCell(0).setCellValue("");
            cogsRow.createCell(1).setCellValue("Harga Pokok Penjualan");
            setNumberCell(cogsRow, 2, report.cogs(), numberStyle);

            // Gross Profit
            Row gpRow = sheet.createRow(rowNum++);
            gpRow.createCell(1).setCellValue("LABA BRUTO (I - HPP)");
            gpRow.getCell(1).setCellStyle(boldStyle);
            setNumberCell(gpRow, 2, report.grossProfit(), numberStyle);
            rowNum++; // blank

            // Section I-C: Biaya Usaha
            rowNum = writeL1Section(sheet, rowNum, "II. BIAYA USAHA", report.operatingExpenses(),
                    report.totalOperatingExpenses(), numberStyle, boldStyle);

            // Net Operating Income
            Row noiRow = sheet.createRow(rowNum++);
            noiRow.createCell(1).setCellValue("PENGHASILAN NETO DARI USAHA (I - II)");
            noiRow.getCell(1).setCellStyle(boldStyle);
            setNumberCell(noiRow, 2, report.netOperatingIncome(), numberStyle);
            rowNum++; // blank

            // Section I-D: Penghasilan Luar Usaha
            rowNum = writeL1Section(sheet, rowNum, "III. PENGHASILAN DARI LUAR USAHA", report.otherIncome(),
                    report.totalOtherIncome(), numberStyle, boldStyle);

            // Section I-E: Biaya Luar Usaha
            rowNum = writeL1Section(sheet, rowNum, "IV. BIAYA DARI LUAR USAHA", report.otherExpenses(),
                    report.totalOtherExpenses(), numberStyle, boldStyle);

            // Net Other Income
            Row noi2Row = sheet.createRow(rowNum++);
            noi2Row.createCell(1).setCellValue("PENGHASILAN NETO DARI LUAR USAHA (III - IV)");
            noi2Row.getCell(1).setCellStyle(boldStyle);
            setNumberCell(noi2Row, 2, report.netOtherIncome(), numberStyle);
            rowNum++; // blank

            // Commercial Net Income
            Row cniRow = sheet.createRow(rowNum++);
            cniRow.createCell(1).setCellValue("PENGHASILAN NETO KOMERSIAL");
            cniRow.getCell(1).setCellStyle(boldStyle);
            setNumberCell(cniRow, 2, report.commercialNetIncome(), numberStyle);
            rowNum++; // blank

            // Fiscal Adjustments
            rowNum = writeL1Adjustments(sheet, rowNum, "V. PENYESUAIAN FISKAL POSITIF",
                    report.positiveAdjustments(), report.totalPositiveAdjustment(), numberStyle, boldStyle);

            rowNum = writeL1Adjustments(sheet, rowNum, "VI. PENYESUAIAN FISKAL NEGATIF",
                    report.negativeAdjustments(), report.totalNegativeAdjustment(), numberStyle, boldStyle);

            // PKP before loss
            Row pkpBeforeRow = sheet.createRow(rowNum++);
            pkpBeforeRow.createCell(1).setCellValue("PENGHASILAN NETO FISKAL");
            pkpBeforeRow.getCell(1).setCellStyle(boldStyle);
            setNumberCell(pkpBeforeRow, 2, report.pkpBeforeLoss(), numberStyle);
            rowNum++;

            // Loss carryforward
            if (!report.lossCarryforwards().isEmpty()) {
                Row lossTitle = sheet.createRow(rowNum++);
                lossTitle.createCell(1).setCellValue("KOMPENSASI KERUGIAN FISKAL");
                lossTitle.getCell(1).setCellStyle(boldStyle);

                for (L1LossItem loss : report.lossCarryforwards()) {
                    Row lossRow = sheet.createRow(rowNum++);
                    lossRow.createCell(1).setCellValue("Rugi Fiskal Tahun " + loss.originYear()
                            + " (kadaluarsa " + loss.expiryYear() + ")");
                    setNumberCell(lossRow, 2, loss.remainingAmount(), numberStyle);
                }

                Row totalLossRow = sheet.createRow(rowNum++);
                totalLossRow.createCell(1).setCellValue("Total Kompensasi Kerugian");
                totalLossRow.getCell(1).setCellStyle(boldStyle);
                setNumberCell(totalLossRow, 2, report.totalLossCompensation(), numberStyle);
                rowNum++;
            }

            // PKP after loss
            Row pkpRow = sheet.createRow(rowNum++);
            pkpRow.createCell(1).setCellValue("PENGHASILAN KENA PAJAK (PKP)");
            pkpRow.getCell(1).setCellStyle(boldStyle);
            setNumberCell(pkpRow, 2, report.pkp(), numberStyle);
            rowNum++; // blank

            // PPh Badan
            PPhBadanCalculation pph = report.pphBadan();
            Row pphTitle = sheet.createRow(rowNum++);
            pphTitle.createCell(1).setCellValue("VII. PPh BADAN TERUTANG");
            pphTitle.getCell(1).setCellStyle(boldStyle);

            Row methodRow = sheet.createRow(rowNum++);
            methodRow.createCell(1).setCellValue("Metode: " + pph.calculationMethod());

            Row pphRow = sheet.createRow(rowNum++);
            pphRow.createCell(1).setCellValue("PPh Terutang");
            setNumberCell(pphRow, 2, pph.pphTerutang(), numberStyle);
            rowNum++; // blank

            // Kredit Pajak
            Row kpTitle = sheet.createRow(rowNum++);
            kpTitle.createCell(1).setCellValue("VIII. KREDIT PAJAK");
            kpTitle.getCell(1).setCellStyle(boldStyle);

            Row kp23Row = sheet.createRow(rowNum++);
            kp23Row.createCell(1).setCellValue("PPh 23 yang dipotong pihak lain");
            setNumberCell(kp23Row, 2, report.kreditPPh23(), numberStyle);

            Row kp25Row = sheet.createRow(rowNum++);
            kp25Row.createCell(1).setCellValue("PPh 25 yang sudah dibayar");
            setNumberCell(kp25Row, 2, report.kreditPPh25(), numberStyle);

            Row totalKpRow = sheet.createRow(rowNum++);
            totalKpRow.createCell(1).setCellValue("Total Kredit Pajak");
            totalKpRow.getCell(1).setCellStyle(boldStyle);
            setNumberCell(totalKpRow, 2, report.totalKreditPajak(), numberStyle);
            rowNum++; // blank

            // PPh 29
            Row pph29Row = sheet.createRow(rowNum);
            pph29Row.createCell(1).setCellValue("PPh KURANG / (LEBIH) BAYAR (PPh 29)");
            pph29Row.getCell(1).setCellStyle(boldStyle);
            setNumberCell(pph29Row, 2, report.pph29(), numberStyle);

            // Auto-size
            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 15000);
            sheet.setColumnWidth(2, 5000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export L4 (Penghasilan Final) to Excel for Coretax key-in.
     */
    public byte[] exportL4ToExcel(L4Report report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("L4 Penghasilan Final");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            int rowNum = 0;

            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LAMPIRAN IV - PENGHASILAN YANG DIKENAKAN PPh FINAL");
            titleCell.setCellStyle(boldStyle);

            Row yearRow = sheet.createRow(rowNum++);
            yearRow.createCell(0).setCellValue("Tahun Pajak: " + report.year());
            rowNum++;

            // Headers
            Row colHeader = sheet.createRow(rowNum++);
            String[] headers = {"No", "Kode Objek Pajak", "Uraian", "Jumlah Bruto (Rp)", "Tarif (%)", "PPh Final (Rp)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = colHeader.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int no = 1;
            for (L4LineItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(no++);
                setStringCell(row, 1, item.taxObjectCode());
                setStringCell(row, 2, item.description());
                setNumberCell(row, 3, item.grossAmount(), numberStyle);
                setNumberCell(row, 4, item.taxRate(), numberStyle);
                setNumberCell(row, 5, item.taxAmount(), numberStyle);
            }

            // Totals
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(2).setCellValue("TOTAL");
            totalRow.getCell(2).setCellStyle(boldStyle);
            setNumberCell(totalRow, 3, report.totalGross(), numberStyle);
            setNumberCell(totalRow, 5, report.totalTax(), numberStyle);

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export Transkrip 8A (Laporan Keuangan) to Excel with Neraca + Laba Rugi sheets.
     */
    public byte[] exportTranskrip8AToExcel(Transkrip8AReport report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);

            // Sheet 1: Neraca (Balance Sheet)
            createTranskrip8ANeracaSheet(workbook, report, headerStyle, numberStyle, boldStyle);

            // Sheet 2: Laba Rugi (Income Statement)
            createTranskrip8ALabaRugiSheet(workbook, report, headerStyle, numberStyle, boldStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export L9 (Penyusutan & Amortisasi) to Excel matching DJP converter template.
     */
    public byte[] exportL9ToExcel(L9Report report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("DATA");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "NamaHarta",          // Asset name
                    "KelompokHarta",      // Fiscal group (I/II/III/IV/Bangunan)
                    "TanggalPerolehan",   // Acquisition date (DD/MM/YYYY)
                    "HargaPerolehan",     // Acquisition cost
                    "MetodePenyusutan",   // GL (Garis Lurus) or SM (Saldo Menurun)
                    "MasaManfaat",        // Useful life (years)
                    "PenyusutanTahunIni", // Depreciation this year
                    "AkumulasiPenyusutan",// Accumulated depreciation
                    "NilaiBuku"           // Book value
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (L9LineItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                setStringCell(row, 0, item.assetName());
                setStringCell(row, 1, item.fiscalGroup());
                setDateCell(row, 2, item.acquisitionDate());
                setNumberCell(row, 3, item.acquisitionCost(), numberStyle);
                setStringCell(row, 4, "Garis Lurus".equals(item.depreciationMethod()) ? "GL" : "SM");
                row.createCell(5).setCellValue(item.usefulLifeYears());
                setNumberCell(row, 6, item.depreciationThisYear(), numberStyle);
                setNumberCell(row, 7, item.accumulatedDepreciation(), numberStyle);
                setNumberCell(row, 8, item.bookValue(), numberStyle);
            }

            // Reference sheet
            Sheet refSheet = workbook.createSheet("REF");
            Row refHeader = refSheet.createRow(0);
            refHeader.createCell(0).setCellValue("Kode");
            refHeader.createCell(1).setCellValue(COL_KETERANGAN);
            refHeader.createCell(2).setCellValue("Masa Manfaat (Tahun)");
            refHeader.getCell(0).setCellStyle(headerStyle);
            refHeader.getCell(1).setCellStyle(headerStyle);
            refHeader.getCell(2).setCellStyle(headerStyle);

            String[][] refs = {
                    {"Kelompok I", "Bukan Bangunan - Kelompok I", "4"},
                    {"Kelompok II", "Bukan Bangunan - Kelompok II", "8"},
                    {"Kelompok III", "Bukan Bangunan - Kelompok III", "16"},
                    {"Kelompok IV", "Bukan Bangunan - Kelompok IV", "20"},
                    {"Bangunan Permanen", "Bangunan Permanen", "20"},
                    {"Bangunan Non-Permanen", "Bangunan Non-Permanen", "10"},
                    {"GL", "Garis Lurus (Straight-Line)", ""},
                    {"SM", "Saldo Menurun (Declining Balance)", ""}
            };
            int refRow = 1;
            for (String[] ref : refs) {
                Row row = refSheet.createRow(refRow++);
                row.createCell(0).setCellValue(ref[0]);
                row.createCell(1).setCellValue(ref[1]);
                row.createCell(2).setCellValue(ref[2]);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            refSheet.autoSizeColumn(0);
            refSheet.autoSizeColumn(1);
            refSheet.autoSizeColumn(2);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export BPA1 (1721-A1 bulk) to Excel matching DJP BPA1 converter template.
     * One row per employee with annual PPh 21 reconciliation data.
     */
    public byte[] exportBpa1ToExcel(Bpa1Report report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("DATA");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "NPWP",                   // Employee NPWP
                    "NIK",                    // Employee NIK
                    "NamaPegawai",            // Employee name
                    "StatusPTKP",             // PTKP status (TK/0, K/1, etc.)
                    "MasaKerja",              // Months worked
                    "PenghasilanBruto",       // Annual gross income
                    "BiayaJabatan",           // 5% of gross, max 6M
                    "IuranBPJS",              // BPJS employee portion (JHT + JP)
                    "PenghasilanNeto",        // Gross - BiayaJabatan - BPJS
                    "PTKP",                   // Non-taxable income
                    "PKP",                    // Taxable income
                    "PPh21Terutang",          // Annual tax owed
                    "PPh21Dipotong",          // Actual tax withheld
                    "PPh21KurangLebihBayar"   // Difference
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Bpa1LineItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                setStringCell(row, 0, item.npwp());
                setStringCell(row, 1, item.nik());
                setStringCell(row, 2, item.name());
                setStringCell(row, 3, item.ptkpStatus() != null ? item.ptkpStatus().getDisplayName() : "");
                row.createCell(4).setCellValue(item.monthCount());
                setNumberCell(row, 5, item.penghasilanBruto(), numberStyle);
                setNumberCell(row, 6, item.biayaJabatan(), numberStyle);
                setNumberCell(row, 7, item.bpjsDeduction(), numberStyle);
                setNumberCell(row, 8, item.penghasilanNeto(), numberStyle);
                setNumberCell(row, 9, item.ptkp(), numberStyle);
                setNumberCell(row, 10, item.pkp(), numberStyle);
                setNumberCell(row, 11, item.pph21Terutang(), numberStyle);
                setNumberCell(row, 12, item.pph21Dipotong(), numberStyle);
                setNumberCell(row, 13, item.pph21KurangBayar(), numberStyle);
            }

            // Totals row
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(2).setCellValue("TOTAL");
            CellStyle boldStyle = createBoldStyle(workbook);
            totalRow.getCell(2).setCellStyle(boldStyle);
            setNumberCell(totalRow, 5, report.totalGross(), numberStyle);
            setNumberCell(totalRow, 11, report.totalPph21Terutang(), numberStyle);
            setNumberCell(totalRow, 12, report.totalPph21Dipotong(), numberStyle);
            setNumberCell(totalRow, 13, report.totalPph21Terutang().subtract(report.totalPph21Dipotong()), numberStyle);

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            // Reference sheet with PTKP values
            createBpa1ReferenceSheet(workbook);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createBpa1ReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("REF");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Status PTKP");
        headerRow.createCell(1).setCellValue("PTKP Tahunan (Rp)");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        String[][] refs = {
                {"TK/0", "54000000"}, {"TK/1", "58500000"}, {"TK/2", "63000000"}, {"TK/3", "67500000"},
                {"K/0", "58500000"}, {"K/1", "63000000"}, {"K/2", "67500000"}, {"K/3", "72000000"},
                {"K/I/0", "112500000"}, {"K/I/1", "117000000"}, {"K/I/2", "121500000"}, {"K/I/3", "126000000"}
        };
        int rowNum = 1;
        for (String[] ref : refs) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(ref[0]);
            setNumberCell(row, 1, new BigDecimal(ref[1]), numberStyle);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    // ==================== L1 EXCEL HELPERS ====================

    private int writeL1Section(Sheet sheet, int rowNum, String title, List<L1LineItem> items,
                               BigDecimal total, CellStyle numberStyle, CellStyle boldStyle) {
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(1).setCellValue(title);
        titleRow.getCell(1).setCellStyle(boldStyle);

        for (L1LineItem item : items) {
            Row row = sheet.createRow(rowNum++);
            setStringCell(row, 0, item.accountCode());
            setStringCell(row, 1, item.accountName());
            setNumberCell(row, 2, item.amount(), numberStyle);
        }

        Row totalRow = sheet.createRow(rowNum++);
        totalRow.createCell(1).setCellValue(PREFIX_TOTAL + title);
        totalRow.getCell(1).setCellStyle(boldStyle);
        setNumberCell(totalRow, 2, total, numberStyle);
        rowNum++; // blank
        return rowNum;
    }

    private int writeL1Adjustments(Sheet sheet, int rowNum, String title, List<L1AdjustmentItem> items,
                                   BigDecimal total, CellStyle numberStyle, CellStyle boldStyle) {
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(1).setCellValue(title);
        titleRow.getCell(1).setCellStyle(boldStyle);

        for (L1AdjustmentItem item : items) {
            Row row = sheet.createRow(rowNum++);
            setStringCell(row, 0, item.accountCode() != null ? item.accountCode() : "");
            setStringCell(row, 1, item.description() + " (" + item.category() + ")");
            setNumberCell(row, 2, item.amount(), numberStyle);
        }

        Row totalRow = sheet.createRow(rowNum++);
        totalRow.createCell(1).setCellValue(PREFIX_TOTAL + title);
        totalRow.getCell(1).setCellStyle(boldStyle);
        setNumberCell(totalRow, 2, total, numberStyle);
        rowNum++; // blank
        return rowNum;
    }

    // ==================== TRANSKRIP 8A HELPERS ====================

    private void createTranskrip8ANeracaSheet(Workbook workbook, Transkrip8AReport report,
                                              CellStyle headerStyle, CellStyle numberStyle, CellStyle boldStyle) {
        Sheet sheet = workbook.createSheet("Neraca");
        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("TRANSKRIP KUTIPAN ELEMEN LAPORAN KEUANGAN (8A)");
        titleRow.getCell(0).setCellStyle(boldStyle);

        Row subtitleRow = sheet.createRow(rowNum++);
        subtitleRow.createCell(0).setCellValue("NERACA / LAPORAN POSISI KEUANGAN");
        subtitleRow.getCell(0).setCellStyle(boldStyle);

        Row yearRow = sheet.createRow(rowNum++);
        yearRow.createCell(0).setCellValue("Per 31 Desember " + report.year());
        rowNum++;

        // Headers
        Row colHeader = sheet.createRow(rowNum++);
        colHeader.createCell(0).setCellValue(COL_KODE_AKUN);
        colHeader.createCell(1).setCellValue("Nama Akun");
        colHeader.createCell(2).setCellValue(COL_JUMLAH_RP);
        colHeader.getCell(0).setCellStyle(headerStyle);
        colHeader.getCell(1).setCellStyle(headerStyle);
        colHeader.getCell(2).setCellStyle(headerStyle);

        // Assets
        rowNum = writeTranskrip8ASection(sheet, rowNum, "ASET", report.assetItems(),
                report.totalAssets(), numberStyle, boldStyle);

        // Liabilities
        rowNum = writeTranskrip8ASection(sheet, rowNum, "LIABILITAS", report.liabilityItems(),
                report.totalLiabilities(), numberStyle, boldStyle);

        // Equity
        rowNum = writeTranskrip8ASection(sheet, rowNum, "EKUITAS", report.equityItems(),
                report.totalEquity(), numberStyle, boldStyle);

        // Laba tahun berjalan
        Row earningsRow = sheet.createRow(rowNum++);
        earningsRow.createCell(1).setCellValue("Laba (Rugi) Tahun Berjalan");
        setNumberCell(earningsRow, 2, report.currentYearEarnings(), numberStyle);

        // Total L + E
        Row totalLERow = sheet.createRow(rowNum);
        totalLERow.createCell(1).setCellValue("TOTAL LIABILITAS + EKUITAS");
        totalLERow.getCell(1).setCellStyle(boldStyle);
        setNumberCell(totalLERow, 2, report.totalLiabilities().add(report.totalEquity()), numberStyle);

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 12000);
        sheet.setColumnWidth(2, 5000);
    }

    private void createTranskrip8ALabaRugiSheet(Workbook workbook, Transkrip8AReport report,
                                                CellStyle headerStyle, CellStyle numberStyle, CellStyle boldStyle) {
        Sheet sheet = workbook.createSheet("Laba Rugi");
        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("TRANSKRIP KUTIPAN ELEMEN LAPORAN KEUANGAN (8A)");
        titleRow.getCell(0).setCellStyle(boldStyle);

        Row subtitleRow = sheet.createRow(rowNum++);
        subtitleRow.createCell(0).setCellValue("LAPORAN LABA RUGI");
        subtitleRow.getCell(0).setCellStyle(boldStyle);

        Row yearRow = sheet.createRow(rowNum++);
        yearRow.createCell(0).setCellValue("Periode 1 Januari - 31 Desember " + report.year());
        rowNum++;

        // Headers
        Row colHeader = sheet.createRow(rowNum++);
        colHeader.createCell(0).setCellValue(COL_KODE_AKUN);
        colHeader.createCell(1).setCellValue("Nama Akun");
        colHeader.createCell(2).setCellValue(COL_JUMLAH_RP);
        colHeader.getCell(0).setCellStyle(headerStyle);
        colHeader.getCell(1).setCellStyle(headerStyle);
        colHeader.getCell(2).setCellStyle(headerStyle);

        // Revenue
        rowNum = writeTranskrip8ASection(sheet, rowNum, "PENDAPATAN", report.revenueItems(),
                report.totalRevenue(), numberStyle, boldStyle);

        // Expenses
        rowNum = writeTranskrip8ASection(sheet, rowNum, "BEBAN", report.expenseItems(),
                report.totalExpense(), numberStyle, boldStyle);

        // Net Income
        Row niRow = sheet.createRow(rowNum);
        niRow.createCell(1).setCellValue("LABA (RUGI) BERSIH");
        niRow.getCell(1).setCellStyle(boldStyle);
        setNumberCell(niRow, 2, report.netIncome(), numberStyle);

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 12000);
        sheet.setColumnWidth(2, 5000);
    }

    private int writeTranskrip8ASection(Sheet sheet, int rowNum, String title, List<Transkrip8ALineItem> items,
                                        BigDecimal total, CellStyle numberStyle, CellStyle boldStyle) {
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(1).setCellValue(title);
        titleRow.getCell(1).setCellStyle(boldStyle);

        for (Transkrip8ALineItem item : items) {
            Row row = sheet.createRow(rowNum++);
            setStringCell(row, 0, item.accountCode());
            setStringCell(row, 1, item.accountName());
            setNumberCell(row, 2, item.amount(), numberStyle);
        }

        Row totalRow = sheet.createRow(rowNum++);
        totalRow.createCell(1).setCellValue(PREFIX_TOTAL + title);
        totalRow.getCell(1).setCellStyle(boldStyle);
        setNumberCell(totalRow, 2, total, numberStyle);
        rowNum++;
        return rowNum;
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    // DTO for export statistics
    public record ExportStatistics(
            int fakturKeluaranCount,
            int fakturMasukanCount,
            int bupotUnifikasiCount,
            BigDecimal totalPPNKeluaran,
            BigDecimal totalPPNMasukan,
            BigDecimal totalPPh
    ) {}
}
