package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillPayment;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.enums.PayeeType;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.TaxObjectCode;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.WithholdingRateBasis;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.repository.BillPaymentRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.repository.WithholdingPaymentRepository;
import com.artivisi.accountingfinance.service.event.TransactionPostedEvent;
import com.artivisi.accountingfinance.service.event.TransactionVoidedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Payment flow with withholding. create() composes a DRAFT transaction and its tax detail; nothing reaches the
 * ledger until the user posts it, here or through the transaction screens. Read access is {@link WithholdingQueryService}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WithholdingPaymentService {

    public static final String PAYMENT_TEMPLATE = "Pembayaran dengan Pemotongan PPh";
    public static final String SOURCE_DOCUMENT_TYPE = "WITHHOLDING_PAYMENT";
    private static final String VAR_SETTLED = "settledAmount";
    private static final String VAR_NET = "netAmount";
    private static final String VAR_TAX = "taxAmount";

    private final WithholdingPaymentRepository paymentRepository;
    private final VendorRepository vendorRepository;
    private final ChartOfAccountRepository accountRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillService billService;
    private final CompanyConfigService companyConfigService;
    private final DocumentPostingService documentPostingService;
    private final TransactionService transactionService;
    private final TaxTransactionDetailService taxDetailService;
    private final WithholdingCalculationService calculationService;
    private final WithholdingAccountResolver accountResolver;
    private final EntityManager entityManager;

    /**
     * @param treatment        null takes the payee's default; both null is an error
     * @param taxObjectCode    null takes the payee's default; both null is an error
     * @param grossAmount      withholding base
     * @param expenseAccountId account debited; must be null when billId is set (the payable is debited instead)
     * @param billId           optional approved bill this payment settles
     * @param settledAmount    bill amount settled, including PPN; required with billId, must be null without it
     * @param paymentMethod    required with billId, recorded on the BillPayment at posting
     */
    @Builder
    public record CreatePayment(UUID vendorId, WithholdingTreatment treatment, String taxObjectCode, LocalDate paymentDate,
                                BigDecimal grossAmount, UUID expenseAccountId, UUID bankAccountId, UUID billId,
                                BigDecimal settledAmount, PaymentMethod paymentMethod, String referenceNumber,
                                String description, String createdBy) {}

    public WithholdingPayment create(CreatePayment command) {
        require(command.vendorId(), "Penerima wajib diisi");
        require(command.paymentDate(), "Tanggal pembayaran wajib diisi");
        require(command.grossAmount(), "Jumlah bruto wajib diisi");
        require(command.bankAccountId(), "Akun bank wajib diisi");
        if (command.description() == null || command.description().isBlank()) {
            throw new IllegalArgumentException("Keterangan wajib diisi");
        }
        if (command.grossAmount().signum() <= 0) {
            throw new IllegalArgumentException("Jumlah bruto harus lebih dari nol");
        }

        Vendor payee = vendorRepository.findByIdForUpdate(command.vendorId())
                .orElseThrow(() -> new EntityNotFoundException("Penerima tidak ditemukan: " + command.vendorId()));
        WithholdingTreatment treatment = resolveTreatment(command.treatment(), payee);
        String objectCode = resolveObjectCode(command.taxObjectCode(), payee, treatment);

        Bill bill = command.billId() == null ? null : resolveBill(command, payee);
        UUID debitAccountId = resolveDebitAccount(command, bill);
        BigDecimal settled = bill == null ? command.grossAmount() : command.settledAmount();
        ChartOfAccount bank = requireAccount(command.bankAccountId(), AccountType.ASSET, "Akun bank");
        ChartOfAccount payable = accountResolver.payableAccount(treatment);

        boolean hasTaxId = payee.hasTaxId();
        WithholdingCalculationService.Withholding result = calculationService.calculate(
                treatment, command.grossAmount(), hasTaxId, payee, command.paymentDate());
        BigDecimal net = settled.subtract(result.tax());
        if (net.signum() < 0) {
            throw new IllegalArgumentException("PPh dipotong melebihi jumlah yang dibayar");
        }

        Map<String, UUID> hints = new HashMap<>();
        hints.put("BEBAN", debitAccountId);
        hints.put("BANK", bank.getId());
        hints.put("HUTANG_PPH", payable.getId());

        Map<String, BigDecimal> variables = new HashMap<>();
        variables.put(VAR_SETTLED, settled);
        variables.put(VAR_NET, net);
        variables.put(VAR_TAX, result.tax());

        WithholdingPayment payment = new WithholdingPayment();
        payment.setVendor(payee);
        payment.setBill(bill);
        payment.setTreatment(treatment);
        payment.setTaxObjectCode(objectCode);
        payment.setPaymentDate(command.paymentDate());
        payment.setGrossAmount(command.grossAmount());
        payment.setSettledAmount(settled);
        payment.setHasTaxId(hasTaxId);
        payment.setPaymentMethod(command.paymentMethod());
        payment.setReferenceNumber(command.referenceNumber());
        applyResult(payment, result, net);

        Transaction transaction = documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName(PAYMENT_TEMPLATE)
                .date(command.paymentDate())
                .description(command.description())
                .amount(settled)
                .hintToAccount(hints)
                .variables(variables)
                .createdBy(command.createdBy())
                .sourceDocumentType(SOURCE_DOCUMENT_TYPE)
                .build());
        payment.setTransaction(transaction);
        payment.setTaxDetail(taxDetailService.save(transaction.getId(), buildTaxDetail(payment, payee, objectCode)));
        WithholdingPayment saved = paymentRepository.save(payment);
        transaction.setSourceDocumentId(saved.getId());
        return saved;
    }

    /** Posting delegates to the transaction; the event listener below records the bill payment. */
    public WithholdingPayment post(UUID paymentId, String postedBy) {
        WithholdingPayment payment = find(paymentId);
        transactionService.post(payment.getTransaction().getId(), postedBy);
        return payment;
    }

    /** Removes a DRAFT payment with its transaction. Each payment is taxed on its own gross, so no other payment changes. */
    public void cancel(UUID paymentId) {
        WithholdingPayment payment = find(paymentId);
        Transaction transaction = payment.getTransaction();
        if (transaction.getStatus() != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Hanya pembayaran berstatus draf yang dapat dibatalkan. "
                    + "Pembayaran yang sudah diposting dibatalkan lewat void transaksi.");
        }
        TaxTransactionDetail detail = payment.getTaxDetail();
        paymentRepository.delete(payment);
        paymentRepository.flush();
        if (detail != null) {
            taxDetailService.delete(detail.getId());
        }
        transactionService.delete(transaction.getId());
        entityManager.flush();
    }

    @EventListener
    public void onTransactionPosted(TransactionPostedEvent event) {
        paymentRepository.findByTransactionId(event.transaction().getId())
                .filter(payment -> payment.getBill() != null)
                .ifPresent(payment -> {
                    BillPayment billPayment = new BillPayment();
                    billPayment.setPaymentDate(payment.getPaymentDate());
                    billPayment.setAmount(payment.getSettledAmount());
                    billPayment.setPaymentMethod(payment.getPaymentMethod());
                    billPayment.setReferenceNumber(payment.getReferenceNumber());
                    billService.recordPayment(payment.getBill().getId(), billPayment);
                    payment.setBillPayment(billPayment);
                });
    }

    @EventListener
    public void onTransactionVoided(TransactionVoidedEvent event) {
        paymentRepository.findByTransactionId(event.transaction().getId())
                .filter(payment -> payment.getBillPayment() != null)
                .ifPresent(payment -> {
                    UUID billPaymentId = payment.getBillPayment().getId();
                    payment.setBillPayment(null);
                    paymentRepository.flush();
                    billService.reversePayment(billPaymentId);
                });
    }

    private void applyResult(WithholdingPayment payment, WithholdingCalculationService.Withholding result, BigDecimal net) {
        payment.setDppAmount(result.dpp());
        payment.setTaxRate(result.rate());
        payment.setTaxAmount(result.tax());
        payment.setNetAmount(net);
        payment.setRateBasis(result.basis());
    }

    private WithholdingTreatment resolveTreatment(WithholdingTreatment requested, Vendor payee) {
        if (payee.getPayeeType() == null) {
            throw new IllegalArgumentException("Jenis penerima pada vendor " + payee.getCode() + " belum diisi");
        }
        WithholdingTreatment treatment = requested != null ? requested : payee.getDefaultWithholding();
        if (treatment == null) {
            throw new IllegalArgumentException("Perlakuan pajak wajib diisi: vendor " + payee.getCode() + " tidak punya perlakuan bawaan");
        }
        if (!treatment.allows(payee.getPayeeType())) {
            throw new IllegalArgumentException(treatment.getIndonesianName() + " tidak berlaku untuk "
                    + payee.getPayeeType().getIndonesianName());
        }
        return treatment;
    }

    private String resolveObjectCode(String requested, Vendor payee, WithholdingTreatment treatment) {
        String code = requested != null && !requested.isBlank() ? requested : payee.getDefaultTaxObjectCode();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Kode objek pajak wajib diisi");
        }
        TaxObjectCode objectCode = TaxObjectCode.fromCode(code);
        if (objectCode == null || objectCode.getTaxType() != treatment.getTaxType()) {
            throw new IllegalArgumentException("Kode objek pajak " + code + " tidak berlaku untuk " + treatment.getIndonesianName());
        }
        return code;
    }

    private Bill resolveBill(CreatePayment command, Vendor payee) {
        require(command.settledAmount(), "Jumlah pelunasan tagihan wajib diisi");
        require(command.paymentMethod(), "Metode pembayaran wajib diisi untuk pelunasan tagihan");
        Bill bill = billService.findById(command.billId());
        if (bill.getVendor() == null || !bill.getVendor().getId().equals(payee.getId())) {
            throw new IllegalArgumentException("Tagihan " + bill.getBillNumber() + " bukan milik penerima ini");
        }
        if (bill.getStatus() != BillStatus.APPROVED && bill.getStatus() != BillStatus.OVERDUE
                && bill.getStatus() != BillStatus.PARTIAL) {
            throw new IllegalStateException("Tagihan " + bill.getBillNumber() + " belum disetujui atau sudah selesai");
        }
        if (command.grossAmount().compareTo(command.settledAmount()) > 0) {
            throw new IllegalArgumentException("Jumlah bruto melebihi jumlah pelunasan tagihan");
        }
        BigDecimal committed = billPaymentRepository.sumPaymentsByBillId(bill.getId())
                .add(paymentRepository.sumDraftSettledOnBill(bill.getId()))
                .add(command.settledAmount());
        if (committed.compareTo(bill.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Pembayaran dan draf pembayaran (Rp " + committed
                    + ") melebihi total tagihan (Rp " + bill.getTotalAmount() + ")");
        }
        return bill;
    }

    private UUID resolveDebitAccount(CreatePayment command, Bill bill) {
        if (bill != null) {
            if (command.expenseAccountId() != null) {
                throw new IllegalArgumentException("Pelunasan tagihan mendebit hutang usaha; akun beban tidak boleh diisi");
            }
            ChartOfAccount payable = companyConfigService.getConfig().getPayableAccount();
            if (payable == null) {
                throw new IllegalStateException("Akun Hutang Usaha belum dikonfigurasi di Pengaturan Perusahaan");
            }
            return payable.getId();
        }
        if (command.settledAmount() != null) {
            throw new IllegalArgumentException("Jumlah pelunasan hanya berlaku untuk pembayaran tagihan");
        }
        require(command.expenseAccountId(), "Akun beban wajib diisi");
        return requireAccount(command.expenseAccountId(), AccountType.EXPENSE, "Akun beban").getId();
    }

    private ChartOfAccount requireAccount(UUID id, AccountType type, String label) {
        ChartOfAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(label + " tidak ditemukan: " + id));
        if (account.getAccountType() != type || Boolean.TRUE.equals(account.getIsHeader())) {
            throw new IllegalArgumentException(label + " " + account.getAccountCode() + " harus akun " + type + " yang bukan header");
        }
        return account;
    }

    private TaxTransactionDetail buildTaxDetail(WithholdingPayment payment, Vendor payee, String objectCode) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(payment.getTreatment().getTaxType());
        detail.setTaxObjectCode(objectCode);
        detail.setDpp(payment.getDppAmount());
        detail.setGrossAmount(payment.getGrossAmount());
        detail.setTaxRate(payment.getTaxRate());
        detail.setTaxAmount(payment.getTaxAmount());
        detail.setCounterpartyName(payee.getName());
        detail.setCounterpartyAddress(payee.getAddress());
        if (payee.getPayeeType() == PayeeType.LUAR_NEGERI) {
            detail.setCounterpartyIdType("FOREIGN");
            detail.setCounterpartyForeignTin(payee.getForeignTin());
            detail.setCounterpartyCountry(payee.getCountryCode());
            if (payment.getRateBasis() == WithholdingRateBasis.PPH26_TREATY) {
                detail.setDgtFormNumber(payee.getDgtFormNumber());
            }
            return detail;
        }
        boolean hasNpwp = payee.getNpwp() != null && !payee.getNpwp().isBlank();
        boolean hasNik = payee.getNik() != null && !payee.getNik().isBlank();
        detail.setCounterpartyNpwp(hasNpwp ? payee.getNpwp() : null);
        detail.setCounterpartyNitku(payee.getNitku());
        detail.setCounterpartyNik(hasNik ? payee.getNik() : null);
        if (hasNpwp) {
            detail.setCounterpartyIdType("TIN");
        } else {
            detail.setCounterpartyIdType(hasNik ? "NIK" : "NONE");
        }
        return detail;
    }

    private WithholdingPayment find(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pembayaran tidak ditemukan: " + id));
    }

    private void require(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
