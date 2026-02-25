package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TaxReportDetailService {

    private final TaxTransactionDetailRepository taxTransactionDetailRepository;
    private final FiscalAdjustmentRepository fiscalAdjustmentRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ReportService reportService;

    private static final String HUTANG_PPN_CODE = "2.1.03";
    private static final String PPN_MASUKAN_CODE = "1.1.25";
    private static final String KREDIT_PAJAK_PPH23_CODE = "1.1.26";
    private static final String BEBAN_PPH25_CODE = "5.9.02";

    private static final BigDecimal PPH_BADAN_RATE = new BigDecimal("0.22");
    private static final BigDecimal PASAL_31E_DISCOUNT_RATE = new BigDecimal("0.50");
    private static final BigDecimal REVENUE_THRESHOLD_FULL = new BigDecimal("4800000000");
    private static final BigDecimal REVENUE_THRESHOLD_MAX = new BigDecimal("50000000000");

    // ==================== PPN DETAIL REPORT ====================

    public PPNDetailReport generatePPNDetailReport(LocalDate startDate, LocalDate endDate) {
        List<TaxTransactionDetail> keluaranItems = taxTransactionDetailRepository
                .findEFakturKeluaranByDateRange(startDate, endDate);
        List<TaxTransactionDetail> masukanItems = taxTransactionDetailRepository
                .findEFakturMasukanByDateRange(startDate, endDate);

        BigDecimal totalDppKeluaran = keluaranItems.stream()
                .map(TaxTransactionDetail::getDpp)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPpnKeluaran = keluaranItems.stream()
                .map(TaxTransactionDetail::getPpn)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDppMasukan = masukanItems.stream()
                .map(TaxTransactionDetail::getDpp)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPpnMasukan = masukanItems.stream()
                .map(TaxTransactionDetail::getPpn)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PPNDetailReport(startDate, endDate,
                keluaranItems, masukanItems,
                totalDppKeluaran, totalPpnKeluaran,
                totalDppMasukan, totalPpnMasukan);
    }

    // ==================== PPh 23 DETAIL REPORT ====================

    public PPh23DetailReport generatePPh23DetailReport(LocalDate startDate, LocalDate endDate) {
        List<TaxTransactionDetail> items = taxTransactionDetailRepository
                .findPPh23ByDateRange(startDate, endDate);

        BigDecimal totalGross = items.stream()
                .map(TaxTransactionDetail::getGrossAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = items.stream()
                .map(TaxTransactionDetail::getTaxAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PPh23DetailReport(startDate, endDate, items, totalGross, totalTax);
    }

    // ==================== PPN CROSS-CHECK REPORT ====================

    public PPNCrossCheckReport generatePPNCrossCheckReport(LocalDate startDate, LocalDate endDate) {
        // Sum PPN from tax_transaction_details (per-faktur)
        PPNDetailReport detailReport = generatePPNDetailReport(startDate, endDate);
        BigDecimal fakturPpnKeluaran = detailReport.totalPpnKeluaran();
        BigDecimal fakturPpnMasukan = detailReport.totalPpnMasukan();

        // Get ledger account balances from journal entries
        BigDecimal ledgerPpnKeluaran = BigDecimal.ZERO;
        BigDecimal ledgerPpnMasukan = BigDecimal.ZERO;

        chartOfAccountRepository.findByAccountCode(HUTANG_PPN_CODE).ifPresent(account -> {
            // Hutang PPN is a LIABILITY — credit normal balance
            // PPN Keluaran = credit - debit for the period
        });

        // Hutang PPN (2.1.03) — LIABILITY, credit balance = PPN Keluaran
        var hutangPpnOpt = chartOfAccountRepository.findByAccountCode(HUTANG_PPN_CODE);
        if (hutangPpnOpt.isPresent()) {
            var account = hutangPpnOpt.get();
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            ledgerPpnKeluaran = credit.subtract(debit);
        }

        // PPN Masukan (1.1.25) — ASSET, debit balance = PPN Masukan
        var ppnMasukanOpt = chartOfAccountRepository.findByAccountCode(PPN_MASUKAN_CODE);
        if (ppnMasukanOpt.isPresent()) {
            var account = ppnMasukanOpt.get();
            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            ledgerPpnMasukan = debit.subtract(credit);
        }

        BigDecimal keluaranDiff = fakturPpnKeluaran.subtract(ledgerPpnKeluaran);
        BigDecimal masukanDiff = fakturPpnMasukan.subtract(ledgerPpnMasukan);

        return new PPNCrossCheckReport(startDate, endDate,
                fakturPpnKeluaran, ledgerPpnKeluaran, keluaranDiff,
                fakturPpnMasukan, ledgerPpnMasukan, masukanDiff);
    }

    // ==================== REKONSILIASI FISKAL ====================

    public RekonsiliasiFiskalReport generateRekonsiliasiFiskal(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // Get commercial income statement
        ReportService.IncomeStatementReport incomeStatement =
                reportService.generateIncomeStatement(startDate, endDate);

        // Get fiscal adjustments
        List<FiscalAdjustment> adjustments = fiscalAdjustmentRepository
                .findByYearOrderByAdjustmentCategoryAscDescriptionAsc(year);

        // Calculate total positive and negative adjustments
        BigDecimal totalPositiveAdjustment = adjustments.stream()
                .filter(a -> a.getAdjustmentDirection() == FiscalAdjustmentDirection.POSITIVE)
                .map(FiscalAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNegativeAdjustment = adjustments.stream()
                .filter(a -> a.getAdjustmentDirection() == FiscalAdjustmentDirection.NEGATIVE)
                .map(FiscalAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netAdjustment = totalPositiveAdjustment.subtract(totalNegativeAdjustment);

        // PKP = commercial net income + net fiscal adjustment
        BigDecimal commercialNetIncome = incomeStatement.netIncome();
        BigDecimal pkp = commercialNetIncome.add(netAdjustment);

        // Ensure PKP is not negative for tax calculation
        BigDecimal taxablePkp = pkp.compareTo(BigDecimal.ZERO) > 0 ? pkp : BigDecimal.ZERO;

        // Calculate PPh Badan
        PPhBadanCalculation pphBadan = calculatePPhBadan(year, taxablePkp, incomeStatement.totalRevenue());

        return new RekonsiliasiFiskalReport(year, incomeStatement, adjustments,
                totalPositiveAdjustment, totalNegativeAdjustment, netAdjustment,
                commercialNetIncome, pkp, pphBadan);
    }

    // ==================== PPh BADAN CALCULATION ====================

    public PPhBadanCalculation calculatePPhBadan(int year, BigDecimal pkp, BigDecimal totalRevenue) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // Calculate PPh Badan with Pasal 31E logic
        BigDecimal pphTerutang;
        String calculationMethod;

        if (totalRevenue.compareTo(REVENUE_THRESHOLD_FULL) < 0) {
            // Revenue < 4.8B: full 50% discount
            pphTerutang = pkp.multiply(PPH_BADAN_RATE).multiply(PASAL_31E_DISCOUNT_RATE)
                    .setScale(0, RoundingMode.DOWN);
            calculationMethod = "Fasilitas Pasal 31E penuh (omset < 4,8M)";
        } else if (totalRevenue.compareTo(REVENUE_THRESHOLD_MAX) < 0) {
            // 4.8B <= Revenue < 50B: proportional
            BigDecimal facilitatedPortion = REVENUE_THRESHOLD_FULL.divide(totalRevenue, 10, RoundingMode.HALF_UP)
                    .multiply(pkp).setScale(0, RoundingMode.DOWN);
            BigDecimal nonFacilitatedPortion = pkp.subtract(facilitatedPortion);

            BigDecimal pphFacilitated = facilitatedPortion.multiply(PPH_BADAN_RATE)
                    .multiply(PASAL_31E_DISCOUNT_RATE).setScale(0, RoundingMode.DOWN);
            BigDecimal pphNonFacilitated = nonFacilitatedPortion.multiply(PPH_BADAN_RATE)
                    .setScale(0, RoundingMode.DOWN);

            pphTerutang = pphFacilitated.add(pphNonFacilitated);
            calculationMethod = "Fasilitas Pasal 31E proporsional (4,8M ≤ omset < 50M)";
        } else {
            // Revenue >= 50B: no discount
            pphTerutang = pkp.multiply(PPH_BADAN_RATE).setScale(0, RoundingMode.DOWN);
            calculationMethod = "Tarif normal 22% (omset ≥ 50M)";
        }

        // Kredit Pajak: PPh 23 yang dipotong pihak lain (account 1.1.26)
        BigDecimal kreditPajakPPh23 = BigDecimal.ZERO;
        var kreditPph23Opt = chartOfAccountRepository.findByAccountCode(KREDIT_PAJAK_PPH23_CODE);
        if (kreditPph23Opt.isPresent()) {
            var account = kreditPph23Opt.get();
            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            kreditPajakPPh23 = debit.subtract(credit); // ASSET — debit normal
        }

        // Kredit Pajak: PPh 25 yang sudah dibayar (account 5.9.02 debits)
        BigDecimal kreditPajakPPh25 = BigDecimal.ZERO;
        var bebanPph25Opt = chartOfAccountRepository.findByAccountCode(BEBAN_PPH25_CODE);
        if (bebanPph25Opt.isPresent()) {
            var account = bebanPph25Opt.get();
            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            kreditPajakPPh25 = debit; // Sum of all PPh 25 payments
        }

        BigDecimal totalKreditPajak = kreditPajakPPh23.add(kreditPajakPPh25);

        // PPh 29 = PPh Terutang - Kredit Pajak
        BigDecimal pph29 = pphTerutang.subtract(totalKreditPajak);

        return new PPhBadanCalculation(pkp, totalRevenue, pphTerutang, calculationMethod,
                kreditPajakPPh23, kreditPajakPPh25, totalKreditPajak, pph29);
    }

    // ==================== FISCAL ADJUSTMENT CRUD ====================

    @Transactional
    public FiscalAdjustment saveAdjustment(FiscalAdjustment adjustment) {
        return fiscalAdjustmentRepository.save(adjustment);
    }

    @Transactional
    public void deleteAdjustment(UUID id) {
        fiscalAdjustmentRepository.deleteById(id);
    }

    public List<FiscalAdjustment> findAdjustmentsByYear(int year) {
        return fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(year);
    }

    // ==================== DTOs ====================

    public record PPNDetailReport(
            LocalDate startDate,
            LocalDate endDate,
            List<TaxTransactionDetail> keluaranItems,
            List<TaxTransactionDetail> masukanItems,
            BigDecimal totalDppKeluaran,
            BigDecimal totalPpnKeluaran,
            BigDecimal totalDppMasukan,
            BigDecimal totalPpnMasukan
    ) {}

    public record PPh23DetailReport(
            LocalDate startDate,
            LocalDate endDate,
            List<TaxTransactionDetail> items,
            BigDecimal totalGross,
            BigDecimal totalTax
    ) {}

    public record PPNCrossCheckReport(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal fakturPpnKeluaran,
            BigDecimal ledgerPpnKeluaran,
            BigDecimal keluaranDifference,
            BigDecimal fakturPpnMasukan,
            BigDecimal ledgerPpnMasukan,
            BigDecimal masukanDifference
    ) {}

    public record RekonsiliasiFiskalReport(
            int year,
            ReportService.IncomeStatementReport incomeStatement,
            List<FiscalAdjustment> adjustments,
            BigDecimal totalPositiveAdjustment,
            BigDecimal totalNegativeAdjustment,
            BigDecimal netAdjustment,
            BigDecimal commercialNetIncome,
            BigDecimal pkp,
            PPhBadanCalculation pphBadan
    ) {}

    public record PPhBadanCalculation(
            BigDecimal pkp,
            BigDecimal totalRevenue,
            BigDecimal pphTerutang,
            String calculationMethod,
            BigDecimal kreditPajakPPh23,
            BigDecimal kreditPajakPPh25,
            BigDecimal totalKreditPajak,
            BigDecimal pph29
    ) {}
}
