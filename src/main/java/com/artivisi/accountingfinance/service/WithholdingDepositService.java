package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.WithholdingDeposit;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.WithholdingDepositRepository;
import com.artivisi.accountingfinance.repository.WithholdingPaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * Setoran of withheld tax. PPh 21 on payroll shares the Hutang PPh 21 account and has its own deposit template;
 * this tracks only the bukan pegawai part. A deposit is a DRAFT until the user posts it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WithholdingDepositService {

    public static final String DEPOSIT_TEMPLATE = "Setor PPh Dipotong";
    public static final String SOURCE_DOCUMENT_TYPE = "WITHHOLDING_DEPOSIT";

    private final WithholdingDepositRepository depositRepository;
    private final WithholdingPaymentRepository paymentRepository;
    private final ChartOfAccountRepository accountRepository;
    private final DocumentPostingService documentPostingService;
    private final TransactionService transactionService;
    private final WithholdingAccountResolver accountResolver;
    private final EntityManager entityManager;

    @Builder
    public record CreateDeposit(WithholdingTreatment treatment, int year, int month, BigDecimal amount, LocalDate depositDate,
                                UUID bankAccountId, String billingCode, String ntpn, String createdBy) {}

    public WithholdingDeposit create(CreateDeposit command) {
        if (command.treatment() == null || command.amount() == null || command.depositDate() == null
                || command.bankAccountId() == null) {
            throw new IllegalArgumentException("Jenis pajak, jumlah, tanggal setor dan akun bank wajib diisi");
        }
        if (command.amount().signum() <= 0) {
            throw new IllegalArgumentException("Jumlah setoran harus lebih dari nol");
        }
        YearMonth masa = YearMonth.of(command.year(), command.month());
        BigDecimal withheld = paymentRepository.sumTax(command.treatment(), masa.atDay(1), masa.atEndOfMonth(), TransactionStatus.POSTED);
        BigDecimal deposited = depositRepository.sumNonVoid(command.treatment(), command.year(), command.month());
        BigDecimal outstanding = withheld.subtract(deposited);
        if (command.amount().compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("Jumlah setoran (Rp " + command.amount()
                    + ") melebihi potongan terposting yang belum disetor (Rp " + outstanding + ")");
        }
        ChartOfAccount bank = accountRepository.findById(command.bankAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Akun bank tidak ditemukan: " + command.bankAccountId()));
        if (bank.getAccountType() != AccountType.ASSET || Boolean.TRUE.equals(bank.getIsHeader())) {
            throw new IllegalArgumentException("Akun bank " + bank.getAccountCode() + " harus akun aset yang bukan header");
        }

        Transaction transaction = documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName(DEPOSIT_TEMPLATE)
                .date(command.depositDate())
                .description("Setor " + command.treatment().getIndonesianName() + " masa " + masa)
                .amount(command.amount())
                .hintToAccount(Map.of("HUTANG_PPH", accountResolver.payableAccount(command.treatment()).getId(), "BANK", bank.getId()))
                .createdBy(command.createdBy())
                .sourceDocumentType(SOURCE_DOCUMENT_TYPE)
                .build());

        WithholdingDeposit deposit = new WithholdingDeposit();
        deposit.setTransaction(transaction);
        deposit.setTreatment(command.treatment());
        deposit.setPeriodYear(command.year());
        deposit.setPeriodMonth(command.month());
        deposit.setAmount(command.amount());
        deposit.setDepositDate(command.depositDate());
        deposit.setBillingCode(command.billingCode());
        deposit.setNtpn(command.ntpn());
        WithholdingDeposit saved = depositRepository.save(deposit);
        transaction.setSourceDocumentId(saved.getId());
        return saved;
    }

    public WithholdingDeposit post(UUID depositId, String postedBy) {
        WithholdingDeposit deposit = find(depositId);
        transactionService.post(deposit.getTransaction().getId(), postedBy);
        return deposit;
    }

    public void cancel(UUID depositId) {
        WithholdingDeposit deposit = find(depositId);
        Transaction transaction = deposit.getTransaction();
        if (transaction.getStatus() != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Hanya setoran berstatus draf yang dapat dibatalkan. "
                    + "Setoran yang sudah diposting dibatalkan lewat void transaksi.");
        }
        depositRepository.delete(deposit);
        depositRepository.flush();
        transactionService.delete(transaction.getId());
        entityManager.flush();
    }

    private WithholdingDeposit find(UUID id) {
        return depositRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Setoran tidak ditemukan: " + id));
    }
}
