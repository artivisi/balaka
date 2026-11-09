package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.entity.WithholdingDeposit;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.WithholdingDepositStatus;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.repository.WithholdingDepositRepository;
import com.artivisi.accountingfinance.repository.WithholdingPaymentRepository;
import com.artivisi.accountingfinance.repository.WithholdingPaymentRepository.UndocumentedRow;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read API over payees, withheld payments, deposits and the per-masa reports. Other features (self-test,
 * workspace) read withholding data here rather than through repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithholdingQueryService {

    /** A PPN detail on a service payment does not document withholding. */
    private static final Set<TaxType> PPH_TYPES = Set.of(TaxType.PPH_21, TaxType.PPH_23, TaxType.PPH_26, TaxType.PPH_42);

    private final WithholdingPaymentRepository paymentRepository;
    private final WithholdingDepositRepository depositRepository;
    private final VendorRepository vendorRepository;
    private final TaxDeadlineService taxDeadlineService;
    private final TaxTransactionDetailRepository taxDetailRepository;

    /**
     * One tax in one masa. withheld and deposited count POSTED rows only; draftWithheld shows what is pending.
     */
    public record MasaStatus(int year, int month, WithholdingTreatment treatment, BigDecimal gross, BigDecimal withheld,
                             BigDecimal draftWithheld, BigDecimal deposited, BigDecimal outstanding, LocalDate dueDate,
                             LocalDate lastDepositDate, WithholdingDepositStatus status) {}

    /** unclassifiedAccounts: expense accounts with activity in the masa the report cannot judge. */
    public record UndocumentedReport(int year, int month, List<UndocumentedRow> rows, List<String> unclassifiedAccounts,
                                     BigDecimal total) {}

    /** Vendors classified as a payee. */
    public List<Vendor> findPayees() {
        return vendorRepository.findByPayeeTypeIsNotNullOrderByNameAsc();
    }

    public WithholdingPayment findPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pembayaran tidak ditemukan: " + id));
    }

    public WithholdingDeposit findDeposit(UUID id) {
        return depositRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Setoran tidak ditemukan: " + id));
    }

    /** Every status including DRAFT and VOID; both filters are optional. */
    public List<WithholdingPayment> findPayments(int year, int month, WithholdingTreatment treatment, UUID vendorId) {
        YearMonth masa = YearMonth.of(year, month);
        return paymentRepository.findForPeriod(masa.atDay(1), masa.atEndOfMonth(), treatment, vendorId);
    }

    public List<WithholdingDeposit> findDeposits(int year, int month) {
        return depositRepository.findForPeriod(year, month);
    }

    /** One row per treatment. A missing or inactive deadline for a treatment is an error. */
    public List<MasaStatus> getMasaStatus(int year, int month, LocalDate asOf) {
        YearMonth masa = YearMonth.of(year, month);
        List<WithholdingDeposit> deposits = depositRepository.findForPeriod(year, month);
        return Arrays.stream(WithholdingTreatment.values())
                .map(treatment -> masaStatus(masa, treatment, deposits, asOf))
                .toList();
    }

    public UndocumentedReport findUndocumentedServicePayments(int year, int month) {
        YearMonth masa = YearMonth.of(year, month);
        List<UndocumentedRow> rows = paymentRepository.findUndocumentedServicePayments(masa.atDay(1), masa.atEndOfMonth(), PPH_TYPES);
        List<String> unclassified = paymentRepository.findUnclassifiedExpenseAccounts(masa.atDay(1), masa.atEndOfMonth()).stream()
                .map(a -> a.getAccountCode() + " " + a.getAccountName())
                .toList();
        BigDecimal total = rows.stream().map(UndocumentedRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UndocumentedReport(year, month, rows, unclassified, total);
    }

    /**
     * PPh 23, 26 or 4(2) tax details this company withholds, on POSTED transactions dated in the range: the rows of
     * the e-Bupot Unifikasi export. Bupot received from customers on invoices are excluded.
     */
    public List<TaxTransactionDetail> findWithheldDetails(TaxType taxType, LocalDate from, LocalDate to) {
        if (taxType != TaxType.PPH_23 && taxType != TaxType.PPH_26 && taxType != TaxType.PPH_42) {
            throw new IllegalArgumentException("Bukan jenis PPh unifikasi: " + taxType);
        }
        return taxDetailRepository.findEBupotUnifikasiByDateRange(from, to).stream()
                .filter(d -> d.getTaxType() == taxType)
                .toList();
    }

    private MasaStatus masaStatus(YearMonth masa, WithholdingTreatment treatment, List<WithholdingDeposit> deposits, LocalDate asOf) {
        LocalDate start = masa.atDay(1);
        LocalDate end = masa.atEndOfMonth();
        BigDecimal withheld = paymentRepository.sumTax(treatment, start, end, TransactionStatus.POSTED);
        BigDecimal draft = paymentRepository.sumTax(treatment, start, end, TransactionStatus.DRAFT);
        BigDecimal gross = paymentRepository.sumGross(treatment, start, end, TransactionStatus.POSTED);

        List<WithholdingDeposit> posted = deposits.stream()
                .filter(d -> d.getTreatment() == treatment && d.getTransaction().getStatus() == TransactionStatus.POSTED)
                .toList();
        BigDecimal deposited = posted.stream().map(WithholdingDeposit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate lastDeposit = posted.stream().map(WithholdingDeposit::getDepositDate).max(LocalDate::compareTo).orElse(null);

        LocalDate due = dueDate(treatment, masa);
        WithholdingDepositStatus status = status(withheld, deposited, due, lastDeposit, asOf);
        return new MasaStatus(masa.getYear(), masa.getMonthValue(), treatment, gross, withheld, draft, deposited,
                withheld.subtract(deposited), due, lastDeposit, status);
    }

    private WithholdingDepositStatus status(BigDecimal withheld, BigDecimal deposited, LocalDate due, LocalDate lastDeposit, LocalDate asOf) {
        int comparison = deposited.compareTo(withheld);
        if (withheld.signum() == 0 && deposited.signum() == 0) {
            return WithholdingDepositStatus.NOTHING_DUE;
        }
        if (comparison > 0) {
            return WithholdingDepositStatus.OVERDEPOSITED;
        }
        if (comparison == 0) {
            return lastDeposit.isAfter(due) ? WithholdingDepositStatus.LATE : WithholdingDepositStatus.ON_TIME;
        }
        return asOf.isAfter(due) ? WithholdingDepositStatus.OVERDUE : WithholdingDepositStatus.OPEN;
    }

    private LocalDate dueDate(WithholdingTreatment treatment, YearMonth masa) {
        TaxDeadline deadline = taxDeadlineService.findByDeadlineType(treatment.getDeadlineType())
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .orElseThrow(() -> new IllegalStateException("Kewajiban pajak " + treatment.getDeadlineType()
                        + " belum dikonfigurasi di Kalender Pajak"));
        return deadline.getDueDateForPeriod(masa.getYear(), masa.getMonthValue());
    }
}
