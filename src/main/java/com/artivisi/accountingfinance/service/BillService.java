package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.repository.BillRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillService {

    private final BillRepository billRepository;
    private final VendorRepository vendorRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ProductRepository productRepository;

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

        bill.setStatus(BillStatus.APPROVED);
        bill.setApprovedAt(LocalDateTime.now());
        bill.setApprovedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        return billRepository.save(bill);
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
