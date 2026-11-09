package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BenefitInKind;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentSource;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentStatus;
import com.artivisi.accountingfinance.enums.FiscalTreatment;
import com.artivisi.accountingfinance.repository.BenefitInKindRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proposes fiscal adjustments from the account flags. Every proposal is a DRAFT that stays out of the
 * fiscal reconciliation until a user confirms it.
 *
 * <p>NON_DEDUCTIBLE: the year's balance of the account. CONDITIONAL: the balance minus the part
 * documented by a benefit-in-kind recipient, which is taxed in that employee's PPh 21.
 * Adjustments already confirmed for the account and year reduce the proposal, so generating again does
 * not count an amount twice. Generating again replaces earlier generated drafts of the year.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiscalAdjustmentDraftService {

    static final String PASAL_NON_DEDUCTIBLE = "Pasal 9 ayat (1) UU PPh";
    static final String PASAL_CONDITIONAL = "Pasal 6 dan Pasal 9 UU PPh";

    private final ReportService reportService;
    private final FiscalAdjustmentRepository fiscalAdjustmentRepository;
    private final BenefitInKindRepository benefitInKindRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public GenerationResult generate(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        fiscalAdjustmentRepository.deleteByYearAndStatusAndSource(
                year, FiscalAdjustmentStatus.DRAFT, FiscalAdjustmentSource.GENERATED);
        fiscalAdjustmentRepository.flush();

        Map<String, BigDecimal> documented = documentedBenefitByAccount(start, end);
        Map<String, BigDecimal> confirmed = confirmedNetByAccount(year);

        List<FiscalAdjustment> drafts = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();
        for (var item : reportService.generateIncomeStatementExcludingClosing(start, end).expenseItems()) {
            ChartOfAccount account = item.account();
            if (item.balance().signum() == 0) {
                continue;
            }
            FiscalTreatment treatment = account.getFiscalTreatment();
            if (treatment == null) {
                unclassified.add(account.getAccountCode() + " " + account.getAccountName());
            } else if (treatment != FiscalTreatment.DEDUCTIBLE) {
                BigDecimal benefit = documented.getOrDefault(account.getAccountCode(), BigDecimal.ZERO);
                BigDecimal already = confirmed.getOrDefault(account.getAccountCode(), BigDecimal.ZERO);
                BigDecimal amount = item.balance().subtract(treatment == FiscalTreatment.CONDITIONAL ? benefit : BigDecimal.ZERO)
                        .subtract(already);
                if (amount.signum() > 0) {
                    drafts.add(fiscalAdjustmentRepository.save(
                            toDraft(year, account, treatment, item.balance(), benefit, already, amount)));
                }
            }
        }
        return new GenerationResult(year, drafts, unclassified,
                transactionRepository.findPostedNumbersWithTagWithoutBenefit(BenefitInKindService.TAG_CODE, start, end));
    }

    @Transactional
    public FiscalAdjustment confirm(UUID id, BigDecimal reviewedAmount) {
        FiscalAdjustment adjustment = fiscalAdjustmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Koreksi fiskal tidak ditemukan: " + id));
        if (adjustment.getStatus() != FiscalAdjustmentStatus.DRAFT) {
            throw new IllegalStateException("Koreksi fiskal ini sudah dikonfirmasi");
        }
        if (reviewedAmount != null) {
            if (reviewedAmount.signum() <= 0) {
                throw new IllegalArgumentException("Jumlah koreksi harus positif");
            }
            adjustment.setAmount(reviewedAmount);
        }
        adjustment.setStatus(FiscalAdjustmentStatus.CONFIRMED);
        return fiscalAdjustmentRepository.save(adjustment);
    }

    private FiscalAdjustment toDraft(int year, ChartOfAccount account, FiscalTreatment treatment,
                                     BigDecimal balance, BigDecimal benefit, BigDecimal already, BigDecimal amount) {
        FiscalAdjustment draft = new FiscalAdjustment();
        draft.setYear(year);
        draft.setDescription(account.getAccountCode() + " " + account.getAccountName());
        draft.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
        draft.setAdjustmentDirection(FiscalAdjustmentDirection.POSITIVE);
        draft.setAmount(amount);
        draft.setAccountCode(account.getAccountCode());
        draft.setStatus(FiscalAdjustmentStatus.DRAFT);
        draft.setSource(FiscalAdjustmentSource.GENERATED);
        if (treatment == FiscalTreatment.CONDITIONAL) {
            draft.setPasal(PASAL_CONDITIONAL);
            draft.setNotes("Akun bersyarat. Saldo " + balance + ", natura dengan penerima " + benefit
                    + ", koreksi terkonfirmasi " + already + ". Periksa syarat pembebanannya sebelum konfirmasi.");
        } else {
            draft.setPasal(PASAL_NON_DEDUCTIBLE);
            draft.setNotes("Akun tidak dapat dikurangkan. Saldo " + balance + ", koreksi terkonfirmasi " + already + ".");
        }
        return draft;
    }

    private Map<String, BigDecimal> confirmedNetByAccount(int year) {
        Map<String, BigDecimal> net = new HashMap<>();
        for (FiscalAdjustment a : fiscalAdjustmentRepository.findByYearAndStatusOrderByAdjustmentCategoryAscDescriptionAsc(
                year, FiscalAdjustmentStatus.CONFIRMED)) {
            if (a.getAccountCode() != null) {
                BigDecimal signed = a.getAdjustmentDirection() == FiscalAdjustmentDirection.POSITIVE
                        ? a.getAmount() : a.getAmount().negate();
                net.merge(a.getAccountCode(), signed, BigDecimal::add);
            }
        }
        return net;
    }

    /**
     * Allocates each transaction's recipient total over its debit lines on conditional accounts, in
     * journal line order. A benefit booked to a deductible account documents nothing on a conditional one.
     */
    private Map<String, BigDecimal> documentedBenefitByAccount(LocalDate start, LocalDate end) {
        Map<Transaction, BigDecimal> perTransaction = new LinkedHashMap<>();
        for (BenefitInKind b : benefitInKindRepository.findPostedBetween(start, end)) {
            perTransaction.merge(b.getTransaction(), b.getAmount(), BigDecimal::add);
        }
        Map<String, BigDecimal> documented = new HashMap<>();
        perTransaction.forEach((transaction, total) -> {
            BigDecimal remaining = total;
            for (JournalEntry entry : transaction.getJournalEntries()) {
                if (entry.getAccount().getFiscalTreatment() == FiscalTreatment.CONDITIONAL
                        && entry.getDebitAmount().signum() > 0 && remaining.signum() > 0) {
                    BigDecimal allocated = remaining.min(entry.getDebitAmount());
                    documented.merge(entry.getAccount().getAccountCode(), allocated, BigDecimal::add);
                    remaining = remaining.subtract(allocated);
                }
            }
        });
        return documented;
    }

    public record GenerationResult(
            int year,
            List<FiscalAdjustment> drafts,
            List<String> unclassifiedAccounts,
            List<String> taggedTransactionsWithoutRecipient
    ) {}
}
