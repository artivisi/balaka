package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.FiscalLossCarryforward;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.FiscalLossCarryforwardRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.service.PayrollService.YearlyPayrollSummary;
import com.artivisi.accountingfinance.service.ReportService.BalanceSheetItem;
import com.artivisi.accountingfinance.service.ReportService.BalanceSheetReport;
import com.artivisi.accountingfinance.service.ReportService.IncomeStatementItem;
import com.artivisi.accountingfinance.service.ReportService.IncomeStatementReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService.PPhBadanCalculation;
import com.artivisi.accountingfinance.service.TaxReportDetailService.PPh23DetailReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService.RekonsiliasiFiskalReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating SPT Tahunan PPh Badan lampiran data.
 * Produces structured data matching Coretax form layouts.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SptTahunanExportService {

    private final TaxReportDetailService taxReportDetailService;
    private final ReportService reportService;
    private final DepreciationReportService depreciationReportService;
    private final TaxTransactionDetailRepository taxTransactionDetailRepository;
    private final PayrollService payrollService;
    private final Pph21CalculationService pph21CalculationService;
    private final FiscalLossCarryforwardRepository fiscalLossCarryforwardRepository;
    private final CompanyConfigRepository companyConfigRepository;

    // Transkrip 8A slot identifiers
    private static final String SLOT_8A_I_1 = "8A.I.1";
    private static final String SLOT_8A_I_2 = "8A.I.2";
    private static final String SLOT_8A_I_3 = "8A.I.3";
    private static final String SLOT_8A_I_4 = "8A.I.4";
    private static final String SLOT_8A_I_5 = "8A.I.5";
    private static final String SLOT_8A_I_6 = "8A.I.6";
    private static final String SLOT_8A_I_7 = "8A.I.7";
    private static final String SLOT_8A_I_8 = "8A.I.8";
    private static final String SLOT_8A_I_9 = "8A.I.9";
    private static final String SLOT_8A_I_10 = "8A.I.10";
    private static final String SLOT_8A_II_1 = "8A.II.1";
    private static final String SLOT_8A_II_2 = "8A.II.2";
    private static final String SLOT_8A_II_3 = "8A.II.3";
    private static final String SLOT_8A_II_4 = "8A.II.4";
    private static final String SLOT_8A_II_5 = "8A.II.5";
    private static final String SLOT_8A_II_6 = "8A.II.6";
    private static final String SLOT_8A_II_7 = "8A.II.7";

    // Account code prefixes for categorization
    private static final String OPERATING_REVENUE_PREFIX = "4.1";
    private static final String OTHER_INCOME_PREFIX = "4.2";
    private static final String OPERATING_EXPENSE_PREFIX = "5.1";
    // OTHER_EXPENSE_PREFIX and TAX_EXPENSE_PREFIX removed — otherExpenses now captures
    // all non-operating expenses (5.2, 5.3, 5.9, etc.) via exclusion filter

    // ==================== L1: REKONSILIASI FISKAL ====================

    /**
     * Generate L1 (Rekonsiliasi Fiskal) in Coretax structure.
     * Maps the app's income statement and fiscal adjustments to the
     * standard L1 form sections (I-III).
     */
    public L1Report generateL1(int year) {
        RekonsiliasiFiskalReport rekon = taxReportDetailService.generateRekonsiliasiFiskal(year);
        IncomeStatementReport incomeStatement = rekon.incomeStatement();

        // Categorize revenue items
        List<L1LineItem> operatingRevenue = categorizeItems(incomeStatement.revenueItems(), OPERATING_REVENUE_PREFIX);
        List<L1LineItem> otherIncome = categorizeItems(incomeStatement.revenueItems(), OTHER_INCOME_PREFIX);

        BigDecimal totalOperatingRevenue = sumLineItems(operatingRevenue);
        BigDecimal totalOtherIncome = sumLineItems(otherIncome);

        // Categorize expense items: operating (5.1) vs everything else (5.2, 5.3, 5.9, etc.)
        List<L1LineItem> operatingExpenses = categorizeItems(incomeStatement.expenseItems(), OPERATING_EXPENSE_PREFIX);
        List<L1LineItem> otherExpenses = categorizeItemsExcludingPrefix(incomeStatement.expenseItems(), OPERATING_EXPENSE_PREFIX);

        BigDecimal totalOperatingExpenses = sumLineItems(operatingExpenses);
        BigDecimal totalOtherExpenses = sumLineItems(otherExpenses);

        // Gross profit (for service companies, COGS = 0)
        BigDecimal grossProfit = totalOperatingRevenue;

        // Net operating income
        BigDecimal netOperatingIncome = grossProfit.subtract(totalOperatingExpenses);

        // Net other income
        BigDecimal netOtherIncome = totalOtherIncome.subtract(totalOtherExpenses);

        // Commercial net income
        BigDecimal commercialNetIncome = netOperatingIncome.add(netOtherIncome);

        // Fiscal adjustments grouped by direction
        List<L1AdjustmentItem> positiveAdjustments = rekon.adjustments().stream()
                .filter(a -> a.getAdjustmentDirection() == FiscalAdjustmentDirection.POSITIVE)
                .map(this::toAdjustmentItem)
                .toList();

        List<L1AdjustmentItem> negativeAdjustments = rekon.adjustments().stream()
                .filter(a -> a.getAdjustmentDirection() == FiscalAdjustmentDirection.NEGATIVE)
                .map(this::toAdjustmentItem)
                .toList();

        List<L1LossItem> lossItems = rekon.activeLosses().stream()
                .map(loss -> new L1LossItem(
                        loss.getOriginYear(), loss.getRemainingAmount(), loss.getExpiryYear()))
                .toList();

        return new L1Report(
                year,
                // Section I: Penghasilan Neto Fiskal
                operatingRevenue, totalOperatingRevenue,
                BigDecimal.ZERO, // COGS (service company)
                grossProfit,
                operatingExpenses, totalOperatingExpenses,
                netOperatingIncome,
                otherIncome, totalOtherIncome,
                otherExpenses, totalOtherExpenses,
                netOtherIncome,
                commercialNetIncome,
                positiveAdjustments, rekon.totalPositiveAdjustment(),
                negativeAdjustments, rekon.totalNegativeAdjustment(),
                rekon.pkp(),
                // Loss carryforward
                lossItems, rekon.lossCompensation(),
                rekon.pkpAfterLoss(),
                // Section II: PPh Terutang
                rekon.pphBadan(),
                // Section III: Kredit Pajak
                rekon.pphBadan().kreditPajakPPh23(),
                rekon.pphBadan().kreditPajakPPh25(),
                rekon.pphBadan().totalKreditPajak(),
                rekon.pphBadan().pph29()
        );
    }

    // ==================== L4: PENGHASILAN FINAL ====================

    /**
     * Generate L4 (Penghasilan Final) — PPh 4(2) summary by tax object code.
     */
    public L4Report generateL4(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        List<TaxTransactionDetail> pph42Details = taxTransactionDetailRepository
                .findByTaxTypeAndDateRange(TaxType.PPH_42, startDate, endDate);

        // Group by tax object code and aggregate
        Map<String, List<TaxTransactionDetail>> byObjectCode = pph42Details.stream()
                .collect(Collectors.groupingBy(d -> d.getTaxObjectCode() != null ? d.getTaxObjectCode() : "UNKNOWN"));

        List<L4LineItem> items = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (Map.Entry<String, List<TaxTransactionDetail>> entry : byObjectCode.entrySet()) {
            String objectCode = entry.getKey();
            List<TaxTransactionDetail> details = entry.getValue();

            BigDecimal gross = details.stream()
                    .map(TaxTransactionDetail::getGrossAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal tax = details.stream()
                    .map(TaxTransactionDetail::getTaxAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal rate = details.getFirst().getTaxRate();

            items.add(new L4LineItem(objectCode, describeObjectCode(objectCode), gross, rate, tax));
            totalGross = totalGross.add(gross);
            totalTax = totalTax.add(tax);
        }

        // Sort by object code
        items.sort((a, b) -> a.taxObjectCode().compareTo(b.taxObjectCode()));

        return new L4Report(year, items, totalGross, totalTax);
    }

    // ==================== TRANSKRIP 8A: LAPORAN KEUANGAN ====================

    /**
     * Generate Transkrip 8A (Laporan Keuangan) — structured balance sheet
     * and income statement matching Coretax 8A-Jasa layout.
     */
    public Transkrip8AReport generateTranskrip8A(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // Exclude closing entries from P&L for tax purposes (BUG-014)
        IncomeStatementReport incomeStatement = reportService.generateIncomeStatementExcludingClosing(startDate, endDate);
        BalanceSheetReport balanceSheet = reportService.generateBalanceSheet(endDate);

        // Map income statement items to transcript lines
        List<Transkrip8ALineItem> revenueLines = incomeStatement.revenueItems().stream()
                .map(item -> new Transkrip8ALineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();

        List<Transkrip8ALineItem> expenseLines = incomeStatement.expenseItems().stream()
                .map(item -> new Transkrip8ALineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();

        // Map balance sheet items
        List<Transkrip8ALineItem> assetLines = balanceSheet.assetItems().stream()
                .map(item -> new Transkrip8ALineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();

        List<Transkrip8ALineItem> liabilityLines = balanceSheet.liabilityItems().stream()
                .map(item -> new Transkrip8ALineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();

        List<Transkrip8ALineItem> equityLines = balanceSheet.equityItems().stream()
                .map(item -> new Transkrip8ALineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();

        return new Transkrip8AReport(
                year,
                // Neraca (Balance Sheet)
                assetLines, balanceSheet.totalAssets(),
                liabilityLines, balanceSheet.totalLiabilities(),
                equityLines, balanceSheet.totalEquity(),
                balanceSheet.currentYearEarnings(),
                // Laba Rugi (Income Statement)
                revenueLines, incomeStatement.totalRevenue(),
                expenseLines, incomeStatement.totalExpense(),
                incomeStatement.netIncome()
        );
    }

    // ==================== L9: PENYUSUTAN & AMORTISASI ====================

    /**
     * Generate L9 (Penyusutan & Amortisasi) data matching DJP converter template.
     */
    public L9Report generateL9(int year) {
        DepreciationReportService.DepreciationReport depReport =
                depreciationReportService.generateReport(year);

        List<L9LineItem> items = depReport.items().stream()
                .map(item -> new L9LineItem(
                        item.assetName(),
                        mapToFiscalGroup(item.categoryName(), item.usefulLifeYears()),
                        item.purchaseDate(),
                        item.purchaseCost(),
                        mapDepreciationMethod(item.depreciationMethod()),
                        item.usefulLifeYears(),
                        item.depreciationThisYear(),
                        item.accumulatedDepreciation(),
                        item.bookValue()))
                .toList();

        return new L9Report(
                year, items,
                depReport.totalPurchaseCost(),
                depReport.totalDepreciationThisYear(),
                depReport.totalAccumulatedDepreciation(),
                depReport.totalBookValue());
    }

    // ==================== BPA1: e-BUPOT PPh 21 ANNUAL ====================

    /**
     * Generate bulk 1721-A1 data for all employees with payroll in the given year.
     * Matches DJP BPA1 converter template for Coretax XML import.
     */
    public Bpa1Report generateBpa1(int year) {
        List<UUID> employeeIds = payrollService.getEmployeesWithPayrollInYear(year);

        List<Bpa1LineItem> items = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalPph21Terutang = BigDecimal.ZERO;
        BigDecimal totalPph21Dipotong = BigDecimal.ZERO;

        for (UUID employeeId : employeeIds) {
            YearlyPayrollSummary summary = payrollService.getYearlyPayrollSummary(employeeId, year);
            Employee employee = summary.employee();

            BigDecimal penghasilanBruto = summary.totalGross();
            BigDecimal biayaJabatan = penghasilanBruto.multiply(new BigDecimal("5"))
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                    .min(Pph21CalculationService.BIAYA_JABATAN_ANNUAL_MAX);
            BigDecimal bpjsDeduction = summary.totalBpjsEmployee();
            BigDecimal penghasilanNeto = penghasilanBruto.subtract(biayaJabatan).subtract(bpjsDeduction);
            BigDecimal ptkp = employee.getPtkpStatus().getAnnualAmount();
            BigDecimal pkpRaw = penghasilanNeto.subtract(ptkp).max(BigDecimal.ZERO);
            BigDecimal pkp = pkpRaw.divide(new BigDecimal("1000"), 0, RoundingMode.FLOOR)
                    .multiply(new BigDecimal("1000"));
            BigDecimal pph21Terutang = pph21CalculationService.calculateProgressiveTax(pkp);
            BigDecimal pph21Dipotong = summary.totalPph21();

            items.add(new Bpa1LineItem(
                    employee.getNpwp(),
                    employee.getNikKtp(),
                    employee.getName(),
                    employee.getPtkpStatus(),
                    summary.monthCount(),
                    penghasilanBruto,
                    biayaJabatan,
                    bpjsDeduction,
                    penghasilanNeto,
                    ptkp,
                    pkp,
                    pph21Terutang,
                    pph21Dipotong,
                    pph21Terutang.subtract(pph21Dipotong)
            ));

            totalGross = totalGross.add(penghasilanBruto);
            totalPph21Terutang = totalPph21Terutang.add(pph21Terutang);
            totalPph21Dipotong = totalPph21Dipotong.add(pph21Dipotong);
        }

        return new Bpa1Report(year, items, totalGross, totalPph21Terutang, totalPph21Dipotong);
    }

    // ==================== L7: FISCAL LOSS CARRYFORWARD ====================

    /**
     * Get active fiscal losses that can be deducted from PKP for the given year.
     * Per UU PPh Pasal 6 ayat 2, losses can be carried forward for 5 years.
     */
    public LossCarryforwardReport generateLossCarryforward(int year) {
        List<FiscalLossCarryforward> allLosses = fiscalLossCarryforwardRepository.findAllByOrderByOriginYearDesc();
        List<FiscalLossCarryforward> activeLosses = fiscalLossCarryforwardRepository.findActiveLossesForYear(year);

        BigDecimal totalRemaining = activeLosses.stream()
                .map(FiscalLossCarryforward::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LossCarryforwardItem> items = allLosses.stream()
                .map(loss -> new LossCarryforwardItem(
                        loss.getId(),
                        loss.getOriginYear(),
                        loss.getOriginalAmount(),
                        loss.getUsedAmount(),
                        loss.getRemainingAmount(),
                        loss.getExpiryYear(),
                        loss.isExpired(year),
                        loss.getNotes()))
                .toList();

        return new LossCarryforwardReport(year, items, totalRemaining);
    }

    // ==================== LOSS CARRYFORWARD CRUD ====================

    @Transactional
    public FiscalLossCarryforward saveLossCarryforward(FiscalLossCarryforward loss) {
        return fiscalLossCarryforwardRepository.save(loss);
    }

    @Transactional
    public void deleteLossCarryforward(UUID id) {
        fiscalLossCarryforwardRepository.deleteById(id);
    }

    public List<FiscalLossCarryforward> findAllLossCarryforwards() {
        return fiscalLossCarryforwardRepository.findAllByOrderByOriginYearDesc();
    }

    public FiscalLossCarryforward findLossCarryforwardById(UUID id) {
        return fiscalLossCarryforwardRepository.findById(id).orElse(null);
    }

    // ==================== CONSOLIDATED LAMPIRAN ====================

    /**
     * Generate consolidated SPT Tahunan Badan lampiran data mapped to Coretax field numbers.
     * Single endpoint returning all lampiran sections ready for direct input into Coretax.
     */
    public SptLampiranReport generateConsolidatedLampiran(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // Taxpayer info
        CompanyConfig config = companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Company config not found"));
        SptTaxpayer taxpayer = new SptTaxpayer(
                config.getNpwp(), config.getNitku(), config.getCompanyName());

        // Transkrip 8A — balance sheet + P&L with Coretax field mapping
        SptTranskrip8A transkrip8A = buildTranskrip8A(year, startDate, endDate);

        // Lampiran I — fiscal reconciliation (reuses existing L1)
        L1Report l1 = generateL1(year);
        SptLampiranI lampiranI = buildLampiranI(l1);

        // Lampiran II — expense breakdown by category
        IncomeStatementReport incomeStatement =
                reportService.generateIncomeStatementExcludingClosing(startDate, endDate);
        SptLampiranII lampiranII = buildLampiranII(incomeStatement);

        // Lampiran III — kredit pajak PPh 23
        PPh23DetailReport pph23 = taxReportDetailService.generatePPh23DetailReport(startDate, endDate);
        SptLampiranIII lampiranIII = buildLampiranIII(pph23);

        // Lampiran V — placeholder (shareholder data not in accounting system)
        SptLampiranV lampiranV = new SptLampiranV(
                "Daftar Pemegang Saham / Pengurus",
                "Manual entry — requires shareholder registry data not in accounting system");

        // PPh Badan summary
        PPhBadanCalculation pphCalc = l1.pphBadan();
        SptPPhBadan pphBadan = new SptPPhBadan(
                l1.pkp(), pphCalc.pphTerutang(),
                pphCalc.totalKreditPajak(), pphCalc.pph29());

        return new SptLampiranReport(year, taxpayer, transkrip8A, lampiranI, lampiranII, lampiranIII, lampiranV, pphBadan);
    }

    // ==================== CORETAX-COMPATIBLE SPT EXPORT ====================

    /**
     * Generate Coretax-compatible SPT Badan export.
     * Aggregates all existing export data into a flat structure matching Coretax form fields.
     * Values are plain numbers (no formatting) for direct entry into Coretax.
     */
    public CoretaxSptBadanExport generateCoretaxExport(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // Company info
        CompanyConfig config = companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Company config not found"));

        // L1 data (includes PPh Badan calculation)
        L1Report l1 = generateL1(year);
        PPhBadanCalculation pph = l1.pphBadan();

        // Build Induk section (SPT main form fields)
        CoretaxInduk induk = new CoretaxInduk(
                config.getNpwp(),
                config.getNitku(),
                config.getCompanyName(),
                year,
                l1.totalOperatingRevenue(),
                l1.grossProfit(),
                l1.totalOperatingExpenses(),
                l1.netOperatingIncome(),
                l1.totalOtherIncome(),
                l1.totalOtherExpenses(),
                l1.netOtherIncome(),
                l1.commercialNetIncome(),
                l1.totalPositiveAdjustment(),
                l1.totalNegativeAdjustment(),
                l1.pkpBeforeLoss(),
                l1.totalLossCompensation(),
                l1.pkp(),
                pph.pkpRounded(),
                pph.pphTerutang(),
                pph.calculationMethod(),
                pph.kreditPajakPPh23(),
                pph.kreditPajakPPh25(),
                pph.totalKreditPajak(),
                pph.pph29()
        );

        // L1-D Laba Rugi items (revenue + expense lines with account codes)
        IncomeStatementReport incomeStatement = reportService.generateIncomeStatementExcludingClosing(startDate, endDate);
        List<CoretaxL1DItem> l1dLabaRugi = new ArrayList<>();
        for (IncomeStatementItem item : incomeStatement.revenueItems()) {
            l1dLabaRugi.add(new CoretaxL1DItem(
                    item.account().getAccountCode(),
                    item.account().getAccountName(),
                    "PENDAPATAN",
                    item.balance()));
        }
        for (IncomeStatementItem item : incomeStatement.expenseItems()) {
            l1dLabaRugi.add(new CoretaxL1DItem(
                    item.account().getAccountCode(),
                    item.account().getAccountName(),
                    "BEBAN",
                    item.balance()));
        }

        // L1-D Neraca items
        BalanceSheetReport balanceSheet = reportService.generateBalanceSheet(endDate);
        CoretaxL1DNeraca l1dNeraca = buildCoretaxNeraca(balanceSheet);

        // L3 Kredit Pajak (individual bupot entries)
        PPh23DetailReport pph23 = taxReportDetailService.generatePPh23DetailReport(startDate, endDate);
        List<CoretaxL3Item> l3KreditPajak = pph23.items().stream()
                .map(d -> new CoretaxL3Item(
                        d.getCounterpartyName(),
                        d.getCounterpartyNpwp(),
                        d.getBupotNumber(),
                        d.getTransaction().getTransactionDate(),
                        d.getGrossAmount(),
                        d.getTaxRate(),
                        d.getTaxAmount()))
                .toList();

        // Penyusutan items
        L9Report l9 = generateL9(year);
        List<CoretaxPenyusutanItem> penyusutan = l9.items().stream()
                .map(item -> new CoretaxPenyusutanItem(
                        item.assetName(),
                        item.fiscalGroup(),
                        item.acquisitionDate(),
                        item.acquisitionCost(),
                        item.depreciationMethod(),
                        item.usefulLifeYears(),
                        item.depreciationThisYear(),
                        item.accumulatedDepreciation(),
                        item.bookValue()))
                .toList();

        return new CoretaxSptBadanExport(year, induk, l1dLabaRugi, l1dNeraca, l3KreditPajak, penyusutan);
    }

    private CoretaxL1DNeraca buildCoretaxNeraca(BalanceSheetReport bs) {
        List<CoretaxNeracaItem> aktiva = bs.assetItems().stream()
                .map(item -> new CoretaxNeracaItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();

        List<CoretaxNeracaItem> pasiva = new ArrayList<>();
        for (BalanceSheetItem item : bs.liabilityItems()) {
            pasiva.add(new CoretaxNeracaItem(
                    item.account().getAccountCode(),
                    item.account().getAccountName(),
                    item.balance()));
        }
        for (BalanceSheetItem item : bs.equityItems()) {
            pasiva.add(new CoretaxNeracaItem(
                    item.account().getAccountCode(),
                    item.account().getAccountName(),
                    item.balance()));
        }

        return new CoretaxL1DNeraca(
                aktiva, bs.totalAssets(),
                pasiva, bs.totalLiabilities().add(bs.totalEquity()),
                bs.currentYearEarnings());
    }

    private SptTranskrip8A buildTranskrip8A(int year, LocalDate startDate, LocalDate endDate) {
        BalanceSheetReport bs = reportService.generateBalanceSheet(LocalDate.of(year, 12, 31));
        IncomeStatementReport is = reportService.generateIncomeStatementExcludingClosing(startDate, endDate);

        // Group balance sheet items into Coretax 8A fields
        List<SptFieldItem> neracaAktiva = mapAssetItems(bs);
        BigDecimal totalAktiva = neracaAktiva.stream().map(SptFieldItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SptFieldItem> neracaPasiva = mapLiabilityEquityItems(bs);
        BigDecimal totalPasiva = neracaPasiva.stream().map(SptFieldItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // P&L summary
        BigDecimal pendapatanUsaha = sumByPrefix(is.revenueItems(), "4.1");
        BigDecimal pendapatanLainnya = sumByPrefix(is.revenueItems(), "4.2");
        BigDecimal bebanUsaha = sumByPrefix(is.expenseItems(), "5.1");
        BigDecimal bebanLainnya = is.totalExpense().subtract(bebanUsaha);

        SptLabaRugi labaRugi = new SptLabaRugi(
                pendapatanUsaha, BigDecimal.ZERO, pendapatanUsaha,
                bebanUsaha, pendapatanUsaha.subtract(bebanUsaha),
                pendapatanLainnya, bebanLainnya,
                is.netIncome(), BigDecimal.ZERO, is.netIncome());

        return new SptTranskrip8A(
                "Transkrip Kutipan Elemen Laporan Keuangan (8A-Jasa)",
                neracaAktiva, totalAktiva, neracaPasiva, totalPasiva, labaRugi);
    }

    private List<SptFieldItem> mapAssetItems(BalanceSheetReport bs) {
        // Group asset accounts into Coretax 8A.I fields
        Map<String, SptFieldItem> groups = new LinkedHashMap<>();
        groups.put(SLOT_8A_I_1, new SptFieldItem(SLOT_8A_I_1, "Kas dan Setara Kas", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_2, new SptFieldItem(SLOT_8A_I_2, "Piutang Usaha", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_3, new SptFieldItem(SLOT_8A_I_3, "Piutang Lain-lain", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_4, new SptFieldItem(SLOT_8A_I_4, "Persediaan", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_5, new SptFieldItem(SLOT_8A_I_5, "Biaya Dibayar Dimuka", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_6, new SptFieldItem(SLOT_8A_I_6, "Investasi Jangka Pendek", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_7, new SptFieldItem(SLOT_8A_I_7, "Aset Pajak Tangguhan", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_8, new SptFieldItem(SLOT_8A_I_8, "Aset Tetap", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_9, new SptFieldItem(SLOT_8A_I_9, "Akumulasi Penyusutan", BigDecimal.ZERO));
        groups.put(SLOT_8A_I_10, new SptFieldItem(SLOT_8A_I_10, "Aset Lainnya", BigDecimal.ZERO));

        for (BalanceSheetItem item : bs.assetItems()) {
            String code = item.account().getAccountCode();
            BigDecimal amount = item.account().getNormalBalance() == NormalBalance.CREDIT
                    ? item.balance().negate() : item.balance();
            String field = mapAssetCodeToField(code, item.account().getNormalBalance());
            groups.computeIfPresent(field, (k, v) -> new SptFieldItem(k, v.label(), v.amount().add(amount)));
        }

        return new ArrayList<>(groups.values());
    }

    private String mapAssetCodeToField(String code, NormalBalance normalBalance) {
        // Cash & equivalents: 1.1.01-1.1.09
        if (code.startsWith("1.1.0")) return SLOT_8A_I_1;
        // Trade receivables: 1.1.10
        if (code.equals("1.1.10")) return SLOT_8A_I_2;
        // Other receivables: 1.1.11-1.1.14
        if (code.startsWith("1.1.1") && !code.equals("1.1.10") && !code.equals("1.1.15")) return SLOT_8A_I_3;
        // Prepaid expenses: 1.1.15-1.1.19
        if (code.equals("1.1.15") || code.startsWith("1.1.16") || code.startsWith("1.1.17")
                || code.startsWith("1.1.18") || code.startsWith("1.1.19")) return SLOT_8A_I_5;
        // Short-term investments: 1.1.21 (Logam Mulia), 1.1.4x
        if (code.equals("1.1.21") || code.startsWith("1.1.4")) return SLOT_8A_I_6;
        // Tax assets: 1.1.25+ (PPN Masukan, Kredit Pajak PPh 23, PPN Dipungut Pemungut, etc.)
        if (code.startsWith("1.1.2")) return SLOT_8A_I_7;
        // Inventory: 1.1.3x
        if (code.startsWith("1.1.3")) return SLOT_8A_I_4;
        // Fixed assets (contra-assets with CREDIT normal balance = accumulated depreciation)
        if ((code.startsWith("1.2") || code.startsWith("1.3")) && normalBalance == NormalBalance.CREDIT) return SLOT_8A_I_9;
        if (code.startsWith("1.2") || code.startsWith("1.3")) return SLOT_8A_I_8;
        return SLOT_8A_I_10;
    }

    private List<SptFieldItem> mapLiabilityEquityItems(BalanceSheetReport bs) {
        Map<String, SptFieldItem> groups = new LinkedHashMap<>();
        groups.put(SLOT_8A_II_1, new SptFieldItem(SLOT_8A_II_1, "Hutang Usaha", BigDecimal.ZERO));
        groups.put(SLOT_8A_II_2, new SptFieldItem(SLOT_8A_II_2, "Hutang Pajak", BigDecimal.ZERO));
        groups.put(SLOT_8A_II_3, new SptFieldItem(SLOT_8A_II_3, "Hutang Lain-lain", BigDecimal.ZERO));
        groups.put(SLOT_8A_II_4, new SptFieldItem(SLOT_8A_II_4, "Hutang Jangka Panjang", BigDecimal.ZERO));
        groups.put(SLOT_8A_II_5, new SptFieldItem(SLOT_8A_II_5, "Modal Disetor", BigDecimal.ZERO));
        groups.put(SLOT_8A_II_6, new SptFieldItem(SLOT_8A_II_6, "Laba Ditahan", BigDecimal.ZERO));
        groups.put(SLOT_8A_II_7, new SptFieldItem(SLOT_8A_II_7, "Laba Tahun Berjalan", BigDecimal.ZERO));

        for (BalanceSheetItem item : bs.liabilityItems()) {
            String code = item.account().getAccountCode();
            String field = mapLiabilityCodeToField(code);
            groups.computeIfPresent(field, (k, v) -> new SptFieldItem(k, v.label(), v.amount().add(item.balance())));
        }

        for (BalanceSheetItem item : bs.equityItems()) {
            String code = item.account().getAccountCode();
            String field = mapEquityCodeToField(code);
            groups.computeIfPresent(field, (k, v) -> new SptFieldItem(k, v.label(), v.amount().add(item.balance())));
        }

        // Current year earnings from balance sheet
        groups.computeIfPresent(SLOT_8A_II_7, (k, v) ->
                new SptFieldItem(k, v.label(), v.amount().add(bs.currentYearEarnings())));

        return new ArrayList<>(groups.values());
    }

    private String mapLiabilityCodeToField(String code) {
        if (code.equals("2.1.01")) return SLOT_8A_II_1;
        // Tax payables: 2.1.02, 2.1.03, 2.1.20-2.1.24
        if (code.equals("2.1.02") || code.equals("2.1.03") || code.startsWith("2.1.2")) return SLOT_8A_II_2;
        // Long-term liabilities: 2.2.x
        if (code.startsWith("2.2")) return SLOT_8A_II_4;
        // Other current liabilities
        return SLOT_8A_II_3;
    }

    private String mapEquityCodeToField(String code) {
        if (code.startsWith("3.1")) return SLOT_8A_II_5;
        if (code.equals("3.2.02")) return SLOT_8A_II_7;
        return SLOT_8A_II_6;
    }

    private SptLampiranI buildLampiranI(L1Report l1) {
        List<SptAdjustmentItem> koreksiPositif = l1.positiveAdjustments().stream()
                .map(a -> new SptAdjustmentItem(a.description(), a.pasal(), a.amount()))
                .toList();
        List<SptAdjustmentItem> koreksiNegatif = l1.negativeAdjustments().stream()
                .map(a -> new SptAdjustmentItem(a.description(), a.pasal(), a.amount()))
                .toList();
        List<SptLossItem> kompensasi = l1.lossCarryforwards().stream()
                .map(loss -> new SptLossItem(loss.originYear(), loss.remainingAmount(), loss.expiryYear()))
                .toList();

        return new SptLampiranI(
                "Perhitungan Penghasilan Neto Fiskal",
                l1.commercialNetIncome(),
                koreksiPositif, l1.totalPositiveAdjustment(),
                koreksiNegatif, l1.totalNegativeAdjustment(),
                l1.pkpBeforeLoss(),
                kompensasi, l1.totalLossCompensation(),
                l1.pkp());
    }

    private SptLampiranII buildLampiranII(IncomeStatementReport is) {
        List<SptExpenseItem> bebanUsaha = is.expenseItems().stream()
                .filter(item -> item.account().getAccountCode().startsWith("5.1"))
                .map(item -> new SptExpenseItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();
        BigDecimal totalBebanUsaha = bebanUsaha.stream()
                .map(SptExpenseItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SptExpenseItem> bebanLuarUsaha = is.expenseItems().stream()
                .filter(item -> !item.account().getAccountCode().startsWith("5.1"))
                .map(item -> new SptExpenseItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();
        BigDecimal totalBebanLuarUsaha = bebanLuarUsaha.stream()
                .map(SptExpenseItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SptLampiranII(
                "Perincian HPP, Biaya Usaha, dan Biaya Luar Usaha",
                bebanUsaha, totalBebanUsaha,
                bebanLuarUsaha, totalBebanLuarUsaha);
    }

    private SptLampiranIII buildLampiranIII(PPh23DetailReport pph23) {
        List<SptKreditPajakItem> items = pph23.items().stream()
                .map(d -> new SptKreditPajakItem(
                        d.getCounterpartyName(),
                        d.getCounterpartyNpwp(),
                        d.getBupotNumber(),
                        d.getTransaction().getTransactionDate(),
                        d.getGrossAmount(),
                        d.getTaxAmount()))
                .toList();

        return new SptLampiranIII(
                "Kredit Pajak Dalam Negeri (PPh 22/23/24)",
                items, pph23.totalTax());
    }

    private BigDecimal sumByPrefix(List<IncomeStatementItem> items, String prefix) {
        return items.stream()
                .filter(item -> item.account().getAccountCode().startsWith(prefix))
                .map(IncomeStatementItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== HELPERS ====================

    private List<L1LineItem> categorizeItems(List<IncomeStatementItem> items, String prefix) {
        return items.stream()
                .filter(item -> item.account().getAccountCode().startsWith(prefix))
                .filter(item -> item.balance().compareTo(BigDecimal.ZERO) != 0)
                .map(item -> new L1LineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();
    }

    private List<L1LineItem> categorizeItemsExcludingPrefix(List<IncomeStatementItem> items, String excludePrefix) {
        return items.stream()
                .filter(item -> !item.account().getAccountCode().startsWith(excludePrefix))
                .filter(item -> item.balance().compareTo(BigDecimal.ZERO) != 0)
                .map(item -> new L1LineItem(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance()))
                .toList();
    }

    private BigDecimal sumLineItems(List<L1LineItem> items) {
        return items.stream()
                .map(L1LineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private L1AdjustmentItem toAdjustmentItem(FiscalAdjustment adj) {
        return new L1AdjustmentItem(
                adj.getDescription(),
                adj.getAdjustmentCategory().name(),
                adj.getAdjustmentDirection().name(),
                adj.getAmount(),
                adj.getAccountCode(),
                adj.getPasal());
    }

    private String describeObjectCode(String code) {
        return switch (code) {
            case "28-409-01" -> "Sewa Tanah dan/atau Bangunan";
            case "28-409-07" -> "Jasa Konstruksi - Pelaksana Kecil";
            case "28-409-08" -> "Jasa Konstruksi - Pelaksana Menengah/Besar";
            case "28-423-01" -> "PPh Final UMKM (PP 55)";
            default -> "PPh Final - " + code;
        };
    }

    /**
     * Map asset category and useful life to fiscal asset group (Kelompok I-IV or Bangunan).
     * Per UU PPh Pasal 11, fiscal groups determine max useful life for tax depreciation.
     */
    private String mapToFiscalGroup(String categoryName, int usefulLifeYears) {
        // Map based on useful life (fiscal rules):
        // Kelompok I: 4 years, Kelompok II: 8 years, Kelompok III: 16 years, Kelompok IV: 20 years
        // Bangunan Permanen: 20 years, Bangunan Non-Permanen: 10 years
        if (categoryName != null && categoryName.toLowerCase().contains("bangunan")) {
            return usefulLifeYears <= 10 ? "Bangunan Non-Permanen" : "Bangunan Permanen";
        }
        if (usefulLifeYears <= 4) return "Kelompok I";
        if (usefulLifeYears <= 8) return "Kelompok II";
        if (usefulLifeYears <= 16) return "Kelompok III";
        return "Kelompok IV";
    }

    private String mapDepreciationMethod(String method) {
        if (method == null) return "Garis Lurus";
        return switch (method) {
            case "Saldo Menurun" -> "Saldo Menurun";
            default -> "Garis Lurus";
        };
    }

    // ==================== DTOs ====================

    public record L1Report(
            int year,
            // Section I: Penghasilan Neto Fiskal
            List<L1LineItem> operatingRevenue,
            BigDecimal totalOperatingRevenue,
            BigDecimal cogs,
            BigDecimal grossProfit,
            List<L1LineItem> operatingExpenses,
            BigDecimal totalOperatingExpenses,
            BigDecimal netOperatingIncome,
            List<L1LineItem> otherIncome,
            BigDecimal totalOtherIncome,
            List<L1LineItem> otherExpenses,
            BigDecimal totalOtherExpenses,
            BigDecimal netOtherIncome,
            BigDecimal commercialNetIncome,
            List<L1AdjustmentItem> positiveAdjustments,
            BigDecimal totalPositiveAdjustment,
            List<L1AdjustmentItem> negativeAdjustments,
            BigDecimal totalNegativeAdjustment,
            BigDecimal pkpBeforeLoss,
            // Loss carryforward (kompensasi kerugian)
            List<L1LossItem> lossCarryforwards,
            BigDecimal totalLossCompensation,
            BigDecimal pkp,
            // Section II: PPh Terutang
            PPhBadanCalculation pphBadan,
            // Section III: Kredit Pajak
            BigDecimal kreditPPh23,
            BigDecimal kreditPPh25,
            BigDecimal totalKreditPajak,
            BigDecimal pph29
    ) {}

    public record L1LineItem(
            String accountCode,
            String accountName,
            BigDecimal amount
    ) {}

    public record L1LossItem(
            int originYear,
            BigDecimal remainingAmount,
            int expiryYear
    ) {}

    public record L1AdjustmentItem(
            String description,
            String category,
            String direction,
            BigDecimal amount,
            String accountCode,
            String pasal
    ) {}

    public record L4Report(
            int year,
            List<L4LineItem> items,
            BigDecimal totalGross,
            BigDecimal totalTax
    ) {}

    public record L4LineItem(
            String taxObjectCode,
            String description,
            BigDecimal grossAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount
    ) {}

    public record Transkrip8AReport(
            int year,
            // Neraca (Balance Sheet)
            List<Transkrip8ALineItem> assetItems,
            BigDecimal totalAssets,
            List<Transkrip8ALineItem> liabilityItems,
            BigDecimal totalLiabilities,
            List<Transkrip8ALineItem> equityItems,
            BigDecimal totalEquity,
            BigDecimal currentYearEarnings,
            // Laba Rugi (Income Statement)
            List<Transkrip8ALineItem> revenueItems,
            BigDecimal totalRevenue,
            List<Transkrip8ALineItem> expenseItems,
            BigDecimal totalExpense,
            BigDecimal netIncome
    ) {}

    public record Transkrip8ALineItem(
            String accountCode,
            String accountName,
            BigDecimal amount
    ) {}

    public record L9Report(
            int year,
            List<L9LineItem> items,
            BigDecimal totalPurchaseCost,
            BigDecimal totalDepreciationThisYear,
            BigDecimal totalAccumulatedDepreciation,
            BigDecimal totalBookValue
    ) {}

    public record L9LineItem(
            String assetName,
            String fiscalGroup,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            String depreciationMethod,
            int usefulLifeYears,
            BigDecimal depreciationThisYear,
            BigDecimal accumulatedDepreciation,
            BigDecimal bookValue
    ) {}

    public record Bpa1Report(
            int year,
            List<Bpa1LineItem> items,
            BigDecimal totalGross,
            BigDecimal totalPph21Terutang,
            BigDecimal totalPph21Dipotong
    ) {}

    public record LossCarryforwardReport(
            int year,
            List<LossCarryforwardItem> items,
            BigDecimal totalActiveRemaining
    ) {}

    public record LossCarryforwardItem(
            UUID id,
            int originYear,
            BigDecimal originalAmount,
            BigDecimal usedAmount,
            BigDecimal remainingAmount,
            int expiryYear,
            boolean expired,
            String notes
    ) {}

    public record Bpa1LineItem(
            String npwp,
            String nik,
            String name,
            PtkpStatus ptkpStatus,
            int monthCount,
            BigDecimal penghasilanBruto,
            BigDecimal biayaJabatan,
            BigDecimal bpjsDeduction,
            BigDecimal penghasilanNeto,
            BigDecimal ptkp,
            BigDecimal pkp,
            BigDecimal pph21Terutang,
            BigDecimal pph21Dipotong,
            BigDecimal pph21KurangBayar
    ) {}

    // ==================== CONSOLIDATED LAMPIRAN DTOs ====================

    public record SptLampiranReport(
            int year,
            SptTaxpayer taxpayer,
            SptTranskrip8A transkrip8A,
            SptLampiranI lampiranI,
            SptLampiranII lampiranII,
            SptLampiranIII lampiranIII,
            SptLampiranV lampiranV,
            SptPPhBadan pphBadan
    ) {}

    public record SptTaxpayer(String npwp, String nitku, String name) {}

    public record SptTranskrip8A(
            String description,
            List<SptFieldItem> neracaAktiva, BigDecimal totalAktiva,
            List<SptFieldItem> neracaPasiva, BigDecimal totalPasiva,
            SptLabaRugi labaRugi
    ) {}

    public record SptFieldItem(String field, String label, BigDecimal amount) {}

    public record SptLabaRugi(
            BigDecimal pendapatanUsaha,
            BigDecimal hargaPokokPenjualan,
            BigDecimal labaKotorUsaha,
            BigDecimal bebanUsaha,
            BigDecimal labaUsaha,
            BigDecimal pendapatanLainnya,
            BigDecimal bebanLainnya,
            BigDecimal labaBersihSebelumPajak,
            BigDecimal bebanPajak,
            BigDecimal labaBersih
    ) {}

    public record SptLampiranI(
            String description,
            BigDecimal pendapatanNeto,
            List<SptAdjustmentItem> koreksiPositif, BigDecimal totalKoreksiPositif,
            List<SptAdjustmentItem> koreksiNegatif, BigDecimal totalKoreksiNegatif,
            BigDecimal penghasilanNetoFiskal,
            List<SptLossItem> kompensasiKerugian, BigDecimal totalKompensasi,
            BigDecimal penghasilanKenaPajak
    ) {}

    public record SptAdjustmentItem(String description, String pasal, BigDecimal amount) {}

    public record SptLossItem(int originYear, BigDecimal amount, int expiryYear) {}

    public record SptLampiranII(
            String description,
            List<SptExpenseItem> bebanUsaha, BigDecimal totalBebanUsaha,
            List<SptExpenseItem> bebanLuarUsaha, BigDecimal totalBebanLuarUsaha
    ) {}

    public record SptExpenseItem(String code, String description, BigDecimal amount) {}

    public record SptLampiranIII(
            String description,
            List<SptKreditPajakItem> kreditPPh23, BigDecimal totalKreditPPh23
    ) {}

    public record SptKreditPajakItem(
            String pemotong, String npwp, String bupotNumber,
            LocalDate date, BigDecimal dpp, BigDecimal pph23
    ) {}

    public record SptLampiranV(
            String description,
            String note
    ) {}

    public record SptPPhBadan(
            BigDecimal penghasilanKenaPajak,
            BigDecimal pphTerutang,
            BigDecimal kreditPajak,
            BigDecimal pph29KurangBayar
    ) {}

    // ==================== CORETAX SPT EXPORT DTOs ====================

    public record CoretaxSptBadanExport(
            int year,
            CoretaxInduk induk,
            List<CoretaxL1DItem> l1dLabaRugi,
            CoretaxL1DNeraca l1dNeraca,
            List<CoretaxL3Item> l3KreditPajak,
            List<CoretaxPenyusutanItem> penyusutan
    ) {}

    public record CoretaxInduk(
            String npwp,
            String nitku,
            String companyName,
            int tahunPajak,
            BigDecimal peredaranUsaha,
            BigDecimal labaKotorUsaha,
            BigDecimal biayaUsaha,
            BigDecimal penghasilanNetoUsaha,
            BigDecimal penghasilanLuarUsaha,
            BigDecimal biayaLuarUsaha,
            BigDecimal penghasilanNetoLuarUsaha,
            BigDecimal penghasilanNetoKomersial,
            BigDecimal koreksiFiskalPositif,
            BigDecimal koreksiFiskalNegatif,
            BigDecimal penghasilanNetoFiskal,
            BigDecimal kompensasiKerugian,
            BigDecimal penghasilanKenaPajak,
            BigDecimal penghasilanKenaPajakPembulatan,
            BigDecimal pphTerutang,
            String metodePerhitungan,
            BigDecimal kreditPajakPPh23,
            BigDecimal kreditPajakPPh25,
            BigDecimal totalKreditPajak,
            BigDecimal pph29KurangBayar
    ) {}

    public record CoretaxL1DItem(
            String kodeAkun,
            String namaAkun,
            String kategori,
            BigDecimal jumlah
    ) {}

    public record CoretaxL1DNeraca(
            List<CoretaxNeracaItem> aktiva,
            BigDecimal totalAktiva,
            List<CoretaxNeracaItem> pasiva,
            BigDecimal totalPasiva,
            BigDecimal labaTahunBerjalan
    ) {}

    public record CoretaxNeracaItem(
            String kodeAkun,
            String namaAkun,
            BigDecimal jumlah
    ) {}

    public record CoretaxL3Item(
            String namaPemotong,
            String npwpPemotong,
            String nomorBuktiPotong,
            LocalDate tanggal,
            BigDecimal jumlahBruto,
            BigDecimal tarif,
            BigDecimal pphDipotong
    ) {}

    public record CoretaxPenyusutanItem(
            String namaHarta,
            String kelompok,
            LocalDate tanggalPerolehan,
            BigDecimal hargaPerolehan,
            String metodePenyusutan,
            int masaManfaatTahun,
            BigDecimal penyusutanTahunIni,
            BigDecimal akumulasiPenyusutan,
            BigDecimal nilaiSisaBuku
    ) {}
}
