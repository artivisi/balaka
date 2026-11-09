package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BenefitInKind;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionTag;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.repository.BenefitInKindRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Records who received a benefit-in-kind through a posted transaction. PayrollService adds the value
 * to the recipient's PPh 21 base in the next calculated run. The system tag NATURA (V008) marks the
 * transaction for the existing tag filter and tag report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BenefitInKindService {

    static final String TAG_CODE = "NATURA";

    private final BenefitInKindRepository benefitInKindRepository;
    private final TransactionRepository transactionRepository;
    private final EmployeeRepository employeeRepository;
    private final TagTypeRepository tagTypeRepository;
    private final TagRepository tagRepository;

    public List<BenefitInKind> findByTransaction(UUID transactionId) {
        return benefitInKindRepository.findByTransactionId(transactionId);
    }

    public List<BenefitInKind> findByYear(int year) {
        return benefitInKindRepository.findPostedBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional
    public BenefitInKind add(UUID transactionId, UUID employeeId, BigDecimal amount, String description) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaksi tidak ditemukan: " + transactionId));
        if (!transaction.isPosted()) {
            throw new IllegalStateException("Penerima natura hanya dapat dicatat pada transaksi berstatus POSTED");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Karyawan tidak ditemukan: " + employeeId));

        BigDecimal expense = transaction.getJournalEntries().stream()
                .filter(e -> e.getAccount().getAccountType() == AccountType.EXPENSE)
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recorded = benefitInKindRepository.findByTransactionId(transactionId).stream()
                .map(BenefitInKind::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (recorded.add(amount).compareTo(expense) > 0) {
            throw new IllegalArgumentException("Total natura " + recorded.add(amount)
                    + " melebihi beban pada transaksi ini (" + expense + ")");
        }

        BenefitInKind benefit = new BenefitInKind();
        benefit.setTransaction(transaction);
        benefit.setEmployee(employee);
        benefit.setAmount(amount);
        benefit.setDescription(description);
        BenefitInKind saved = benefitInKindRepository.save(benefit);

        attachTag(transaction);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        BenefitInKind benefit = benefitInKindRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Data natura tidak ditemukan: " + id));
        if (benefit.getPayrollDetail() != null) {
            throw new IllegalStateException("Natura sudah masuk payroll periode "
                    + benefit.getPayrollDetail().getPayrollRun().getPayrollPeriod()
                    + " dan tidak dapat dihapus. Hitung ulang atau batalkan payroll tersebut lebih dulu.");
        }
        Transaction transaction = benefit.getTransaction();
        benefitInKindRepository.delete(benefit);
        log.info("Deleted benefit in kind {} on transaction {}", benefit.getId(), transaction.getId());
        benefitInKindRepository.flush();
        if (benefitInKindRepository.findByTransactionId(transaction.getId()).isEmpty()) {
            detachTag(transaction);
        }
    }

    private void attachTag(Transaction transaction) {
        boolean tagged = transaction.getTransactionTags().stream()
                .anyMatch(tt -> isSystemTag(tt.getTag()));
        if (tagged) {
            return;
        }
        var tagType = tagTypeRepository.findByCode(TAG_CODE)
                .orElseThrow(() -> new IllegalStateException("Tipe label sistem " + TAG_CODE + " tidak ditemukan"));
        Tag tag = tagRepository.findByTagTypeIdAndCode(tagType.getId(), TAG_CODE)
                .orElseThrow(() -> new IllegalStateException("Label sistem " + TAG_CODE + " tidak ditemukan"));
        TransactionTag link = new TransactionTag();
        link.setTransaction(transaction);
        link.setTag(tag);
        List<TransactionTag> tags = new ArrayList<>(transaction.getTransactionTags());
        tags.add(link);
        transaction.setTransactionTags(tags);
    }

    private void detachTag(Transaction transaction) {
        transaction.setTransactionTags(transaction.getTransactionTags().stream()
                .filter(tt -> !isSystemTag(tt.getTag()))
                .toList());
    }

    private boolean isSystemTag(Tag tag) {
        return TAG_CODE.equals(tag.getCode()) && TAG_CODE.equals(tag.getTagType().getCode());
    }
}
