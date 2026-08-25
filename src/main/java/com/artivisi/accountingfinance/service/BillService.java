package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.BillPayment;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.repository.BillPaymentRepository;
import com.artivisi.accountingfinance.repository.BillRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.security.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final VendorRepository vendorRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ProductRepository productRepository;
    private final DocumentPostingService documentPostingService;
    private final CompanyConfigService companyConfigService;

    public Bill findById(UUID id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tagihan tidak ditemukan: " + id));
    }

    public Bill findByBillNumber(String billNumber) {
        return billRepository.findByBillNumber(billNumber)
                .orElseThrow(() -> new EntityNotFoundException("Tagihan tidak ditemukan: " + billNumber));
    }

    public Page<Bill> findAll(Pageable pageable) {
        return billRepository.findAllByOrderByBillDateDesc(pageable);
    }

    public Page<Bill> findByFilters(BillStatus status, UUID vendorId, Pageable pageable) {
        return billRepository.findByFilters(status, vendorId, pageable);
    }

    public Page<Bill> findByFiltersWithDates(BillStatus status, UUID vendorId,
                                              LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        return billRepository.findByFiltersWithDates(status, vendorId, dateFrom, dateTo, pageable);
    }

    public List<Bill> findByVendorId(UUID vendorId) {
        return billRepository.findByVendorId(vendorId);
    }

    public long countByStatus(BillStatus status) {
        return billRepository.countByStatus(status);
    }

    @Transactional
    public Bill create(Bill bill, List<BillLine> lines) {
        if (bill.getBillNumber() == null || bill.getBillNumber().isBlank()) {
            bill.setBillNumber(generateBillNumber());
        } else {
            if (billRepository.existsByBillNumber(bill.getBillNumber())) {
                throw new IllegalArgumentException("Nomor tagihan sudah digunakan: " + bill.getBillNumber());
            }
        }

        if (bill.getVendor() != null && bill.getVendor().getId() != null) {
            Vendor vendor = vendorRepository.findById(bill.getVendor().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Vendor tidak ditemukan"));
            bill.setVendor(vendor);
        }

        bill.setStatus(BillStatus.DRAFT);

        if (lines != null && !lines.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                BillLine line = lines.get(i);
                line.setBill(bill);
                line.setLineOrder(i);
                resolveLineReferences(line);
            }
            bill.getLines().addAll(lines);
            bill.recalculateFromLines();
        }

        return billRepository.save(bill);
    }

    @Transactional
    public Bill update(UUID id, Bill updatedBill, List<BillLine> lines) {
        Bill existing = findById(id);

        if (existing.getStatus() != BillStatus.DRAFT) {
            throw new IllegalStateException("Hanya tagihan draf yang dapat diedit");
        }

        if (!existing.getBillNumber().equals(updatedBill.getBillNumber())) {
            if (billRepository.existsByBillNumber(updatedBill.getBillNumber())) {
                throw new IllegalArgumentException("Nomor tagihan sudah digunakan: " + updatedBill.getBillNumber());
            }
            existing.setBillNumber(updatedBill.getBillNumber());
        }

        if (updatedBill.getVendor() != null && updatedBill.getVendor().getId() != null) {
            Vendor vendor = vendorRepository.findById(updatedBill.getVendor().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Vendor tidak ditemukan"));
            existing.setVendor(vendor);
        }

        existing.setVendorInvoiceNumber(updatedBill.getVendorInvoiceNumber());
        existing.setBillDate(updatedBill.getBillDate());
        existing.setDueDate(updatedBill.getDueDate());
        existing.setNotes(updatedBill.getNotes());

        existing.getLines().clear();
        if (lines != null && !lines.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                BillLine line = lines.get(i);
                line.setBill(existing);
                line.setLineOrder(i);
                resolveLineReferences(line);
            }
            existing.getLines().addAll(lines);
        }
        existing.recalculateFromLines();

        return billRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Bill bill = findById(id);

        if (bill.getStatus() != BillStatus.DRAFT && bill.getStatus() != BillStatus.CANCELLED) {
            throw new IllegalStateException("Hanya tagihan draf atau dibatalkan yang dapat dihapus");
        }

        billRepository.delete(bill);
    }

    @Transactional
    public Bill approve(UUID id) {
        Bill bill = findById(id);

        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new IllegalStateException("Hanya tagihan draf yang dapat disetujui");
        }

        // Recognize expense & payable: one DRAFT transaction per distinct expense
        // account (R2) via the template engine. Accounting approves the DRAFT(s) to post.
        recognizeBillExpense(bill);

        bill.setStatus(BillStatus.APPROVED);
        bill.setApprovedAt(LocalDateTime.now());
        bill.setApprovedBy(CurrentUser.name());
        return billRepository.save(bill);
    }

    /**
     * Groups bill lines by their expense account and, per group, composes a DRAFT
     * transaction via the "Tagihan Vendor" template. Amounts come from the bill's own
     * line figures; the payable and input-tax accounts come from CompanyConfig. Throws
     * (no fallback) if any required account is unconfigured.
     */
    private void recognizeBillExpense(Bill bill) {
        CompanyConfig config = companyConfigService.getConfig();
        UUID payableAccountId = requireAccountId(config.getPayableAccount(),
                "Akun Hutang Usaha belum dikonfigurasi di Pengaturan Perusahaan");
        UUID inputTaxAccountId = requireAccountId(config.getInputTaxAccount(),
                "Akun PPN Masukan belum dikonfigurasi di Pengaturan Perusahaan");

        Map<UUID, ChartOfAccount> expenseAccounts = new LinkedHashMap<>();
        Map<UUID, BigDecimal> expenseByAccount = new LinkedHashMap<>();
        Map<UUID, BigDecimal> taxByAccount = new LinkedHashMap<>();

        for (BillLine line : bill.getLines()) {
            ChartOfAccount expenseAccount = line.getExpenseAccount();
            if (expenseAccount == null) {
                throw new IllegalStateException("Baris tagihan '" + line.getDescription()
                        + "' belum memiliki Akun Beban");
            }
            UUID key = expenseAccount.getId();
            expenseAccounts.putIfAbsent(key, expenseAccount);
            expenseByAccount.merge(key, nz(line.getAmount()), BigDecimal::add);
            taxByAccount.merge(key, nz(line.getTaxAmount()), BigDecimal::add);
        }

        for (Map.Entry<UUID, ChartOfAccount> group : expenseAccounts.entrySet()) {
            UUID expenseAccountId = group.getKey();
            BigDecimal expenseAmount = expenseByAccount.get(expenseAccountId);
            BigDecimal ppnAmount = taxByAccount.getOrDefault(expenseAccountId, BigDecimal.ZERO);
            BigDecimal apAmount = expenseAmount.add(ppnAmount);

            Map<String, UUID> hints = new HashMap<>();
            hints.put("BEBAN", expenseAccountId);
            hints.put("PPN_MASUKAN", inputTaxAccountId);
            hints.put("HUTANG", payableAccountId);

            Map<String, BigDecimal> variables = new HashMap<>();
            variables.put("expenseAmount", expenseAmount);
            variables.put("ppnAmount", ppnAmount);
            variables.put("apAmount", apAmount);

            documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                    .templateName("Tagihan Vendor")
                    .date(bill.getBillDate())
                    .description("Tagihan " + bill.getBillNumber() + " - " + group.getValue().getAccountName())
                    .amount(apAmount)
                    .hintToAccount(hints)
                    .variables(variables)
                    .createdBy("system")
                    .sourceDocumentType("BILL")
                    .sourceDocumentId(bill.getId())
                    .build());
        }
    }

    private UUID requireAccountId(ChartOfAccount account, String message) {
        if (account == null || account.getId() == null) {
            throw new IllegalStateException(message);
        }
        return account.getId();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Transactional
    public Bill markAsPaid(UUID id) {
        Bill bill = findById(id);

        if (bill.getStatus() != BillStatus.APPROVED && bill.getStatus() != BillStatus.OVERDUE
                && bill.getStatus() != BillStatus.PARTIAL) {
            throw new IllegalStateException("Hanya tagihan yang disetujui, jatuh tempo, atau sebagian yang dapat ditandai lunas");
        }

        bill.setStatus(BillStatus.PAID);
        bill.setPaidAt(LocalDateTime.now());
        return billRepository.save(bill);
    }

    @Transactional
    public Bill recordPayment(UUID billId, BillPayment payment) {
        Bill bill = findById(billId);

        if (bill.getStatus() != BillStatus.APPROVED && bill.getStatus() != BillStatus.OVERDUE
                && bill.getStatus() != BillStatus.PARTIAL) {
            throw new IllegalStateException("Pembayaran hanya bisa dicatat untuk tagihan berstatus Disetujui, Jatuh Tempo, atau Sebagian");
        }

        BigDecimal existingPayments = billPaymentRepository.sumPaymentsByBillId(billId);
        BigDecimal totalAfterPayment = existingPayments.add(payment.getAmount());
        BigDecimal totalAmount = bill.getTotalAmount();

        if (totalAfterPayment.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("Total pembayaran (Rp " + totalAfterPayment +
                    ") melebihi total tagihan (Rp " + totalAmount + ")");
        }

        payment.setBill(bill);
        billPaymentRepository.save(payment);

        if (totalAfterPayment.compareTo(totalAmount) == 0) {
            bill.setStatus(BillStatus.PAID);
            bill.setPaidAt(LocalDateTime.now());
        } else {
            bill.setStatus(BillStatus.PARTIAL);
        }

        return billRepository.save(bill);
    }

    public List<BillPayment> findPaymentsByBillId(UUID billId) {
        return billPaymentRepository.findByBillIdOrderByPaymentDateAsc(billId);
    }

    @Transactional
    public Bill cancel(UUID id) {
        Bill bill = findById(id);

        if (bill.getStatus() == BillStatus.PAID) {
            throw new IllegalStateException("Tagihan lunas tidak dapat dibatalkan");
        }

        bill.setStatus(BillStatus.CANCELLED);
        return billRepository.save(bill);
    }

    @Transactional
    public int updateOverdueBills() {
        List<Bill> overdueBills = billRepository.findOverdueBills(LocalDate.now());
        int count = 0;
        for (Bill bill : overdueBills) {
            bill.setStatus(BillStatus.OVERDUE);
            billRepository.save(bill);
            count++;
        }
        return count;
    }

    private String generateBillNumber() {
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix = "BILL-" + yearMonth + "-";
        Integer maxSeq = billRepository.findMaxSequenceByPrefix(prefix + "%");
        int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + String.format("%04d", nextSeq);
    }

    private void resolveLineReferences(BillLine line) {
        if (line.getExpenseAccount() != null && line.getExpenseAccount().getId() != null) {
            ChartOfAccount account = chartOfAccountRepository.findById(line.getExpenseAccount().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Akun beban tidak ditemukan"));
            line.setExpenseAccount(account);
        } else {
            line.setExpenseAccount(null);
        }

        if (line.getProduct() != null && line.getProduct().getId() != null) {
            Product product = productRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Produk tidak ditemukan"));
            line.setProduct(product);
        } else {
            line.setProduct(null);
        }
    }
}
