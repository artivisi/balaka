package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
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
            createBupotReferenceSheet(workbook);

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

            row.createCell(0).setCellValue(detail.getTransactionCode() != null ? detail.getTransactionCode() : "01");
            row.createCell(1).setCellValue(detail.getFakturNumber() != null ? detail.getFakturNumber() : "");

            Cell dateCell = row.createCell(2);
            if (detail.getFakturDate() != null) {
                dateCell.setCellValue(detail.getFakturDate().format(DATE_FORMATTER));
            }

            row.createCell(3).setCellValue(config.getNpwp() != null ? config.getNpwp() : "");
            row.createCell(4).setCellValue(config.getNitku() != null ? config.getNitku() : "");
            row.createCell(5).setCellValue(detail.getCounterpartyIdType() != null ? detail.getCounterpartyIdType() : "TIN");
            row.createCell(6).setCellValue(detail.getCounterpartyIdNumber());
            row.createCell(7).setCellValue(detail.getCounterpartyNitku() != null ? detail.getCounterpartyNitku() : "");
            row.createCell(8).setCellValue(detail.getCounterpartyName() != null ? detail.getCounterpartyName() : "");
            row.createCell(9).setCellValue(detail.getCounterpartyAddress() != null ? detail.getCounterpartyAddress() : "");
            row.createCell(10).setCellValue("B"); // B = Jasa (services), default for IT Services

            // Numeric cells
            Cell grossCell = row.createCell(11);
            grossCell.setCellValue(calculateGross(detail.getDpp(), detail.getPpn()).doubleValue());
            grossCell.setCellStyle(numberStyle);

            Cell dppCell = row.createCell(12);
            dppCell.setCellValue(detail.getDpp() != null ? detail.getDpp().doubleValue() : 0);
            dppCell.setCellStyle(numberStyle);

            Cell ppnCell = row.createCell(13);
            ppnCell.setCellValue(detail.getPpn() != null ? detail.getPpn().doubleValue() : 0);
            ppnCell.setCellStyle(numberStyle);

            Cell ppnbmCell = row.createCell(14);
            ppnbmCell.setCellValue(detail.getPpnbm() != null ? detail.getPpnbm().doubleValue() : 0);
            ppnbmCell.setCellStyle(numberStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createEFakturReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("REF");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Kode");
        headerRow.createCell(1).setCellValue("Keterangan");
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

            row.createCell(0).setCellValue(detail.getBupotNumber() != null ? detail.getBupotNumber() : "");

            Cell dateCell = row.createCell(1);
            if (detail.getTransaction() != null && detail.getTransaction().getTransactionDate() != null) {
                dateCell.setCellValue(detail.getTransaction().getTransactionDate().format(DATE_FORMATTER));
            }

            row.createCell(2).setCellValue(config.getNpwp() != null ? config.getNpwp() : "");
            row.createCell(3).setCellValue(config.getNitku() != null ? config.getNitku() : "");
            row.createCell(4).setCellValue(detail.getCounterpartyIdType() != null ? detail.getCounterpartyIdType() : "TIN");
            row.createCell(5).setCellValue(detail.getCounterpartyIdNumber());
            row.createCell(6).setCellValue(detail.getCounterpartyNitku() != null ? detail.getCounterpartyNitku() : "");
            row.createCell(7).setCellValue(detail.getCounterpartyName() != null ? detail.getCounterpartyName() : "");
            row.createCell(8).setCellValue(detail.getTaxObjectCode() != null ? detail.getTaxObjectCode() : "");

            Cell grossCell = row.createCell(9);
            grossCell.setCellValue(detail.getGrossAmount() != null ? detail.getGrossAmount().doubleValue() : 0);
            grossCell.setCellStyle(numberStyle);

            Cell rateCell = row.createCell(10);
            rateCell.setCellValue(detail.getTaxRate() != null ? detail.getTaxRate().doubleValue() : 0);
            rateCell.setCellStyle(numberStyle);

            Cell taxCell = row.createCell(11);
            taxCell.setCellValue(detail.getTaxAmount() != null ? detail.getTaxAmount().doubleValue() : 0);
            taxCell.setCellStyle(numberStyle);

            row.createCell(12).setCellValue(""); // FacilityType - empty for normal
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createBupotReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("REF");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Kode Objek Pajak");
        headerRow.createCell(1).setCellValue("Keterangan");
        headerRow.createCell(2).setCellValue("Tarif Default (%)");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);
        headerRow.getCell(2).setCellStyle(headerStyle);

        // Common tax object codes
        String[][] refs = {
                {"24-104-01", "Jasa Teknik", "2"},
                {"24-104-02", "Jasa Manajemen", "2"},
                {"24-104-03", "Jasa Konsultan", "2"},
                {"24-104-14", "Jasa Pemeliharaan/Perawatan/Perbaikan", "2"},
                {"24-104-21", "Jasa Katering", "2"},
                {"24-104-22", "Jasa Kebersihan/Cleaning Service", "2"},
                {"24-104-99", "Jasa Lainnya", "2"},
                {"24-100-02", "Sewa Kendaraan Angkutan Darat", "2"},
                {"28-409-01", "Sewa Tanah dan/atau Bangunan", "10"},
                {"28-409-07", "Jasa Konstruksi - Pelaksana Kecil", "1.75"},
                {"28-409-08", "Jasa Konstruksi - Pelaksana Menengah/Besar", "2.65"},
                {"28-423-01", "PPh Final UMKM", "0.5"}
        };

        int rowNum = 1;
        for (String[] ref : refs) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(ref[0]);
            row.createCell(1).setCellValue(ref[1]);
            row.createCell(2).setCellValue(ref[2]);
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

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat("dd/MM/yyyy"));
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
