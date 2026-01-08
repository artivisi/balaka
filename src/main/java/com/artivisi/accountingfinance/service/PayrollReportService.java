package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollReportService {

    private static final String COMPANY_NAME = "PT ArtiVisi Intermedia";
    private static final String TOTAL_LABEL = "TOTAL";
    private static final String TOTAL_POTONGAN = "Total Potongan";
    private static final String PPH_21 = "PPh 21";
    private static final String NUMBER_PATTERN = "#,##0";
    private static final String PERIODE_PREFIX = "Periode ";
    private static final DecimalFormat NUMBER_FORMAT;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("id", "ID"));

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("id", "ID"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        NUMBER_FORMAT = new DecimalFormat(NUMBER_PATTERN, symbols);
    }

    private final PayrollService payrollService;

    // ==================== PAYROLL SUMMARY REPORT ====================

    public byte[] exportPayrollSummaryToPdf(PayrollRun payrollRun, List<PayrollDetail> details) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "REKAP GAJI KARYAWAN", "Payroll Summary",
                    PERIODE_PREFIX + payrollRun.getPeriodDisplayName());

            // Summary section
            addSummarySection(document, payrollRun);

            // Detail table
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{5, 20, 15, 12, 12, 12, 12, 12});
            table.setSpacingBefore(15);

            addTableHeader(table, "No", "Nama Karyawan", "NIK", "Gaji Bruto", "BPJS Karyawan", PPH_21, TOTAL_POTONGAN, "Gaji Neto");

            int no = 1;
            for (PayrollDetail detail : details) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, detail.getEmployeeName(), Element.ALIGN_LEFT);
                addTableCell(table, detail.getEmployeeId(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(detail.getGrossSalary()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(detail.getTotalEmployeeBpjs()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(detail.getPph21()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(detail.getTotalDeductions()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(detail.getNetPay()), Element.ALIGN_RIGHT);
            }

            // Total row
            addTotalRow(table, payrollRun);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Payroll Summary PDF", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    public byte[] exportPayrollSummaryToExcel(PayrollRun payrollRun, List<PayrollDetail> details) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Rekap Gaji");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "REKAP GAJI KARYAWAN",
                    PERIODE_PREFIX + payrollRun.getPeriodDisplayName(), 8);

            // Summary
            rowNum = addExcelSummary(workbook, sheet, rowNum, payrollRun);
            rowNum++; // Empty row

            // Headers
            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] headers = {"No", "Nama Karyawan", "NIK", "Gaji Bruto", "BPJS Karyawan", PPH_21, TOTAL_POTONGAN, "Gaji Neto"};
            for (int i = 0; i < headers.length; i++) {
                createCell(headerRow, i, headers[i], headerStyle);
            }

            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            int no = 1;
            for (PayrollDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, String.valueOf(no++), textStyle);
                createCell(row, 1, detail.getEmployeeName(), textStyle);
                createCell(row, 2, detail.getEmployeeId(), textStyle);
                createNumericCell(row, 3, detail.getGrossSalary(), numberStyle);
                createNumericCell(row, 4, detail.getTotalEmployeeBpjs(), numberStyle);
                createNumericCell(row, 5, detail.getPph21(), numberStyle);
                createNumericCell(row, 6, detail.getTotalDeductions(), numberStyle);
                createNumericCell(row, 7, detail.getNetPay(), numberStyle);
            }

            // Total row
            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, TOTAL_LABEL, totalStyle);
            createCell(totalRow, 2, "", totalStyle);
            createNumericCell(totalRow, 3, payrollRun.getTotalGross(), totalStyle);

            BigDecimal totalEmployeeBpjs = details.stream()
                .map(PayrollDetail::getTotalEmployeeBpjs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            createNumericCell(totalRow, 4, totalEmployeeBpjs, totalStyle);
            createNumericCell(totalRow, 5, payrollRun.getTotalPph21(), totalStyle);
            createNumericCell(totalRow, 6, payrollRun.getTotalDeductions(), totalStyle);
            createNumericCell(totalRow, 7, payrollRun.getTotalNetPay(), totalStyle);

            autoSizeColumns(sheet, 8);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Payroll Summary Excel", e);
            throw new RuntimeException("Failed to generate Excel: " + e.getMessage());
        }
    }

    // ==================== PPH 21 MONTHLY REPORT ====================

    public byte[] exportPph21ReportToPdf(PayrollRun payrollRun, List<PayrollDetail> details) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN PPh 21 BULANAN", "Monthly PPh 21 Report",
                    "Masa Pajak " + payrollRun.getPeriodDisplayName());

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{5, 25, 20, 15, 15, 20});
            table.setSpacingBefore(15);

            addTableHeader(table, "No", "Nama", "NPWP", "Penghasilan Bruto", PPH_21, "Status PTKP");

            int no = 1;
            for (PayrollDetail detail : details) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, detail.getEmployeeName(), Element.ALIGN_LEFT);
                addTableCell(table, detail.getEmployee().getNpwp() != null ? detail.getEmployee().getNpwp() : "-", Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(detail.getGrossSalary()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(detail.getPph21()), Element.ALIGN_RIGHT);
                addTableCell(table, detail.getEmployee().getPtkpStatus().name(), Element.ALIGN_CENTER);
            }

            // Total
            PdfPCell emptyCell = createEmptyCell();
            table.addCell(emptyCell);

            PdfPCell labelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
            labelCell.setPadding(5);
            labelCell.setBackgroundColor(new Color(230, 230, 230));
            labelCell.setBorderWidth(1f);
            table.addCell(labelCell);

            table.addCell(emptyCell);

            PdfPCell grossCell = new PdfPCell(new Phrase(formatNumber(payrollRun.getTotalGross()), getBoldFont()));
            grossCell.setPadding(5);
            grossCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            grossCell.setBackgroundColor(new Color(230, 230, 230));
            grossCell.setBorderWidth(1f);
            table.addCell(grossCell);

            PdfPCell pphCell = new PdfPCell(new Phrase(formatNumber(payrollRun.getTotalPph21()), getBoldFont()));
            pphCell.setPadding(5);
            pphCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            pphCell.setBackgroundColor(new Color(230, 230, 230));
            pphCell.setBorderWidth(1f);
            table.addCell(pphCell);

            table.addCell(emptyCell);

            document.add(table);

            // Notes
            Paragraph notes = new Paragraph("\nCatatan:", getBoldFont());
            notes.setSpacingBefore(20);
            document.add(notes);
            document.add(new Paragraph("- Laporan ini digunakan untuk pelaporan SPT Masa PPh 21", getNormalFont()));
            document.add(new Paragraph("- Setor PPh 21 paling lambat tanggal 10 bulan berikutnya", getNormalFont()));
            document.add(new Paragraph("- Lapor SPT Masa paling lambat tanggal 20 bulan berikutnya", getNormalFont()));

            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating PPh 21 Report PDF", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    public byte[] exportPph21ReportToExcel(PayrollRun payrollRun, List<PayrollDetail> details) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(PPH_21);
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN PPh 21 BULANAN",
                    "Masa Pajak " + payrollRun.getPeriodDisplayName(), 6);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] headers = {"No", "Nama", "NPWP", "Penghasilan Bruto", PPH_21, "Status PTKP"};
            for (int i = 0; i < headers.length; i++) {
                createCell(headerRow, i, headers[i], headerStyle);
            }

            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            int no = 1;
            for (PayrollDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, String.valueOf(no++), textStyle);
                createCell(row, 1, detail.getEmployeeName(), textStyle);
                createCell(row, 2, detail.getEmployee().getNpwp() != null ? detail.getEmployee().getNpwp() : "-", textStyle);
                createNumericCell(row, 3, detail.getGrossSalary(), numberStyle);
                createNumericCell(row, 4, detail.getPph21(), numberStyle);
                createCell(row, 5, detail.getEmployee().getPtkpStatus().name(), textStyle);
            }

            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, TOTAL_LABEL, totalStyle);
            createCell(totalRow, 2, "", totalStyle);
            createNumericCell(totalRow, 3, payrollRun.getTotalGross(), totalStyle);
            createNumericCell(totalRow, 4, payrollRun.getTotalPph21(), totalStyle);
            createCell(totalRow, 5, "", totalStyle);

            autoSizeColumns(sheet, 6);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating PPh 21 Report Excel", e);
            throw new RuntimeException("Failed to generate Excel: " + e.getMessage());
        }
    }

    // ==================== BPJS REPORT ====================

    public byte[] exportBpjsReportToPdf(PayrollRun payrollRun, List<PayrollDetail> details) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN IURAN BPJS", "BPJS Contribution Report",
                    PERIODE_PREFIX + payrollRun.getPeriodDisplayName());

            // BPJS Kesehatan
            document.add(new Paragraph("BPJS Kesehatan", getBoldFont()));
            PdfPTable kesTable = new PdfPTable(5);
            kesTable.setWidthPercentage(100);
            kesTable.setWidths(new float[]{5, 30, 20, 20, 25});
            kesTable.setSpacingBefore(10);
            kesTable.setSpacingAfter(20);

            addTableHeader(kesTable, "No", "Nama", "Gaji", "Perusahaan (4%)", "Karyawan (1%)");

            int no = 1;
            BigDecimal totalKesCompany = BigDecimal.ZERO;
            BigDecimal totalKesEmployee = BigDecimal.ZERO;
            for (PayrollDetail detail : details) {
                addTableCell(kesTable, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(kesTable, detail.getEmployeeName(), Element.ALIGN_LEFT);
                addTableCell(kesTable, formatNumber(detail.getGrossSalary()), Element.ALIGN_RIGHT);
                addTableCell(kesTable, formatNumber(detail.getBpjsKesCompany()), Element.ALIGN_RIGHT);
                addTableCell(kesTable, formatNumber(detail.getBpjsKesEmployee()), Element.ALIGN_RIGHT);
                totalKesCompany = totalKesCompany.add(detail.getBpjsKesCompany());
                totalKesEmployee = totalKesEmployee.add(detail.getBpjsKesEmployee());
            }

            addBpjsTotalRow(kesTable, totalKesCompany, totalKesEmployee);
            document.add(kesTable);

            // BPJS Ketenagakerjaan
            document.add(new Paragraph("BPJS Ketenagakerjaan", getBoldFont()));
            PdfPTable tkTable = new PdfPTable(9);
            tkTable.setWidthPercentage(100);
            tkTable.setWidths(new float[]{5, 18, 12, 10, 10, 10, 10, 10, 10});
            tkTable.setSpacingBefore(10);

            addTableHeader(tkTable, "No", "Nama", "Gaji", "JKK", "JKM", "JHT (P)", "JHT (K)", "JP (P)", "JP (K)");

            no = 1;
            BigDecimal totalJkk = BigDecimal.ZERO, totalJkm = BigDecimal.ZERO;
            BigDecimal totalJhtC = BigDecimal.ZERO, totalJhtE = BigDecimal.ZERO;
            BigDecimal totalJpC = BigDecimal.ZERO, totalJpE = BigDecimal.ZERO;

            for (PayrollDetail detail : details) {
                addTableCell(tkTable, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(tkTable, detail.getEmployeeName(), Element.ALIGN_LEFT);
                addTableCell(tkTable, formatNumber(detail.getGrossSalary()), Element.ALIGN_RIGHT);
                addTableCell(tkTable, formatNumber(detail.getBpjsJkk()), Element.ALIGN_RIGHT);
                addTableCell(tkTable, formatNumber(detail.getBpjsJkm()), Element.ALIGN_RIGHT);
                addTableCell(tkTable, formatNumber(detail.getBpjsJhtCompany()), Element.ALIGN_RIGHT);
                addTableCell(tkTable, formatNumber(detail.getBpjsJhtEmployee()), Element.ALIGN_RIGHT);
                addTableCell(tkTable, formatNumber(detail.getBpjsJpCompany()), Element.ALIGN_RIGHT);
                addTableCell(tkTable, formatNumber(detail.getBpjsJpEmployee()), Element.ALIGN_RIGHT);

                totalJkk = totalJkk.add(detail.getBpjsJkk());
                totalJkm = totalJkm.add(detail.getBpjsJkm());
                totalJhtC = totalJhtC.add(detail.getBpjsJhtCompany());
                totalJhtE = totalJhtE.add(detail.getBpjsJhtEmployee());
                totalJpC = totalJpC.add(detail.getBpjsJpCompany());
                totalJpE = totalJpE.add(detail.getBpjsJpEmployee());
            }

            addBpjsTkTotalRow(tkTable, totalJkk, totalJkm, totalJhtC, totalJhtE, totalJpC, totalJpE);
            document.add(tkTable);

            // Summary
            BigDecimal totalCompany = totalKesCompany.add(totalJkk).add(totalJkm).add(totalJhtC).add(totalJpC);
            BigDecimal totalEmployee = totalKesEmployee.add(totalJhtE).add(totalJpE);

            Paragraph summary = new Paragraph("\nRingkasan:", getBoldFont());
            summary.setSpacingBefore(20);
            document.add(summary);
            document.add(new Paragraph("Total Iuran Perusahaan: Rp " + formatNumber(totalCompany), getNormalFont()));
            document.add(new Paragraph("Total Iuran Karyawan: Rp " + formatNumber(totalEmployee), getNormalFont()));
            document.add(new Paragraph("Total Setoran BPJS: Rp " + formatNumber(totalCompany.add(totalEmployee)), getBoldFont()));

            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating BPJS Report PDF", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    public byte[] exportBpjsReportToExcel(PayrollRun payrollRun, List<PayrollDetail> details) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // BPJS Kesehatan Sheet
            Sheet kesSheet = workbook.createSheet("BPJS Kesehatan");
            int rowNum = 0;
            rowNum = addExcelHeader(workbook, kesSheet, rowNum, "BPJS KESEHATAN",
                    PERIODE_PREFIX + payrollRun.getPeriodDisplayName(), 5);

            Row kesHeaderRow = kesSheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] kesHeaders = {"No", "Nama", "Gaji", "Perusahaan (4%)", "Karyawan (1%)"};
            for (int i = 0; i < kesHeaders.length; i++) {
                createCell(kesHeaderRow, i, kesHeaders[i], headerStyle);
            }

            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            int no = 1;
            BigDecimal totalKesCompany = BigDecimal.ZERO;
            BigDecimal totalKesEmployee = BigDecimal.ZERO;
            for (PayrollDetail detail : details) {
                Row row = kesSheet.createRow(rowNum++);
                createCell(row, 0, String.valueOf(no++), textStyle);
                createCell(row, 1, detail.getEmployeeName(), textStyle);
                createNumericCell(row, 2, detail.getGrossSalary(), numberStyle);
                createNumericCell(row, 3, detail.getBpjsKesCompany(), numberStyle);
                createNumericCell(row, 4, detail.getBpjsKesEmployee(), numberStyle);
                totalKesCompany = totalKesCompany.add(detail.getBpjsKesCompany());
                totalKesEmployee = totalKesEmployee.add(detail.getBpjsKesEmployee());
            }

            Row kesTotalRow = kesSheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(kesTotalRow, 0, "", totalStyle);
            createCell(kesTotalRow, 1, TOTAL_LABEL, totalStyle);
            createCell(kesTotalRow, 2, "", totalStyle);
            createNumericCell(kesTotalRow, 3, totalKesCompany, totalStyle);
            createNumericCell(kesTotalRow, 4, totalKesEmployee, totalStyle);
            autoSizeColumns(kesSheet, 5);

            // BPJS Ketenagakerjaan Sheet
            Sheet tkSheet = workbook.createSheet("BPJS Ketenagakerjaan");
            rowNum = 0;
            rowNum = addExcelHeader(workbook, tkSheet, rowNum, "BPJS KETENAGAKERJAAN",
                    PERIODE_PREFIX + payrollRun.getPeriodDisplayName(), 9);

            Row tkHeaderRow = tkSheet.createRow(rowNum++);
            String[] tkHeaders = {"No", "Nama", "Gaji", "JKK", "JKM", "JHT (P)", "JHT (K)", "JP (P)", "JP (K)"};
            for (int i = 0; i < tkHeaders.length; i++) {
                createCell(tkHeaderRow, i, tkHeaders[i], headerStyle);
            }

            no = 1;
            BigDecimal totalJkk = BigDecimal.ZERO, totalJkm = BigDecimal.ZERO;
            BigDecimal totalJhtC = BigDecimal.ZERO, totalJhtE = BigDecimal.ZERO;
            BigDecimal totalJpC = BigDecimal.ZERO, totalJpE = BigDecimal.ZERO;

            for (PayrollDetail detail : details) {
                Row row = tkSheet.createRow(rowNum++);
                createCell(row, 0, String.valueOf(no++), textStyle);
                createCell(row, 1, detail.getEmployeeName(), textStyle);
                createNumericCell(row, 2, detail.getGrossSalary(), numberStyle);
                createNumericCell(row, 3, detail.getBpjsJkk(), numberStyle);
                createNumericCell(row, 4, detail.getBpjsJkm(), numberStyle);
                createNumericCell(row, 5, detail.getBpjsJhtCompany(), numberStyle);
                createNumericCell(row, 6, detail.getBpjsJhtEmployee(), numberStyle);
                createNumericCell(row, 7, detail.getBpjsJpCompany(), numberStyle);
                createNumericCell(row, 8, detail.getBpjsJpEmployee(), numberStyle);

                totalJkk = totalJkk.add(detail.getBpjsJkk());
                totalJkm = totalJkm.add(detail.getBpjsJkm());
                totalJhtC = totalJhtC.add(detail.getBpjsJhtCompany());
                totalJhtE = totalJhtE.add(detail.getBpjsJhtEmployee());
                totalJpC = totalJpC.add(detail.getBpjsJpCompany());
                totalJpE = totalJpE.add(detail.getBpjsJpEmployee());
            }

            Row tkTotalRow = tkSheet.createRow(rowNum);
            createCell(tkTotalRow, 0, "", totalStyle);
            createCell(tkTotalRow, 1, TOTAL_LABEL, totalStyle);
            createCell(tkTotalRow, 2, "", totalStyle);
            createNumericCell(tkTotalRow, 3, totalJkk, totalStyle);
            createNumericCell(tkTotalRow, 4, totalJkm, totalStyle);
            createNumericCell(tkTotalRow, 5, totalJhtC, totalStyle);
            createNumericCell(tkTotalRow, 6, totalJhtE, totalStyle);
            createNumericCell(tkTotalRow, 7, totalJpC, totalStyle);
            createNumericCell(tkTotalRow, 8, totalJpE, totalStyle);
            autoSizeColumns(tkSheet, 9);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating BPJS Report Excel", e);
            throw new RuntimeException("Failed to generate Excel: " + e.getMessage());
        }
    }

    // ==================== PAYSLIP ====================

    public byte[] generatePayslipPdf(PayrollRun payrollRun, PayrollDetail detail) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A5);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header
            Paragraph company = new Paragraph(COMPANY_NAME, getTitleFont());
            company.setAlignment(Element.ALIGN_CENTER);
            document.add(company);

            Paragraph title = new Paragraph("SLIP GAJI", getTitleFont());
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(5);
            document.add(title);

            Paragraph period = new Paragraph("Periode: " + payrollRun.getPeriodDisplayName(), getSubtitleFont());
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(15);
            document.add(period);

            // Employee info
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{30, 70});
            infoTable.setSpacingAfter(15);

            addInfoRow(infoTable, "NIK", detail.getEmployeeId());
            addInfoRow(infoTable, "Nama", detail.getEmployeeName());
            addInfoRow(infoTable, "Status PTKP", detail.getEmployee().getPtkpStatus().name());
            document.add(infoTable);

            // Earnings
            document.add(new Paragraph("PENDAPATAN", getBoldFont()));
            PdfPTable earningsTable = new PdfPTable(2);
            earningsTable.setWidthPercentage(100);
            earningsTable.setWidths(new float[]{60, 40});
            earningsTable.setSpacingBefore(5);
            earningsTable.setSpacingAfter(10);

            addPayslipRow(earningsTable, "Gaji Pokok", detail.getBaseSalary());
            addPayslipRow(earningsTable, "Total Pendapatan", detail.getGrossSalary());
            document.add(earningsTable);

            // Deductions
            document.add(new Paragraph("POTONGAN", getBoldFont()));
            PdfPTable deductionsTable = new PdfPTable(2);
            deductionsTable.setWidthPercentage(100);
            deductionsTable.setWidths(new float[]{60, 40});
            deductionsTable.setSpacingBefore(5);
            deductionsTable.setSpacingAfter(10);

            addPayslipRow(deductionsTable, "BPJS Kesehatan", detail.getBpjsKesEmployee());
            addPayslipRow(deductionsTable, "BPJS JHT", detail.getBpjsJhtEmployee());
            addPayslipRow(deductionsTable, "BPJS JP", detail.getBpjsJpEmployee());
            addPayslipRow(deductionsTable, PPH_21, detail.getPph21());
            addPayslipRow(deductionsTable, TOTAL_POTONGAN, detail.getTotalDeductions());
            document.add(deductionsTable);

            // Net Pay
            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(100);
            netTable.setWidths(new float[]{60, 40});
            netTable.setSpacingBefore(10);

            PdfPCell netLabel = new PdfPCell(new Phrase("GAJI BERSIH", getBoldFont()));
            netLabel.setPadding(8);
            netLabel.setBackgroundColor(new Color(230, 230, 230));
            netLabel.setBorderWidth(1f);
            netTable.addCell(netLabel);

            PdfPCell netValue = new PdfPCell(new Phrase("Rp " + formatNumber(detail.getNetPay()), getBoldFont()));
            netValue.setPadding(8);
            netValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            netValue.setBackgroundColor(new Color(230, 230, 230));
            netValue.setBorderWidth(1f);
            netTable.addCell(netValue);

            document.add(netTable);

            // Footer
            Paragraph footer = new Paragraph("\nDokumen ini dibuat secara otomatis oleh sistem.", getSmallFont());
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Payslip PDF for employee: {}", detail.getEmployeeId(), e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    // ==================== BUKTI POTONG 1721-A1 ====================

    public byte[] generateBuktiPotong1721A1(PayrollService.YearlyPayrollSummary summary) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            var employee = summary.employee();

            // Header
            Paragraph title = new Paragraph("BUKTI PEMOTONGAN PAJAK PENGHASILAN PASAL 21", getTitleFont());
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("BAGI PEGAWAI TETAP ATAU PENERIMA PENSIUN ATAU TUNJANGAN HARI TUA/", getSubtitleFont());
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);

            Paragraph subtitle2 = new Paragraph("JAMINAN HARI TUA BERKALA (FORMULIR 1721-A1)", getSubtitleFont());
            subtitle2.setAlignment(Element.ALIGN_CENTER);
            subtitle2.setSpacingAfter(15);
            document.add(subtitle2);

            // Form header info
            Paragraph formInfo = new Paragraph("TAHUN KALENDER: " + summary.year(), getBoldFont());
            formInfo.setSpacingAfter(10);
            document.add(formInfo);

            // Section A - Pemotong Pajak
            document.add(new Paragraph("A. IDENTITAS PEMOTONG PAJAK", getBoldFont()));
            PdfPTable companyTable = new PdfPTable(2);
            companyTable.setWidthPercentage(100);
            companyTable.setWidths(new float[]{30, 70});
            companyTable.setSpacingBefore(5);
            companyTable.setSpacingAfter(15);

            addInfoRow(companyTable, "1. NPWP", "XX.XXX.XXX.X-XXX.XXX");
            addInfoRow(companyTable, "2. Nama", COMPANY_NAME);
            addInfoRow(companyTable, "3. Alamat", "Jakarta");
            document.add(companyTable);

            // Section B - Penerima Penghasilan
            document.add(new Paragraph("B. IDENTITAS PENERIMA PENGHASILAN", getBoldFont()));
            PdfPTable employeeTable = new PdfPTable(2);
            employeeTable.setWidthPercentage(100);
            employeeTable.setWidths(new float[]{30, 70});
            employeeTable.setSpacingBefore(5);
            employeeTable.setSpacingAfter(15);

            addInfoRow(employeeTable, "4. NPWP", employee.getNpwp() != null ? employee.getNpwp() : "-");
            addInfoRow(employeeTable, "5. NIK", employee.getEmployeeId());
            addInfoRow(employeeTable, "6. Nama", employee.getName());
            addInfoRow(employeeTable, "7. Status/Jumlah Tanggungan", employee.getPtkpStatus().name());
            addInfoRow(employeeTable, "8. Kode Negara Domisili", "IDN");
            document.add(employeeTable);

            // Section C - Rincian Penghasilan
            document.add(new Paragraph("C. RINCIAN PENGHASILAN DAN PENGHITUNGAN PPh PASAL 21", getBoldFont()));
            PdfPTable incomeTable = new PdfPTable(2);
            incomeTable.setWidthPercentage(100);
            incomeTable.setWidths(new float[]{60, 40});
            incomeTable.setSpacingBefore(5);
            incomeTable.setSpacingAfter(15);

            // Calculate biaya jabatan (5% of gross, max 6,000,000/year)
            var biayaJabatan = summary.totalGross().multiply(new java.math.BigDecimal("0.05"));
            var maxBiayaJabatan = new java.math.BigDecimal("6000000");
            if (biayaJabatan.compareTo(maxBiayaJabatan) > 0) {
                biayaJabatan = maxBiayaJabatan;
            }

            // Penghasilan Neto = Gross - Biaya Jabatan - BPJS JHT (employee) - BPJS JP (employee)
            var penghasilanNeto = summary.totalGross().subtract(biayaJabatan).subtract(summary.totalBpjsEmployee());

            // Get PTKP
            var ptkp = getPtkpAmount(employee.getPtkpStatus());

            // PKP = Penghasilan Neto - PTKP
            var pkp = penghasilanNeto.subtract(ptkp);
            if (pkp.compareTo(java.math.BigDecimal.ZERO) < 0) {
                pkp = java.math.BigDecimal.ZERO;
            }

            addIncomeRow(incomeTable, "9. Penghasilan Bruto (Setahun)", "Rp " + formatNumber(summary.totalGross()));
            addIncomeRow(incomeTable, "10. Biaya Jabatan (5% maks Rp 6.000.000)", "Rp " + formatNumber(biayaJabatan));
            addIncomeRow(incomeTable, "11. Iuran Pensiun/JHT/JP yang dibayar karyawan", "Rp " + formatNumber(summary.totalBpjsEmployee()));
            addIncomeRow(incomeTable, "12. Penghasilan Neto (9 - 10 - 11)", "Rp " + formatNumber(penghasilanNeto));
            addIncomeRow(incomeTable, "13. PTKP", "Rp " + formatNumber(ptkp));
            addIncomeRow(incomeTable, "14. Penghasilan Kena Pajak (12 - 13)", "Rp " + formatNumber(pkp));
            addIncomeRow(incomeTable, "15. PPh Pasal 21 Terutang", "Rp " + formatNumber(summary.totalPph21()));
            document.add(incomeTable);

            // Section D - Summary
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{60, 40});
            summaryTable.setSpacingBefore(10);

            PdfPCell labelCell = new PdfPCell(new Phrase("16. PPh PASAL 21 YANG TELAH DIPOTONG", getBoldFont()));
            labelCell.setPadding(8);
            labelCell.setBackgroundColor(new Color(230, 230, 230));
            labelCell.setBorderWidth(1f);
            summaryTable.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase("Rp " + formatNumber(summary.totalPph21()), getBoldFont()));
            valueCell.setPadding(8);
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setBackgroundColor(new Color(230, 230, 230));
            valueCell.setBorderWidth(1f);
            summaryTable.addCell(valueCell);

            document.add(summaryTable);

            // Footer
            Paragraph footer = new Paragraph("\n\nDokumen ini dibuat secara otomatis oleh sistem.", getSmallFont());
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            Paragraph note = new Paragraph("Untuk keperluan pelaporan SPT Tahunan PPh Orang Pribadi.", getSmallFont());
            note.setAlignment(Element.ALIGN_CENTER);
            document.add(note);

            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating 1721-A1 for employee: {}", summary.employee().getEmployeeId(), e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private java.math.BigDecimal getPtkpAmount(com.artivisi.accountingfinance.entity.PtkpStatus status) {
        return status.getAnnualAmount();
    }

    private void addIncomeRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getNormalFont()));
        labelCell.setPadding(5);
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, getNormalFont()));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorderWidth(0.5f);
        table.addCell(valueCell);
    }

    // ==================== HELPER METHODS ====================

    private String formatNumber(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }
        return NUMBER_FORMAT.format(value);
    }

    // PDF Fonts
    private Font getTitleFont() {
        return new Font(Font.HELVETICA, 14, Font.BOLD);
    }

    private Font getSubtitleFont() {
        return new Font(Font.HELVETICA, 10, Font.NORMAL);
    }

    private Font getHeaderFont() {
        return new Font(Font.HELVETICA, 9, Font.BOLD);
    }

    private Font getNormalFont() {
        return new Font(Font.HELVETICA, 9, Font.NORMAL);
    }

    private Font getBoldFont() {
        return new Font(Font.HELVETICA, 9, Font.BOLD);
    }

    private Font getSmallFont() {
        return new Font(Font.HELVETICA, 7, Font.ITALIC);
    }

    private void addReportHeader(Document document, String title, String subtitle, String period)
            throws DocumentException {
        Paragraph companyPara = new Paragraph(COMPANY_NAME, getTitleFont());
        companyPara.setAlignment(Element.ALIGN_CENTER);
        document.add(companyPara);

        Paragraph titlePara = new Paragraph(title, getTitleFont());
        titlePara.setAlignment(Element.ALIGN_CENTER);
        titlePara.setSpacingBefore(10);
        document.add(titlePara);

        Paragraph subtitlePara = new Paragraph(subtitle, getSubtitleFont());
        subtitlePara.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitlePara);

        Paragraph periodPara = new Paragraph(period, getSubtitleFont());
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingAfter(10);
        document.add(periodPara);
    }

    private void addSummarySection(Document document, PayrollRun payrollRun) throws DocumentException {
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(50);
        summary.setHorizontalAlignment(Element.ALIGN_LEFT);
        summary.setSpacingBefore(10);

        addSummaryRow(summary, "Jumlah Karyawan", String.valueOf(payrollRun.getEmployeeCount()));
        addSummaryRow(summary, "Total Bruto", "Rp " + formatNumber(payrollRun.getTotalGross()));
        addSummaryRow(summary, TOTAL_POTONGAN, "Rp " + formatNumber(payrollRun.getTotalDeductions()));
        addSummaryRow(summary, "Total Neto", "Rp " + formatNumber(payrollRun.getTotalNetPay()));

        document.add(summary);
    }

    private void addSummaryRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getNormalFont()));
        labelCell.setBorder(0);
        labelCell.setPadding(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(": " + value, getNormalFont()));
        valueCell.setBorder(0);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, getHeaderFont()));
            cell.setBackgroundColor(new Color(240, 240, 240));
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addTableCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, getNormalFont()));
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private PdfPCell createEmptyCell() {
        PdfPCell cell = new PdfPCell(new Phrase("", getNormalFont()));
        cell.setPadding(5);
        cell.setBackgroundColor(new Color(230, 230, 230));
        cell.setBorderWidth(1f);
        return cell;
    }

    private void addTotalRow(PdfPTable table, PayrollRun payrollRun) {
        BigDecimal totalEmployeeBpjs = payrollService.getPayrollDetails(payrollRun.getId()).stream()
            .map(PayrollDetail::getTotalEmployeeBpjs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPCell emptyCell = createEmptyCell();
        table.addCell(emptyCell);

        PdfPCell labelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(230, 230, 230));
        labelCell.setBorderWidth(1f);
        table.addCell(labelCell);

        table.addCell(emptyCell);

        PdfPCell grossCell = new PdfPCell(new Phrase(formatNumber(payrollRun.getTotalGross()), getBoldFont()));
        grossCell.setPadding(5);
        grossCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        grossCell.setBackgroundColor(new Color(230, 230, 230));
        grossCell.setBorderWidth(1f);
        table.addCell(grossCell);

        PdfPCell bpjsCell = new PdfPCell(new Phrase(formatNumber(totalEmployeeBpjs), getBoldFont()));
        bpjsCell.setPadding(5);
        bpjsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        bpjsCell.setBackgroundColor(new Color(230, 230, 230));
        bpjsCell.setBorderWidth(1f);
        table.addCell(bpjsCell);

        PdfPCell pphCell = new PdfPCell(new Phrase(formatNumber(payrollRun.getTotalPph21()), getBoldFont()));
        pphCell.setPadding(5);
        pphCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        pphCell.setBackgroundColor(new Color(230, 230, 230));
        pphCell.setBorderWidth(1f);
        table.addCell(pphCell);

        PdfPCell dedCell = new PdfPCell(new Phrase(formatNumber(payrollRun.getTotalDeductions()), getBoldFont()));
        dedCell.setPadding(5);
        dedCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        dedCell.setBackgroundColor(new Color(230, 230, 230));
        dedCell.setBorderWidth(1f);
        table.addCell(dedCell);

        PdfPCell netCell = new PdfPCell(new Phrase(formatNumber(payrollRun.getTotalNetPay()), getBoldFont()));
        netCell.setPadding(5);
        netCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        netCell.setBackgroundColor(new Color(230, 230, 230));
        netCell.setBorderWidth(1f);
        table.addCell(netCell);
    }

    private void addBpjsTotalRow(PdfPTable table, BigDecimal company, BigDecimal employee) {
        PdfPCell emptyCell = createEmptyCell();
        table.addCell(emptyCell);

        PdfPCell labelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(230, 230, 230));
        labelCell.setBorderWidth(1f);
        table.addCell(labelCell);

        table.addCell(emptyCell);

        PdfPCell companyCell = new PdfPCell(new Phrase(formatNumber(company), getBoldFont()));
        companyCell.setPadding(5);
        companyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        companyCell.setBackgroundColor(new Color(230, 230, 230));
        companyCell.setBorderWidth(1f);
        table.addCell(companyCell);

        PdfPCell employeeCell = new PdfPCell(new Phrase(formatNumber(employee), getBoldFont()));
        employeeCell.setPadding(5);
        employeeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        employeeCell.setBackgroundColor(new Color(230, 230, 230));
        employeeCell.setBorderWidth(1f);
        table.addCell(employeeCell);
    }

    private void addBpjsTkTotalRow(PdfPTable table, BigDecimal jkk, BigDecimal jkm,
            BigDecimal jhtC, BigDecimal jhtE, BigDecimal jpC, BigDecimal jpE) {
        PdfPCell emptyCell = createEmptyCell();
        table.addCell(emptyCell);

        PdfPCell labelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(230, 230, 230));
        labelCell.setBorderWidth(1f);
        table.addCell(labelCell);

        table.addCell(emptyCell);

        for (BigDecimal val : new BigDecimal[]{jkk, jkm, jhtC, jhtE, jpC, jpE}) {
            PdfPCell cell = new PdfPCell(new Phrase(formatNumber(val), getBoldFont()));
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cell.setBackgroundColor(new Color(230, 230, 230));
            cell.setBorderWidth(1f);
            table.addCell(cell);
        }
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getNormalFont()));
        labelCell.setBorder(0);
        labelCell.setPadding(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(": " + value, getNormalFont()));
        valueCell.setBorder(0);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private void addPayslipRow(PdfPTable table, String label, BigDecimal value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getNormalFont()));
        labelCell.setPadding(4);
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase("Rp " + formatNumber(value), getNormalFont()));
        valueCell.setPadding(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorderWidth(0.5f);
        table.addCell(valueCell);
    }

    // Excel Helpers
    private int addExcelHeader(Workbook workbook, Sheet sheet, int startRow, String title, String period, int columns) {
        CellStyle titleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);

        Row companyRow = sheet.createRow(startRow++);
        Cell companyCell = companyRow.createCell(0);
        companyCell.setCellValue(COMPANY_NAME);
        companyCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(startRow - 1, startRow - 1, 0, columns - 1));

        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(startRow - 1, startRow - 1, 0, columns - 1));

        CellStyle subtitleStyle = workbook.createCellStyle();
        subtitleStyle.setAlignment(HorizontalAlignment.CENTER);
        Row periodRow = sheet.createRow(startRow++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue(period);
        periodCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(startRow - 1, startRow - 1, 0, columns - 1));

        startRow++; // Empty row
        return startRow;
    }

    private int addExcelSummary(Workbook workbook, Sheet sheet, int startRow, PayrollRun payrollRun) {
        CellStyle textStyle = createTextStyle(workbook);

        Row countRow = sheet.createRow(startRow++);
        createCell(countRow, 0, "Jumlah Karyawan: " + payrollRun.getEmployeeCount(), textStyle);

        Row grossRow = sheet.createRow(startRow++);
        createCell(grossRow, 0, "Total Bruto: Rp " + formatNumber(payrollRun.getTotalGross()), textStyle);

        Row dedRow = sheet.createRow(startRow++);
        createCell(dedRow, 0, TOTAL_POTONGAN + ": Rp " + formatNumber(payrollRun.getTotalDeductions()), textStyle);

        Row netRow = sheet.createRow(startRow++);
        createCell(netRow, 0, "Total Neto: Rp " + formatNumber(payrollRun.getTotalNetPay()), textStyle);

        return startRow;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(NUMBER_PATTERN));
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(NUMBER_PATTERN));
        return style;
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
