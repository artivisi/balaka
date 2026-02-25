package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.exception.ReportGenerationException;
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

import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExportService {

    private final DepreciationReportService depreciationReportService;
    private final InventoryReportService inventoryReportService;

    private static final String COMPANY_NAME = "PT ArtiVisi Intermedia";
    private static final String PDF_GENERATION_ERROR = "Failed to generate PDF: ";
    private static final String EXCEL_GENERATION_ERROR = "Failed to generate Excel: ";
    private static final String TOTAL_LABEL = "TOTAL";
    private static final String COL_KATEGORI = "Kategori";
    private static final String COL_NAMA_PRODUK = "Nama Produk";
    private static final String COL_NILAI = "Nilai";
    private static final String COL_SATUAN = "Satuan";
    private static final String COL_HARGA_RATA_RATA = "Harga Rata-rata";
    private static final String TOTAL_LIABILITIES_EQUITY = "TOTAL LIABILITAS + EKUITAS";
    private static final String DATE_PATTERN_DMY = "dd/MM/yyyy";
    private static final String LABEL_PERIODE = "Periode ";
    private static final String LABEL_PER_TANGGAL = "Per tanggal ";
    private static final String EXCEL_NUMBER_FORMAT = "#,##0";
    private static final DecimalFormat NUMBER_FORMAT;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("id", "ID"));

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("id", "ID"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        NUMBER_FORMAT = new DecimalFormat(EXCEL_NUMBER_FORMAT, symbols);
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

    // ==================== TRIAL BALANCE ====================

    public byte[] exportTrialBalanceToPdf(ReportService.TrialBalanceReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "NERACA SALDO", "Trial Balance",
                    LABEL_PER_TANGGAL + report.asOfDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{10, 40, 25, 25});
            table.setSpacingBefore(20);

            addTableHeader(table, "Kode", "Nama Akun", "Debit", "Kredit");

            for (ReportService.TrialBalanceItem item : report.items()) {
                addTableCell(table, item.account().getAccountCode(), Element.ALIGN_LEFT);
                addTableCell(table, item.account().getAccountName(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.debitBalance()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.creditBalance()), Element.ALIGN_RIGHT);
            }

            addTotalRow(table, TOTAL_LABEL, formatNumber(report.totalDebit()), formatNumber(report.totalCredit()));

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Trial Balance PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportTrialBalanceToExcel(ReportService.TrialBalanceReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Neraca Saldo");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "NERACA SALDO",
                    LABEL_PER_TANGGAL + report.asOfDate().format(DATE_FORMAT), 4);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            createCell(headerRow, 0, "Kode", headerStyle);
            createCell(headerRow, 1, "Nama Akun", headerStyle);
            createCell(headerRow, 2, "Debit", headerStyle);
            createCell(headerRow, 3, "Kredit", headerStyle);

            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            for (ReportService.TrialBalanceItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, item.account().getAccountCode(), textStyle);
                createCell(row, 1, item.account().getAccountName(), textStyle);
                createNumericCell(row, 2, item.debitBalance(), numberStyle);
                createNumericCell(row, 3, item.creditBalance(), numberStyle);
            }

            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, TOTAL_LABEL, totalStyle);
            createNumericCell(totalRow, 2, report.totalDebit(), totalStyle);
            createNumericCell(totalRow, 3, report.totalCredit(), totalStyle);

            autoSizeColumns(sheet, 4);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Trial Balance Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== BALANCE SHEET ====================

    public byte[] exportBalanceSheetToPdf(ReportService.BalanceSheetReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN POSISI KEUANGAN", "Balance Sheet",
                    LABEL_PER_TANGGAL + report.asOfDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{70, 30});
            table.setSpacingBefore(20);

            // ASSETS
            addSectionHeader(table, "ASET");
            for (ReportService.BalanceSheetItem item : report.assetItems()) {
                addTableCell(table, "  " + item.account().getAccountName(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.balance()), Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Total Aset", formatNumber(report.totalAssets()));

            // LIABILITIES
            addSectionHeader(table, "LIABILITAS");
            for (ReportService.BalanceSheetItem item : report.liabilityItems()) {
                addTableCell(table, "  " + item.account().getAccountName(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.balance()), Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Total Liabilitas", formatNumber(report.totalLiabilities()));

            // EQUITY
            addSectionHeader(table, "EKUITAS");
            for (ReportService.BalanceSheetItem item : report.equityItems()) {
                addTableCell(table, "  " + item.account().getAccountName(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.balance()), Element.ALIGN_RIGHT);
            }
            addTableCell(table, "  Laba Tahun Berjalan", Element.ALIGN_LEFT);
            addTableCell(table, formatNumber(report.currentYearEarnings()), Element.ALIGN_RIGHT);
            addSubtotalRow(table, "Total Ekuitas", formatNumber(report.totalEquity()));

            // TOTAL LIABILITIES + EQUITY
            BigDecimal totalLiabilitiesAndEquity = report.totalLiabilities().add(report.totalEquity());
            addTotalRow(table, "TOTAL_LIABILITIES_EQUITY", formatNumber(totalLiabilitiesAndEquity), null);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Balance Sheet PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportBalanceSheetToExcel(ReportService.BalanceSheetReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Laporan Posisi Keuangan");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN POSISI KEUANGAN",
                    LABEL_PER_TANGGAL + report.asOfDate().format(DATE_FORMAT), 2);

            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            // ASSETS
            Row assetHeader = sheet.createRow(rowNum++);
            createCell(assetHeader, 0, "ASET", sectionStyle);
            for (ReportService.BalanceSheetItem item : report.assetItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.account().getAccountName(), textStyle);
                createNumericCell(row, 1, item.balance(), numberStyle);
            }
            Row assetTotal = sheet.createRow(rowNum++);
            createCell(assetTotal, 0, "Total Aset", totalStyle);
            createNumericCell(assetTotal, 1, report.totalAssets(), totalStyle);
            rowNum++;

            // LIABILITIES
            Row liabilityHeader = sheet.createRow(rowNum++);
            createCell(liabilityHeader, 0, "LIABILITAS", sectionStyle);
            for (ReportService.BalanceSheetItem item : report.liabilityItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.account().getAccountName(), textStyle);
                createNumericCell(row, 1, item.balance(), numberStyle);
            }
            Row liabilityTotal = sheet.createRow(rowNum++);
            createCell(liabilityTotal, 0, "Total Liabilitas", totalStyle);
            createNumericCell(liabilityTotal, 1, report.totalLiabilities(), totalStyle);
            rowNum++;

            // EQUITY
            Row equityHeader = sheet.createRow(rowNum++);
            createCell(equityHeader, 0, "EKUITAS", sectionStyle);
            for (ReportService.BalanceSheetItem item : report.equityItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.account().getAccountName(), textStyle);
                createNumericCell(row, 1, item.balance(), numberStyle);
            }
            Row earningsRow = sheet.createRow(rowNum++);
            createCell(earningsRow, 0, "  Laba Tahun Berjalan", textStyle);
            createNumericCell(earningsRow, 1, report.currentYearEarnings(), numberStyle);
            Row equityTotal = sheet.createRow(rowNum++);
            createCell(equityTotal, 0, "Total Ekuitas", totalStyle);
            createNumericCell(equityTotal, 1, report.totalEquity(), totalStyle);
            rowNum++;

            // TOTAL
            Row grandTotal = sheet.createRow(rowNum);
            createCell(grandTotal, 0, "TOTAL_LIABILITIES_EQUITY", totalStyle);
            createNumericCell(grandTotal, 1, report.totalLiabilities().add(report.totalEquity()), totalStyle);

            autoSizeColumns(sheet, 2);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Balance Sheet Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== INCOME STATEMENT ====================

    public byte[] exportIncomeStatementToPdf(ReportService.IncomeStatementReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN LABA RUGI", "Income Statement",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{70, 30});
            table.setSpacingBefore(20);

            // REVENUE
            addSectionHeader(table, "PENDAPATAN");
            for (ReportService.IncomeStatementItem item : report.revenueItems()) {
                addTableCell(table, "  " + item.account().getAccountName(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.balance()), Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Total Pendapatan", formatNumber(report.totalRevenue()));

            // EXPENSES
            addSectionHeader(table, "BEBAN OPERASIONAL");
            for (ReportService.IncomeStatementItem item : report.expenseItems()) {
                addTableCell(table, "  " + item.account().getAccountName(), Element.ALIGN_LEFT);
                addTableCell(table, "(" + formatNumber(item.balance()) + ")", Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Total Beban", "(" + formatNumber(report.totalExpense()) + ")");

            // NET INCOME
            String netIncomeLabel = report.netIncome().compareTo(BigDecimal.ZERO) >= 0 ? "LABA BERSIH" : "RUGI BERSIH";
            addTotalRow(table, netIncomeLabel, formatNumber(report.netIncome()), null);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Income Statement PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportIncomeStatementToExcel(ReportService.IncomeStatementReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Laporan Laba Rugi");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN LABA RUGI",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 2);

            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            // REVENUE
            Row revenueHeader = sheet.createRow(rowNum++);
            createCell(revenueHeader, 0, "PENDAPATAN", sectionStyle);
            for (ReportService.IncomeStatementItem item : report.revenueItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.account().getAccountName(), textStyle);
                createNumericCell(row, 1, item.balance(), numberStyle);
            }
            Row revenueTotal = sheet.createRow(rowNum++);
            createCell(revenueTotal, 0, "Total Pendapatan", totalStyle);
            createNumericCell(revenueTotal, 1, report.totalRevenue(), totalStyle);
            rowNum++;

            // EXPENSES
            Row expenseHeader = sheet.createRow(rowNum++);
            createCell(expenseHeader, 0, "BEBAN OPERASIONAL", sectionStyle);
            for (ReportService.IncomeStatementItem item : report.expenseItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.account().getAccountName(), textStyle);
                createNumericCell(row, 1, item.balance().negate(), numberStyle);
            }
            Row expenseTotal = sheet.createRow(rowNum++);
            createCell(expenseTotal, 0, "Total Beban", totalStyle);
            createNumericCell(expenseTotal, 1, report.totalExpense().negate(), totalStyle);
            rowNum++;

            // NET INCOME
            String netIncomeLabel = report.netIncome().compareTo(BigDecimal.ZERO) >= 0 ? "LABA BERSIH" : "RUGI BERSIH";
            Row netIncomeRow = sheet.createRow(rowNum);
            createCell(netIncomeRow, 0, netIncomeLabel, totalStyle);
            createNumericCell(netIncomeRow, 1, report.netIncome(), totalStyle);

            autoSizeColumns(sheet, 2);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Income Statement Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== CASH FLOW STATEMENT ====================

    public byte[] exportCashFlowToPdf(ReportService.CashFlowReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN ARUS KAS", "Cash Flow Statement",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{70, 30});
            table.setSpacingBefore(20);

            // OPERATING ACTIVITIES
            addSectionHeader(table, "ARUS KAS DARI AKTIVITAS OPERASI");
            for (ReportService.CashFlowItem item : report.operatingItems()) {
                addTableCell(table, "  " + item.description(), Element.ALIGN_LEFT);
                addTableCell(table, formatCashFlowNumber(item.amount()), Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Arus Kas Bersih dari Operasi", formatCashFlowNumber(report.operatingTotal()));

            // INVESTING ACTIVITIES
            addSectionHeader(table, "ARUS KAS DARI AKTIVITAS INVESTASI");
            for (ReportService.CashFlowItem item : report.investingItems()) {
                addTableCell(table, "  " + item.description(), Element.ALIGN_LEFT);
                addTableCell(table, formatCashFlowNumber(item.amount()), Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Arus Kas Bersih dari Investasi", formatCashFlowNumber(report.investingTotal()));

            // FINANCING ACTIVITIES
            addSectionHeader(table, "ARUS KAS DARI AKTIVITAS PENDANAAN");
            for (ReportService.CashFlowItem item : report.financingItems()) {
                addTableCell(table, "  " + item.description(), Element.ALIGN_LEFT);
                addTableCell(table, formatCashFlowNumber(item.amount()), Element.ALIGN_RIGHT);
            }
            addSubtotalRow(table, "Arus Kas Bersih dari Pendanaan", formatCashFlowNumber(report.financingTotal()));

            // SUMMARY
            addTableCell(table, "", Element.ALIGN_LEFT);
            addTableCell(table, "", Element.ALIGN_RIGHT);
            addSubtotalRow(table, "Kenaikan/(Penurunan) Bersih Kas", formatCashFlowNumber(report.netCashChange()));
            addSubtotalRow(table, "Saldo Kas Awal Periode", formatNumber(report.beginningCashBalance()));
            addTotalRow(table, "SALDO KAS AKHIR PERIODE", formatNumber(report.endingCashBalance()), null);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Cash Flow PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportCashFlowToExcel(ReportService.CashFlowReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Laporan Arus Kas");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN ARUS KAS",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 2);

            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            // OPERATING ACTIVITIES
            Row operatingHeader = sheet.createRow(rowNum++);
            createCell(operatingHeader, 0, "ARUS KAS DARI AKTIVITAS OPERASI", sectionStyle);
            for (ReportService.CashFlowItem item : report.operatingItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.description(), textStyle);
                createNumericCell(row, 1, item.amount(), numberStyle);
            }
            Row operatingTotal = sheet.createRow(rowNum++);
            createCell(operatingTotal, 0, "Arus Kas Bersih dari Operasi", totalStyle);
            createNumericCell(operatingTotal, 1, report.operatingTotal(), totalStyle);
            rowNum++;

            // INVESTING ACTIVITIES
            Row investingHeader = sheet.createRow(rowNum++);
            createCell(investingHeader, 0, "ARUS KAS DARI AKTIVITAS INVESTASI", sectionStyle);
            for (ReportService.CashFlowItem item : report.investingItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.description(), textStyle);
                createNumericCell(row, 1, item.amount(), numberStyle);
            }
            Row investingTotal = sheet.createRow(rowNum++);
            createCell(investingTotal, 0, "Arus Kas Bersih dari Investasi", totalStyle);
            createNumericCell(investingTotal, 1, report.investingTotal(), totalStyle);
            rowNum++;

            // FINANCING ACTIVITIES
            Row financingHeader = sheet.createRow(rowNum++);
            createCell(financingHeader, 0, "ARUS KAS DARI AKTIVITAS PENDANAAN", sectionStyle);
            for (ReportService.CashFlowItem item : report.financingItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, "  " + item.description(), textStyle);
                createNumericCell(row, 1, item.amount(), numberStyle);
            }
            Row financingTotal = sheet.createRow(rowNum++);
            createCell(financingTotal, 0, "Arus Kas Bersih dari Pendanaan", totalStyle);
            createNumericCell(financingTotal, 1, report.financingTotal(), totalStyle);
            rowNum++;

            // SUMMARY
            Row netChangeRow = sheet.createRow(rowNum++);
            createCell(netChangeRow, 0, "Kenaikan/(Penurunan) Bersih Kas", totalStyle);
            createNumericCell(netChangeRow, 1, report.netCashChange(), totalStyle);

            Row beginningRow = sheet.createRow(rowNum++);
            createCell(beginningRow, 0, "Saldo Kas Awal Periode", textStyle);
            createNumericCell(beginningRow, 1, report.beginningCashBalance(), numberStyle);

            Row endingRow = sheet.createRow(rowNum);
            createCell(endingRow, 0, "SALDO KAS AKHIR PERIODE", totalStyle);
            createNumericCell(endingRow, 1, report.endingCashBalance(), totalStyle);

            autoSizeColumns(sheet, 2);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Cash Flow Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== DEPRECIATION REPORT ====================

    public byte[] exportDepreciationToPdf(DepreciationReportService.DepreciationReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN PENYUSUTAN ASET TETAP", "Depreciation Report",
                    "Tahun " + report.year());

            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 12, 10, 8, 12, 6, 8, 12, 12, 12});
            table.setSpacingBefore(20);

            addTableHeader(table, "No", "Nama Aset", COL_KATEGORI, "Tgl Perolehan",
                    "Harga Perolehan", "Masa", "Metode", "Penyusutan Thn Ini", "Akum. Penyusutan", "Nilai Buku");

            int no = 1;
            for (DepreciationReportService.DepreciationReportItem item : report.items()) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, item.assetName(), Element.ALIGN_LEFT);
                addTableCell(table, item.categoryName(), Element.ALIGN_LEFT);
                addTableCell(table, item.purchaseDate().format(DateTimeFormatter.ofPattern(DATE_PATTERN_DMY)), Element.ALIGN_CENTER);
                addTableCell(table, formatNumber(item.purchaseCost()), Element.ALIGN_RIGHT);
                addTableCell(table, item.usefulLifeYears() + " thn", Element.ALIGN_CENTER);
                addTableCell(table, item.depreciationMethod(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.depreciationThisYear()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.accumulatedDepreciation()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.bookValue()), Element.ALIGN_RIGHT);
            }

            // Total row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
            totalLabelCell.setColspan(4);
            totalLabelCell.setPadding(6);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setBackgroundColor(new Color(230, 230, 230));
            totalLabelCell.setBorderWidth(1f);
            table.addCell(totalLabelCell);

            PdfPCell purchaseTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalPurchaseCost()), getBoldFont()));
            purchaseTotalCell.setPadding(6);
            purchaseTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            purchaseTotalCell.setBackgroundColor(new Color(230, 230, 230));
            purchaseTotalCell.setBorderWidth(1f);
            table.addCell(purchaseTotalCell);

            PdfPCell emptyCell = new PdfPCell(new Phrase("", getBoldFont()));
            emptyCell.setColspan(2);
            emptyCell.setPadding(6);
            emptyCell.setBackgroundColor(new Color(230, 230, 230));
            emptyCell.setBorderWidth(1f);
            table.addCell(emptyCell);

            PdfPCell depreciationTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalDepreciationThisYear()), getBoldFont()));
            depreciationTotalCell.setPadding(6);
            depreciationTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            depreciationTotalCell.setBackgroundColor(new Color(230, 230, 230));
            depreciationTotalCell.setBorderWidth(1f);
            table.addCell(depreciationTotalCell);

            PdfPCell accumTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalAccumulatedDepreciation()), getBoldFont()));
            accumTotalCell.setPadding(6);
            accumTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            accumTotalCell.setBackgroundColor(new Color(230, 230, 230));
            accumTotalCell.setBorderWidth(1f);
            table.addCell(accumTotalCell);

            PdfPCell bookValueTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalBookValue()), getBoldFont()));
            bookValueTotalCell.setPadding(6);
            bookValueTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            bookValueTotalCell.setBackgroundColor(new Color(230, 230, 230));
            bookValueTotalCell.setBorderWidth(1f);
            table.addCell(bookValueTotalCell);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Depreciation Report PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportDepreciationToExcel(DepreciationReportService.DepreciationReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Laporan Penyusutan");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN PENYUSUTAN ASET TETAP",
                    "Tahun " + report.year(), 10);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            createCell(headerRow, 0, "No", headerStyle);
            createCell(headerRow, 1, "Nama Aset", headerStyle);
            createCell(headerRow, 2, COL_KATEGORI, headerStyle);
            createCell(headerRow, 3, "Tgl Perolehan", headerStyle);
            createCell(headerRow, 4, "Harga Perolehan", headerStyle);
            createCell(headerRow, 5, "Masa Manfaat", headerStyle);
            createCell(headerRow, 6, "Metode", headerStyle);
            createCell(headerRow, 7, "Penyusutan Tahun Ini", headerStyle);
            createCell(headerRow, 8, "Akum. Penyusutan", headerStyle);
            createCell(headerRow, 9, "Nilai Buku", headerStyle);

            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            int no = 1;
            for (DepreciationReportService.DepreciationReportItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createNumericCell(row, 0, java.math.BigDecimal.valueOf(no++), textStyle);
                createCell(row, 1, item.assetName(), textStyle);
                createCell(row, 2, item.categoryName(), textStyle);
                Cell dateCell = row.createCell(3);
                dateCell.setCellValue(java.sql.Date.valueOf(item.purchaseDate()));
                dateCell.setCellStyle(dateStyle);
                createNumericCell(row, 4, item.purchaseCost(), numberStyle);
                createCell(row, 5, item.usefulLifeYears() + " tahun", textStyle);
                createCell(row, 6, item.depreciationMethod(), textStyle);
                createNumericCell(row, 7, item.depreciationThisYear(), numberStyle);
                createNumericCell(row, 8, item.accumulatedDepreciation(), numberStyle);
                createNumericCell(row, 9, item.bookValue(), numberStyle);
            }

            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, "", totalStyle);
            createCell(totalRow, 2, "", totalStyle);
            createCell(totalRow, 3, TOTAL_LABEL, totalStyle);
            createNumericCell(totalRow, 4, report.totalPurchaseCost(), totalStyle);
            createCell(totalRow, 5, "", totalStyle);
            createCell(totalRow, 6, "", totalStyle);
            createNumericCell(totalRow, 7, report.totalDepreciationThisYear(), totalStyle);
            createNumericCell(totalRow, 8, report.totalAccumulatedDepreciation(), totalStyle);
            createNumericCell(totalRow, 9, report.totalBookValue(), totalStyle);

            autoSizeColumns(sheet, 10);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Depreciation Report Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== STOCK BALANCE REPORT ====================

    public byte[] exportStockBalanceToPdf(InventoryReportService.StockBalanceReport report, LocalDate asOfDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN SALDO STOK", "Stock Balance Report",
                    LABEL_PER_TANGGAL + asOfDate.format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 10, 15, 10, 8, 10, 12, 12});
            table.setSpacingBefore(20);

            addTableHeader(table, "No", "Kode", COL_NAMA_PRODUK, COL_KATEGORI, COL_SATUAN, "Qty", COL_HARGA_RATA_RATA, COL_NILAI);

            int no = 1;
            for (InventoryReportService.StockBalanceItem item : report.items()) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, item.productCode(), Element.ALIGN_LEFT);
                addTableCell(table, item.productName(), Element.ALIGN_LEFT);
                addTableCell(table, item.categoryName(), Element.ALIGN_LEFT);
                addTableCell(table, item.unit(), Element.ALIGN_CENTER);
                addTableCell(table, formatNumber(item.quantity()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.averageCost()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.totalValue()), Element.ALIGN_RIGHT);
            }

            // Total row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
            totalLabelCell.setColspan(5);
            totalLabelCell.setPadding(6);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setBackgroundColor(new Color(230, 230, 230));
            totalLabelCell.setBorderWidth(1f);
            table.addCell(totalLabelCell);

            PdfPCell qtyTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalQuantity()), getBoldFont()));
            qtyTotalCell.setPadding(6);
            qtyTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            qtyTotalCell.setBackgroundColor(new Color(230, 230, 230));
            qtyTotalCell.setBorderWidth(1f);
            table.addCell(qtyTotalCell);

            PdfPCell emptyCell = new PdfPCell(new Phrase("", getBoldFont()));
            emptyCell.setPadding(6);
            emptyCell.setBackgroundColor(new Color(230, 230, 230));
            emptyCell.setBorderWidth(1f);
            table.addCell(emptyCell);

            PdfPCell valueTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalValue()), getBoldFont()));
            valueTotalCell.setPadding(6);
            valueTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueTotalCell.setBackgroundColor(new Color(230, 230, 230));
            valueTotalCell.setBorderWidth(1f);
            table.addCell(valueTotalCell);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Stock Balance PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportStockBalanceToExcel(InventoryReportService.StockBalanceReport report, LocalDate asOfDate) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Saldo Stok");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN SALDO STOK",
                    LABEL_PER_TANGGAL + asOfDate.format(DATE_FORMAT), 8);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            createCell(headerRow, 0, "No", headerStyle);
            createCell(headerRow, 1, "Kode", headerStyle);
            createCell(headerRow, 2, COL_NAMA_PRODUK, headerStyle);
            createCell(headerRow, 3, COL_KATEGORI, headerStyle);
            createCell(headerRow, 4, COL_SATUAN, headerStyle);
            createCell(headerRow, 5, "Qty", headerStyle);
            createCell(headerRow, 6, COL_HARGA_RATA_RATA, headerStyle);
            createCell(headerRow, 7, COL_NILAI, headerStyle);

            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            int no = 1;
            for (InventoryReportService.StockBalanceItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createNumericCell(row, 0, BigDecimal.valueOf(no++), textStyle);
                createCell(row, 1, item.productCode(), textStyle);
                createCell(row, 2, item.productName(), textStyle);
                createCell(row, 3, item.categoryName(), textStyle);
                createCell(row, 4, item.unit(), textStyle);
                createNumericCell(row, 5, item.quantity(), numberStyle);
                createNumericCell(row, 6, item.averageCost(), numberStyle);
                createNumericCell(row, 7, item.totalValue(), numberStyle);
            }

            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, "", totalStyle);
            createCell(totalRow, 2, "", totalStyle);
            createCell(totalRow, 3, "", totalStyle);
            createCell(totalRow, 4, TOTAL_LABEL, totalStyle);
            createNumericCell(totalRow, 5, report.totalQuantity(), totalStyle);
            createCell(totalRow, 6, "", totalStyle);
            createNumericCell(totalRow, 7, report.totalValue(), totalStyle);

            autoSizeColumns(sheet, 8);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Stock Balance Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== STOCK MOVEMENT REPORT ====================

    public byte[] exportStockMovementToPdf(InventoryReportService.StockMovementReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN MUTASI STOK", "Stock Movement Report",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 8, 10, 12, 10, 10, 10, 10, 10});
            table.setSpacingBefore(20);

            addTableHeader(table, "No", "Tanggal", "Kode", COL_NAMA_PRODUK, "Tipe", "Qty", "Harga", COL_NILAI, "Saldo");

            int no = 1;
            for (InventoryReportService.StockMovementItem item : report.items()) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, item.transactionDate().format(DateTimeFormatter.ofPattern(DATE_PATTERN_DMY)), Element.ALIGN_CENTER);
                addTableCell(table, item.productCode(), Element.ALIGN_LEFT);
                addTableCell(table, item.productName(), Element.ALIGN_LEFT);
                addTableCell(table, item.transactionTypeLabel(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.quantity()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.unitCost()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.totalCost()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.balanceAfter()), Element.ALIGN_RIGHT);
            }

            document.add(table);

            // Summary table
            Paragraph summaryTitle = new Paragraph("Ringkasan", getBoldFont());
            summaryTitle.setSpacingBefore(20);
            document.add(summaryTitle);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(40);
            summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            summaryTable.setSpacingBefore(10);

            addTableCell(summaryTable, "Total Masuk (Qty)", Element.ALIGN_LEFT);
            addTableCell(summaryTable, formatNumber(report.totalInboundQty()), Element.ALIGN_RIGHT);
            addTableCell(summaryTable, "Total Keluar (Qty)", Element.ALIGN_LEFT);
            addTableCell(summaryTable, formatNumber(report.totalOutboundQty()), Element.ALIGN_RIGHT);
            addTableCell(summaryTable, "Total Masuk (Nilai)", Element.ALIGN_LEFT);
            addTableCell(summaryTable, formatNumber(report.totalInboundValue()), Element.ALIGN_RIGHT);
            addTableCell(summaryTable, "Total Keluar (Nilai)", Element.ALIGN_LEFT);
            addTableCell(summaryTable, formatNumber(report.totalOutboundValue()), Element.ALIGN_RIGHT);

            document.add(summaryTable);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Stock Movement PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportStockMovementToExcel(InventoryReportService.StockMovementReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Mutasi Stok");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN MUTASI STOK",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 9);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            createCell(headerRow, 0, "No", headerStyle);
            createCell(headerRow, 1, "Tanggal", headerStyle);
            createCell(headerRow, 2, "Kode", headerStyle);
            createCell(headerRow, 3, COL_NAMA_PRODUK, headerStyle);
            createCell(headerRow, 4, "Tipe", headerStyle);
            createCell(headerRow, 5, "Qty", headerStyle);
            createCell(headerRow, 6, "Harga", headerStyle);
            createCell(headerRow, 7, COL_NILAI, headerStyle);
            createCell(headerRow, 8, "Saldo", headerStyle);

            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            int no = 1;
            for (InventoryReportService.StockMovementItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createNumericCell(row, 0, BigDecimal.valueOf(no++), textStyle);
                Cell dateCell = row.createCell(1);
                dateCell.setCellValue(java.sql.Date.valueOf(item.transactionDate()));
                dateCell.setCellStyle(dateStyle);
                createCell(row, 2, item.productCode(), textStyle);
                createCell(row, 3, item.productName(), textStyle);
                createCell(row, 4, item.transactionTypeLabel(), textStyle);
                createNumericCell(row, 5, item.quantity(), numberStyle);
                createNumericCell(row, 6, item.unitCost(), numberStyle);
                createNumericCell(row, 7, item.totalCost(), numberStyle);
                createNumericCell(row, 8, item.balanceAfter(), numberStyle);
            }

            // Summary
            rowNum += 2;
            CellStyle totalStyle = createTotalStyle(workbook);

            Row summaryHeader = sheet.createRow(rowNum++);
            createCell(summaryHeader, 0, "Ringkasan", totalStyle);

            Row inboundQtyRow = sheet.createRow(rowNum++);
            createCell(inboundQtyRow, 0, "Total Masuk (Qty)", textStyle);
            createNumericCell(inboundQtyRow, 1, report.totalInboundQty(), numberStyle);

            Row outboundQtyRow = sheet.createRow(rowNum++);
            createCell(outboundQtyRow, 0, "Total Keluar (Qty)", textStyle);
            createNumericCell(outboundQtyRow, 1, report.totalOutboundQty(), numberStyle);

            Row inboundValueRow = sheet.createRow(rowNum++);
            createCell(inboundValueRow, 0, "Total Masuk (Nilai)", textStyle);
            createNumericCell(inboundValueRow, 1, report.totalInboundValue(), numberStyle);

            Row outboundValueRow = sheet.createRow(rowNum);
            createCell(outboundValueRow, 0, "Total Keluar (Nilai)", textStyle);
            createNumericCell(outboundValueRow, 1, report.totalOutboundValue(), numberStyle);

            autoSizeColumns(sheet, 9);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Stock Movement Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== INVENTORY VALUATION REPORT ====================

    public byte[] exportValuationToPdf(InventoryReportService.ValuationReport report, LocalDate asOfDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN PENILAIAN PERSEDIAAN", "Inventory Valuation Report",
                    LABEL_PER_TANGGAL + asOfDate.format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 10, 15, 10, 8, 10, 12, 12});
            table.setSpacingBefore(20);

            addTableHeader(table, "No", "Kode", COL_NAMA_PRODUK, COL_KATEGORI, COL_SATUAN, "Qty", COL_HARGA_RATA_RATA, COL_NILAI);

            int no = 1;
            for (InventoryReportService.ValuationItem item : report.items()) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, item.productCode(), Element.ALIGN_LEFT);
                addTableCell(table, item.productName(), Element.ALIGN_LEFT);
                addTableCell(table, item.categoryName(), Element.ALIGN_LEFT);
                addTableCell(table, item.unit(), Element.ALIGN_CENTER);
                addTableCell(table, formatNumber(item.quantity()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.averageCost()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.totalValue()), Element.ALIGN_RIGHT);
            }

            // Total row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
            totalLabelCell.setColspan(7);
            totalLabelCell.setPadding(6);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setBackgroundColor(new Color(230, 230, 230));
            totalLabelCell.setBorderWidth(1f);
            table.addCell(totalLabelCell);

            PdfPCell valueTotalCell = new PdfPCell(new Phrase(formatNumber(report.totalValue()), getBoldFont()));
            valueTotalCell.setPadding(6);
            valueTotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueTotalCell.setBackgroundColor(new Color(230, 230, 230));
            valueTotalCell.setBorderWidth(1f);
            table.addCell(valueTotalCell);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Valuation PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportValuationToExcel(InventoryReportService.ValuationReport report, LocalDate asOfDate) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Penilaian Persediaan");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN PENILAIAN PERSEDIAAN",
                    LABEL_PER_TANGGAL + asOfDate.format(DATE_FORMAT), 8);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            createCell(headerRow, 0, "No", headerStyle);
            createCell(headerRow, 1, "Kode", headerStyle);
            createCell(headerRow, 2, COL_NAMA_PRODUK, headerStyle);
            createCell(headerRow, 3, COL_KATEGORI, headerStyle);
            createCell(headerRow, 4, COL_SATUAN, headerStyle);
            createCell(headerRow, 5, "Qty", headerStyle);
            createCell(headerRow, 6, COL_HARGA_RATA_RATA, headerStyle);
            createCell(headerRow, 7, COL_NILAI, headerStyle);

            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            int no = 1;
            for (InventoryReportService.ValuationItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createNumericCell(row, 0, BigDecimal.valueOf(no++), textStyle);
                createCell(row, 1, item.productCode(), textStyle);
                createCell(row, 2, item.productName(), textStyle);
                createCell(row, 3, item.categoryName(), textStyle);
                createCell(row, 4, item.unit(), textStyle);
                createNumericCell(row, 5, item.quantity(), numberStyle);
                createNumericCell(row, 6, item.averageCost(), numberStyle);
                createNumericCell(row, 7, item.totalValue(), numberStyle);
            }

            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, "", totalStyle);
            createCell(totalRow, 2, "", totalStyle);
            createCell(totalRow, 3, "", totalStyle);
            createCell(totalRow, 4, "", totalStyle);
            createCell(totalRow, 5, "", totalStyle);
            createCell(totalRow, 6, TOTAL_LABEL, totalStyle);
            createNumericCell(totalRow, 7, report.totalValue(), totalStyle);

            autoSizeColumns(sheet, 8);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Valuation Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== PRODUCT PROFITABILITY REPORT ====================

    public byte[] exportProductProfitabilityToPdf(InventoryReportService.ProfitabilityReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "LAPORAN PROFITABILITAS PRODUK", "Product Profitability Report",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 8, 15, 10, 8, 12, 12, 12, 8});
            table.setSpacingBefore(20);

            addTableHeader(table, "No", "Kode", COL_NAMA_PRODUK, COL_KATEGORI, "Qty", "Pendapatan", "HPP", "Margin", "%");

            int no = 1;
            for (InventoryReportService.ProfitabilityItem item : report.items()) {
                addTableCell(table, String.valueOf(no++), Element.ALIGN_CENTER);
                addTableCell(table, item.productCode(), Element.ALIGN_LEFT);
                addTableCell(table, item.productName(), Element.ALIGN_LEFT);
                addTableCell(table, item.categoryName(), Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.quantitySold()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.revenue()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.cogs()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.margin()), Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.marginPercent()) + "%", Element.ALIGN_RIGHT);
            }

            // Total row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
            totalLabelCell.setColspan(4);
            totalLabelCell.setPadding(6);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setBackgroundColor(new Color(230, 230, 230));
            totalLabelCell.setBorderWidth(1f);
            table.addCell(totalLabelCell);

            PdfPCell qtyCell = new PdfPCell(new Phrase(formatNumber(report.totalQuantitySold()), getBoldFont()));
            qtyCell.setPadding(6);
            qtyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            qtyCell.setBackgroundColor(new Color(230, 230, 230));
            qtyCell.setBorderWidth(1f);
            table.addCell(qtyCell);

            PdfPCell revenueCell = new PdfPCell(new Phrase(formatNumber(report.totalRevenue()), getBoldFont()));
            revenueCell.setPadding(6);
            revenueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            revenueCell.setBackgroundColor(new Color(230, 230, 230));
            revenueCell.setBorderWidth(1f);
            table.addCell(revenueCell);

            PdfPCell cogsCell = new PdfPCell(new Phrase(formatNumber(report.totalCogs()), getBoldFont()));
            cogsCell.setPadding(6);
            cogsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cogsCell.setBackgroundColor(new Color(230, 230, 230));
            cogsCell.setBorderWidth(1f);
            table.addCell(cogsCell);

            PdfPCell marginCell = new PdfPCell(new Phrase(formatNumber(report.totalMargin()), getBoldFont()));
            marginCell.setPadding(6);
            marginCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            marginCell.setBackgroundColor(new Color(230, 230, 230));
            marginCell.setBorderWidth(1f);
            table.addCell(marginCell);

            PdfPCell percentCell = new PdfPCell(new Phrase(formatNumber(report.getTotalMarginPercent()) + "%", getBoldFont()));
            percentCell.setPadding(6);
            percentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            percentCell.setBackgroundColor(new Color(230, 230, 230));
            percentCell.setBorderWidth(1f);
            table.addCell(percentCell);

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Product Profitability PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportProductProfitabilityToExcel(InventoryReportService.ProfitabilityReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Profitabilitas Produk");
            int rowNum = 0;

            rowNum = addExcelHeader(workbook, sheet, rowNum, "LAPORAN PROFITABILITAS PRODUK",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 9);

            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = createHeaderStyle(workbook);
            createCell(headerRow, 0, "No", headerStyle);
            createCell(headerRow, 1, "Kode", headerStyle);
            createCell(headerRow, 2, COL_NAMA_PRODUK, headerStyle);
            createCell(headerRow, 3, COL_KATEGORI, headerStyle);
            createCell(headerRow, 4, "Qty Terjual", headerStyle);
            createCell(headerRow, 5, "Pendapatan", headerStyle);
            createCell(headerRow, 6, "HPP", headerStyle);
            createCell(headerRow, 7, "Margin", headerStyle);
            createCell(headerRow, 8, "Margin %", headerStyle);

            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);

            int no = 1;
            for (InventoryReportService.ProfitabilityItem item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createNumericCell(row, 0, BigDecimal.valueOf(no++), textStyle);
                createCell(row, 1, item.productCode(), textStyle);
                createCell(row, 2, item.productName(), textStyle);
                createCell(row, 3, item.categoryName(), textStyle);
                createNumericCell(row, 4, item.quantitySold(), numberStyle);
                createNumericCell(row, 5, item.revenue(), numberStyle);
                createNumericCell(row, 6, item.cogs(), numberStyle);
                createNumericCell(row, 7, item.margin(), numberStyle);
                createNumericCell(row, 8, item.marginPercent().divide(BigDecimal.valueOf(100)), percentStyle);
            }

            Row totalRow = sheet.createRow(rowNum);
            CellStyle totalStyle = createTotalStyle(workbook);
            createCell(totalRow, 0, "", totalStyle);
            createCell(totalRow, 1, "", totalStyle);
            createCell(totalRow, 2, "", totalStyle);
            createCell(totalRow, 3, TOTAL_LABEL, totalStyle);
            createNumericCell(totalRow, 4, report.totalQuantitySold(), totalStyle);
            createNumericCell(totalRow, 5, report.totalRevenue(), totalStyle);
            createNumericCell(totalRow, 6, report.totalCogs(), totalStyle);
            createNumericCell(totalRow, 7, report.totalMargin(), totalStyle);
            Cell percentTotalCell = totalRow.createCell(8);
            percentTotalCell.setCellValue(report.getTotalMarginPercent().divide(BigDecimal.valueOf(100)).doubleValue());
            percentTotalCell.setCellStyle(percentStyle);

            autoSizeColumns(sheet, 9);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Product Profitability Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== PPN DETAIL ====================

    public byte[] exportPpnDetailToPdf(TaxReportDetailService.PPNDetailReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "RINCIAN PPN PER FAKTUR", "VAT Detail Report",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            // Keluaran section
            Paragraph keluaranTitle = new Paragraph("PPN Keluaran", getBoldFont());
            keluaranTitle.setSpacingBefore(15);
            document.add(keluaranTitle);

            PdfPTable keluaranTable = new PdfPTable(7);
            keluaranTable.setWidthPercentage(100);
            keluaranTable.setWidths(new float[]{18, 10, 5, 20, 17, 15, 15});
            keluaranTable.setSpacingBefore(5);
            addTableHeader(keluaranTable, "No. Faktur", "Tanggal", "Kode", "Lawan Transaksi", "NPWP", "DPP", "PPN");

            for (TaxTransactionDetail item : report.keluaranItems()) {
                addTableCell(keluaranTable, item.getFakturNumber() != null ? item.getFakturNumber() : "-", Element.ALIGN_LEFT);
                addTableCell(keluaranTable, item.getFakturDate() != null ? item.getFakturDate().format(DateTimeFormatter.ofPattern(DATE_PATTERN_DMY)) : "-", Element.ALIGN_LEFT);
                addTableCell(keluaranTable, item.getTransactionCode() != null ? item.getTransactionCode() : "-", Element.ALIGN_CENTER);
                addTableCell(keluaranTable, item.getCounterpartyName() != null ? item.getCounterpartyName() : "-", Element.ALIGN_LEFT);
                addTableCell(keluaranTable, item.getCounterpartyNpwp() != null ? item.getCounterpartyNpwp() : "-", Element.ALIGN_LEFT);
                addTableCell(keluaranTable, formatNumber(item.getDpp()), Element.ALIGN_RIGHT);
                addTableCell(keluaranTable, formatNumber(item.getPpn()), Element.ALIGN_RIGHT);
            }
            addPpnTotalRow(keluaranTable, "Total PPN Keluaran", formatNumber(report.totalDppKeluaran()), formatNumber(report.totalPpnKeluaran()));
            document.add(keluaranTable);

            // Masukan section
            Paragraph masukanTitle = new Paragraph("PPN Masukan", getBoldFont());
            masukanTitle.setSpacingBefore(15);
            document.add(masukanTitle);

            PdfPTable masukanTable = new PdfPTable(7);
            masukanTable.setWidthPercentage(100);
            masukanTable.setWidths(new float[]{18, 10, 5, 20, 17, 15, 15});
            masukanTable.setSpacingBefore(5);
            addTableHeader(masukanTable, "No. Faktur", "Tanggal", "Kode", "Lawan Transaksi", "NPWP", "DPP", "PPN");

            for (TaxTransactionDetail item : report.masukanItems()) {
                addTableCell(masukanTable, item.getFakturNumber() != null ? item.getFakturNumber() : "-", Element.ALIGN_LEFT);
                addTableCell(masukanTable, item.getFakturDate() != null ? item.getFakturDate().format(DateTimeFormatter.ofPattern(DATE_PATTERN_DMY)) : "-", Element.ALIGN_LEFT);
                addTableCell(masukanTable, item.getTransactionCode() != null ? item.getTransactionCode() : "-", Element.ALIGN_CENTER);
                addTableCell(masukanTable, item.getCounterpartyName() != null ? item.getCounterpartyName() : "-", Element.ALIGN_LEFT);
                addTableCell(masukanTable, item.getCounterpartyNpwp() != null ? item.getCounterpartyNpwp() : "-", Element.ALIGN_LEFT);
                addTableCell(masukanTable, formatNumber(item.getDpp()), Element.ALIGN_RIGHT);
                addTableCell(masukanTable, formatNumber(item.getPpn()), Element.ALIGN_RIGHT);
            }
            addPpnTotalRow(masukanTable, "Total PPN Masukan", formatNumber(report.totalDppMasukan()), formatNumber(report.totalPpnMasukan()));
            document.add(masukanTable);

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating PPN Detail PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportPpnDetailToExcel(TaxReportDetailService.PPNDetailReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Rincian PPN");
            int rowNum = 0;
            rowNum = addExcelHeader(workbook, sheet, rowNum, "RINCIAN PPN PER FAKTUR",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 7);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            // Keluaran
            Row keluaranHeader = sheet.createRow(rowNum++);
            createCell(keluaranHeader, 0, "PPN Keluaran", sectionStyle);

            Row kHdr = sheet.createRow(rowNum++);
            String[] headers = {"No. Faktur", "Tanggal", "Kode", "Lawan Transaksi", "NPWP", "DPP", "PPN"};
            for (int i = 0; i < headers.length; i++) createCell(kHdr, i, headers[i], headerStyle);

            for (TaxTransactionDetail item : report.keluaranItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, item.getFakturNumber() != null ? item.getFakturNumber() : "-", textStyle);
                createCell(row, 1, item.getFakturDate() != null ? item.getFakturDate().format(DateTimeFormatter.ofPattern(DATE_PATTERN_DMY)) : "-", textStyle);
                createCell(row, 2, item.getTransactionCode() != null ? item.getTransactionCode() : "-", textStyle);
                createCell(row, 3, item.getCounterpartyName() != null ? item.getCounterpartyName() : "-", textStyle);
                createCell(row, 4, item.getCounterpartyNpwp() != null ? item.getCounterpartyNpwp() : "-", textStyle);
                createNumericCell(row, 5, item.getDpp(), numberStyle);
                createNumericCell(row, 6, item.getPpn(), numberStyle);
            }
            Row kTotal = sheet.createRow(rowNum++);
            createCell(kTotal, 0, "Total PPN Keluaran", totalStyle);
            for (int i = 1; i < 5; i++) createCell(kTotal, i, "", totalStyle);
            createNumericCell(kTotal, 5, report.totalDppKeluaran(), totalStyle);
            createNumericCell(kTotal, 6, report.totalPpnKeluaran(), totalStyle);
            rowNum++;

            // Masukan
            Row masukanHeader = sheet.createRow(rowNum++);
            createCell(masukanHeader, 0, "PPN Masukan", sectionStyle);

            Row mHdr = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) createCell(mHdr, i, headers[i], headerStyle);

            for (TaxTransactionDetail item : report.masukanItems()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, item.getFakturNumber() != null ? item.getFakturNumber() : "-", textStyle);
                createCell(row, 1, item.getFakturDate() != null ? item.getFakturDate().format(DateTimeFormatter.ofPattern(DATE_PATTERN_DMY)) : "-", textStyle);
                createCell(row, 2, item.getTransactionCode() != null ? item.getTransactionCode() : "-", textStyle);
                createCell(row, 3, item.getCounterpartyName() != null ? item.getCounterpartyName() : "-", textStyle);
                createCell(row, 4, item.getCounterpartyNpwp() != null ? item.getCounterpartyNpwp() : "-", textStyle);
                createNumericCell(row, 5, item.getDpp(), numberStyle);
                createNumericCell(row, 6, item.getPpn(), numberStyle);
            }
            Row mTotal = sheet.createRow(rowNum);
            createCell(mTotal, 0, "Total PPN Masukan", totalStyle);
            for (int i = 1; i < 5; i++) createCell(mTotal, i, "", totalStyle);
            createNumericCell(mTotal, 5, report.totalDppMasukan(), totalStyle);
            createNumericCell(mTotal, 6, report.totalPpnMasukan(), totalStyle);

            autoSizeColumns(sheet, 7);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating PPN Detail Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== PPh 23 DETAIL ====================

    public byte[] exportPph23DetailToPdf(TaxReportDetailService.PPh23DetailReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "RINCIAN PPh 23 PER BUKTI POTONG", "WHT Art. 23 Detail Report",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{18, 10, 20, 17, 15, 8, 12});
            table.setSpacingBefore(20);
            addTableHeader(table, "No. Bupot", "Kode Objek", "Lawan Transaksi", "NPWP", "Bruto", "Tarif", "PPh 23");

            for (TaxTransactionDetail item : report.items()) {
                addTableCell(table, item.getBupotNumber() != null ? item.getBupotNumber() : "-", Element.ALIGN_LEFT);
                addTableCell(table, item.getTaxObjectCode() != null ? item.getTaxObjectCode() : "-", Element.ALIGN_LEFT);
                addTableCell(table, item.getCounterpartyName() != null ? item.getCounterpartyName() : "-", Element.ALIGN_LEFT);
                addTableCell(table, item.getCounterpartyNpwp() != null ? item.getCounterpartyNpwp() : "-", Element.ALIGN_LEFT);
                addTableCell(table, formatNumber(item.getGrossAmount()), Element.ALIGN_RIGHT);
                addTableCell(table, item.getTaxRate() != null ? item.getTaxRate() + "%" : "-", Element.ALIGN_RIGHT);
                addTableCell(table, formatNumber(item.getTaxAmount()), Element.ALIGN_RIGHT);
            }

            addPph23TotalRow(table, formatNumber(report.totalGross()), formatNumber(report.totalTax()));
            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating PPh 23 Detail PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportPph23DetailToExcel(TaxReportDetailService.PPh23DetailReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Rincian PPh 23");
            int rowNum = 0;
            rowNum = addExcelHeader(workbook, sheet, rowNum, "RINCIAN PPh 23 PER BUKTI POTONG",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 7);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            Row hdr = sheet.createRow(rowNum++);
            String[] headers = {"No. Bupot", "Kode Objek", "Lawan Transaksi", "NPWP", "Bruto", "Tarif", "PPh 23"};
            for (int i = 0; i < headers.length; i++) createCell(hdr, i, headers[i], headerStyle);

            for (TaxTransactionDetail item : report.items()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, item.getBupotNumber() != null ? item.getBupotNumber() : "-", textStyle);
                createCell(row, 1, item.getTaxObjectCode() != null ? item.getTaxObjectCode() : "-", textStyle);
                createCell(row, 2, item.getCounterpartyName() != null ? item.getCounterpartyName() : "-", textStyle);
                createCell(row, 3, item.getCounterpartyNpwp() != null ? item.getCounterpartyNpwp() : "-", textStyle);
                createNumericCell(row, 4, item.getGrossAmount(), numberStyle);
                createCell(row, 5, item.getTaxRate() != null ? item.getTaxRate() + "%" : "-", textStyle);
                createNumericCell(row, 6, item.getTaxAmount(), numberStyle);
            }

            Row total = sheet.createRow(rowNum);
            createCell(total, 0, TOTAL_LABEL, totalStyle);
            for (int i = 1; i < 4; i++) createCell(total, i, "", totalStyle);
            createNumericCell(total, 4, report.totalGross(), totalStyle);
            createCell(total, 5, "", totalStyle);
            createNumericCell(total, 6, report.totalTax(), totalStyle);

            autoSizeColumns(sheet, 7);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating PPh 23 Detail Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== PPN CROSS-CHECK ====================

    public byte[] exportPpnCrosscheckToPdf(TaxReportDetailService.PPNCrossCheckReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "CROSS-CHECK PPN", "Faktur vs Buku Besar",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{30, 23, 24, 23});
            table.setSpacingBefore(20);
            addTableHeader(table, "Komponen", "Faktur Pajak", "Buku Besar", "Selisih");

            addTableCell(table, "PPN Keluaran", Element.ALIGN_LEFT);
            addTableCell(table, formatNumber(report.fakturPpnKeluaran()), Element.ALIGN_RIGHT);
            addTableCell(table, formatNumber(report.ledgerPpnKeluaran()), Element.ALIGN_RIGHT);
            addTableCell(table, formatNumber(report.keluaranDifference()), Element.ALIGN_RIGHT);

            addTableCell(table, "PPN Masukan", Element.ALIGN_LEFT);
            addTableCell(table, formatNumber(report.fakturPpnMasukan()), Element.ALIGN_RIGHT);
            addTableCell(table, formatNumber(report.ledgerPpnMasukan()), Element.ALIGN_RIGHT);
            addTableCell(table, formatNumber(report.masukanDifference()), Element.ALIGN_RIGHT);

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating PPN Crosscheck PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportPpnCrosscheckToExcel(TaxReportDetailService.PPNCrossCheckReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Cross-check PPN");
            int rowNum = 0;
            rowNum = addExcelHeader(workbook, sheet, rowNum, "CROSS-CHECK PPN",
                    LABEL_PERIODE + report.startDate().format(DATE_FORMAT) + " - " + report.endDate().format(DATE_FORMAT), 4);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            Row hdr = sheet.createRow(rowNum++);
            createCell(hdr, 0, "Komponen", headerStyle);
            createCell(hdr, 1, "Faktur Pajak", headerStyle);
            createCell(hdr, 2, "Buku Besar", headerStyle);
            createCell(hdr, 3, "Selisih", headerStyle);

            Row keluaran = sheet.createRow(rowNum++);
            createCell(keluaran, 0, "PPN Keluaran", textStyle);
            createNumericCell(keluaran, 1, report.fakturPpnKeluaran(), numberStyle);
            createNumericCell(keluaran, 2, report.ledgerPpnKeluaran(), numberStyle);
            createNumericCell(keluaran, 3, report.keluaranDifference(), numberStyle);

            Row masukan = sheet.createRow(rowNum);
            createCell(masukan, 0, "PPN Masukan", textStyle);
            createNumericCell(masukan, 1, report.fakturPpnMasukan(), numberStyle);
            createNumericCell(masukan, 2, report.ledgerPpnMasukan(), numberStyle);
            createNumericCell(masukan, 3, report.masukanDifference(), numberStyle);

            autoSizeColumns(sheet, 4);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating PPN Crosscheck Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== REKONSILIASI FISKAL ====================

    public byte[] exportRekonsiliasiFiskalToPdf(TaxReportDetailService.RekonsiliasiFiskalReport report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addReportHeader(document, "REKONSILIASI FISKAL", "Fiscal Reconciliation",
                    "Tahun Pajak " + report.year());

            // Section A: Commercial P&L
            Paragraph sectionA = new Paragraph("A. Laba Rugi Komersial", getBoldFont());
            sectionA.setSpacingBefore(15);
            document.add(sectionA);

            PdfPTable plTable = new PdfPTable(2);
            plTable.setWidthPercentage(100);
            plTable.setWidths(new float[]{60, 40});
            plTable.setSpacingBefore(5);
            addTableCell(plTable, "Pendapatan", Element.ALIGN_LEFT);
            addTableCell(plTable, formatNumber(report.incomeStatement().totalRevenue()), Element.ALIGN_RIGHT);
            addTableCell(plTable, "Beban", Element.ALIGN_LEFT);
            addTableCell(plTable, "(" + formatNumber(report.incomeStatement().totalExpense()) + ")", Element.ALIGN_RIGHT);
            addSubtotalRow(plTable, "Laba Bersih Komersial", formatNumber(report.commercialNetIncome()));
            document.add(plTable);

            // Section B: Fiscal Adjustments
            Paragraph sectionB = new Paragraph("B. Koreksi Fiskal", getBoldFont());
            sectionB.setSpacingBefore(15);
            document.add(sectionB);

            PdfPTable adjTable = new PdfPTable(4);
            adjTable.setWidthPercentage(100);
            adjTable.setWidths(new float[]{40, 20, 20, 20});
            adjTable.setSpacingBefore(5);
            addTableHeader(adjTable, "Uraian", "Kategori", "Arah", "Jumlah");

            for (FiscalAdjustment adj : report.adjustments()) {
                addTableCell(adjTable, adj.getDescription(), Element.ALIGN_LEFT);
                addTableCell(adjTable, adj.getAdjustmentCategory().getIndonesianName(), Element.ALIGN_LEFT);
                addTableCell(adjTable, adj.getAdjustmentDirection().getIndonesianName(), Element.ALIGN_LEFT);
                String amountStr = adj.getAdjustmentDirection() == FiscalAdjustmentDirection.NEGATIVE
                        ? "(" + formatNumber(adj.getAmount()) + ")" : formatNumber(adj.getAmount());
                addTableCell(adjTable, amountStr, Element.ALIGN_RIGHT);
            }
            document.add(adjTable);

            // Adjustment totals
            PdfPTable adjTotalTable = new PdfPTable(2);
            adjTotalTable.setWidthPercentage(100);
            adjTotalTable.setWidths(new float[]{60, 40});
            addSubtotalRow(adjTotalTable, "Koreksi Positif", formatNumber(report.totalPositiveAdjustment()));
            addSubtotalRow(adjTotalTable, "Koreksi Negatif", "(" + formatNumber(report.totalNegativeAdjustment()) + ")");
            addSubtotalRow(adjTotalTable, "Koreksi Fiskal Neto", formatNumber(report.netAdjustment()));
            document.add(adjTotalTable);

            // Section C: PKP & PPh Badan
            Paragraph sectionC = new Paragraph("C. Penghasilan Kena Pajak & PPh Badan", getBoldFont());
            sectionC.setSpacingBefore(15);
            document.add(sectionC);

            PdfPTable pphTable = new PdfPTable(2);
            pphTable.setWidthPercentage(100);
            pphTable.setWidths(new float[]{60, 40});
            pphTable.setSpacingBefore(5);

            addTableCell(pphTable, "Laba Bersih Komersial", Element.ALIGN_LEFT);
            addTableCell(pphTable, formatNumber(report.commercialNetIncome()), Element.ALIGN_RIGHT);
            addTableCell(pphTable, "Koreksi Fiskal Neto", Element.ALIGN_LEFT);
            addTableCell(pphTable, formatNumber(report.netAdjustment()), Element.ALIGN_RIGHT);
            addSubtotalRow(pphTable, "Penghasilan Kena Pajak (PKP)", formatNumber(report.pkp()));

            addTableCell(pphTable, "PPh Badan Terutang (" + report.pphBadan().calculationMethod() + ")", Element.ALIGN_LEFT);
            addTableCell(pphTable, formatNumber(report.pphBadan().pphTerutang()), Element.ALIGN_RIGHT);
            addTableCell(pphTable, "Kredit Pajak PPh 23", Element.ALIGN_LEFT);
            addTableCell(pphTable, "(" + formatNumber(report.pphBadan().kreditPajakPPh23()) + ")", Element.ALIGN_RIGHT);
            addTableCell(pphTable, "Kredit Pajak PPh 25", Element.ALIGN_LEFT);
            addTableCell(pphTable, "(" + formatNumber(report.pphBadan().kreditPajakPPh25()) + ")", Element.ALIGN_RIGHT);
            addSubtotalRow(pphTable, "PPh Pasal 29 (Kurang/Lebih Bayar)", formatNumber(report.pphBadan().pph29()));

            document.add(pphTable);
            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating Rekonsiliasi Fiskal PDF", e);
            throw new ReportGenerationException(PDF_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    public byte[] exportRekonsiliasiFiskalToExcel(TaxReportDetailService.RekonsiliasiFiskalReport report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Rekonsiliasi Fiskal");
            int rowNum = 0;
            rowNum = addExcelHeader(workbook, sheet, rowNum, "REKONSILIASI FISKAL",
                    "Tahun Pajak " + report.year(), 2);

            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            // Section A
            Row sA = sheet.createRow(rowNum++);
            createCell(sA, 0, "A. Laba Rugi Komersial", sectionStyle);

            Row rev = sheet.createRow(rowNum++);
            createCell(rev, 0, "Pendapatan", textStyle);
            createNumericCell(rev, 1, report.incomeStatement().totalRevenue(), numberStyle);

            Row exp = sheet.createRow(rowNum++);
            createCell(exp, 0, "Beban", textStyle);
            createNumericCell(exp, 1, report.incomeStatement().totalExpense().negate(), numberStyle);

            Row ni = sheet.createRow(rowNum++);
            createCell(ni, 0, "Laba Bersih Komersial", totalStyle);
            createNumericCell(ni, 1, report.commercialNetIncome(), totalStyle);
            rowNum++;

            // Section B
            Row sB = sheet.createRow(rowNum++);
            createCell(sB, 0, "B. Koreksi Fiskal", sectionStyle);

            for (FiscalAdjustment adj : report.adjustments()) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, adj.getDescription() + " (" + adj.getAdjustmentCategory().getIndonesianName() + ", " + adj.getAdjustmentDirection().getIndonesianName() + ")", textStyle);
                BigDecimal val = adj.getAdjustmentDirection() == FiscalAdjustmentDirection.NEGATIVE
                        ? adj.getAmount().negate() : adj.getAmount();
                createNumericCell(row, 1, val, numberStyle);
            }

            Row adjNet = sheet.createRow(rowNum++);
            createCell(adjNet, 0, "Koreksi Fiskal Neto", totalStyle);
            createNumericCell(adjNet, 1, report.netAdjustment(), totalStyle);
            rowNum++;

            // Section C
            Row sC = sheet.createRow(rowNum++);
            createCell(sC, 0, "C. PKP & PPh Badan", sectionStyle);

            Row pkpRow = sheet.createRow(rowNum++);
            createCell(pkpRow, 0, "Penghasilan Kena Pajak (PKP)", totalStyle);
            createNumericCell(pkpRow, 1, report.pkp(), totalStyle);

            Row pphRow = sheet.createRow(rowNum++);
            createCell(pphRow, 0, "PPh Badan Terutang", textStyle);
            createNumericCell(pphRow, 1, report.pphBadan().pphTerutang(), numberStyle);

            Row kp23 = sheet.createRow(rowNum++);
            createCell(kp23, 0, "Kredit Pajak PPh 23", textStyle);
            createNumericCell(kp23, 1, report.pphBadan().kreditPajakPPh23().negate(), numberStyle);

            Row kp25 = sheet.createRow(rowNum++);
            createCell(kp25, 0, "Kredit Pajak PPh 25", textStyle);
            createNumericCell(kp25, 1, report.pphBadan().kreditPajakPPh25().negate(), numberStyle);

            Row pph29 = sheet.createRow(rowNum);
            createCell(pph29, 0, "PPh Pasal 29 (Kurang/Lebih Bayar)", totalStyle);
            createNumericCell(pph29, 1, report.pphBadan().pph29(), totalStyle);

            autoSizeColumns(sheet, 2);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Rekonsiliasi Fiskal Excel", e);
            throw new ReportGenerationException(EXCEL_GENERATION_ERROR + e.getMessage(), e);
        }
    }

    // ==================== HELPER METHODS ====================

    private String formatNumber(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }
        return NUMBER_FORMAT.format(value);
    }

    private String formatCashFlowNumber(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return "(" + NUMBER_FORMAT.format(value.abs()) + ")";
        }
        return NUMBER_FORMAT.format(value);
    }

    // PDF Helpers

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

    private void addSectionHeader(PdfPTable table, String title) {
        PdfPCell cell = new PdfPCell(new Phrase(title, getBoldFont()));
        cell.setColspan(2);
        cell.setPadding(6);
        cell.setBackgroundColor(new Color(245, 245, 245));
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addSubtotalRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getBoldFont()));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(250, 250, 250));
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, getBoldFont()));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(new Color(250, 250, 250));
        valueCell.setBorderWidth(0.5f);
        table.addCell(valueCell);
    }

    private void addPpnTotalRow(PdfPTable table, String label, String dppTotal, String ppnTotal) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getBoldFont()));
        labelCell.setColspan(5);
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(240, 240, 240));
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell dppCell = new PdfPCell(new Phrase(dppTotal, getBoldFont()));
        dppCell.setPadding(5);
        dppCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        dppCell.setBackgroundColor(new Color(240, 240, 240));
        dppCell.setBorderWidth(0.5f);
        table.addCell(dppCell);

        PdfPCell ppnCell = new PdfPCell(new Phrase(ppnTotal, getBoldFont()));
        ppnCell.setPadding(5);
        ppnCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        ppnCell.setBackgroundColor(new Color(240, 240, 240));
        ppnCell.setBorderWidth(0.5f);
        table.addCell(ppnCell);
    }

    private void addPph23TotalRow(PdfPTable table, String grossTotal, String taxTotal) {
        PdfPCell labelCell = new PdfPCell(new Phrase(TOTAL_LABEL, getBoldFont()));
        labelCell.setColspan(4);
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(240, 240, 240));
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell grossCell = new PdfPCell(new Phrase(grossTotal, getBoldFont()));
        grossCell.setPadding(5);
        grossCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        grossCell.setBackgroundColor(new Color(240, 240, 240));
        grossCell.setBorderWidth(0.5f);
        table.addCell(grossCell);

        PdfPCell emptyCell = new PdfPCell(new Phrase("", getBoldFont()));
        emptyCell.setPadding(5);
        emptyCell.setBackgroundColor(new Color(240, 240, 240));
        emptyCell.setBorderWidth(0.5f);
        table.addCell(emptyCell);

        PdfPCell taxCell = new PdfPCell(new Phrase(taxTotal, getBoldFont()));
        taxCell.setPadding(5);
        taxCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        taxCell.setBackgroundColor(new Color(240, 240, 240));
        taxCell.setBorderWidth(0.5f);
        table.addCell(taxCell);
    }

    private void addTotalRow(PdfPTable table, String label, String value1, String value2) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, getBoldFont()));
        labelCell.setPadding(6);
        labelCell.setBackgroundColor(new Color(230, 230, 230));
        labelCell.setBorderWidth(1f);

        if (value2 == null) {
            labelCell.setColspan(1);
            table.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(value1, getBoldFont()));
            valueCell.setPadding(6);
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setBackgroundColor(new Color(230, 230, 230));
            valueCell.setBorderWidth(1f);
            table.addCell(valueCell);
        } else {
            table.addCell(labelCell);

            PdfPCell debitCell = new PdfPCell(new Phrase(value1, getBoldFont()));
            debitCell.setPadding(6);
            debitCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            debitCell.setBackgroundColor(new Color(230, 230, 230));
            debitCell.setBorderWidth(1f);
            table.addCell(debitCell);

            PdfPCell creditCell = new PdfPCell(new Phrase(value2, getBoldFont()));
            creditCell.setPadding(6);
            creditCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            creditCell.setBackgroundColor(new Color(230, 230, 230));
            creditCell.setBorderWidth(1f);
            table.addCell(creditCell);
        }
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

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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
        style.setDataFormat(format.getFormat(EXCEL_NUMBER_FORMAT));
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
        style.setDataFormat(format.getFormat(EXCEL_NUMBER_FORMAT));
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(DATE_PATTERN_DMY));
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
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
