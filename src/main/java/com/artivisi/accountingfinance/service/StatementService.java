package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.AccountStatement;
import com.artivisi.accountingfinance.dto.StatementEntry;
import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillPayment;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import com.artivisi.accountingfinance.repository.BillPaymentRepository;
import com.artivisi.accountingfinance.repository.BillRepository;
import com.artivisi.accountingfinance.repository.InvoicePaymentRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatementService {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final ChartOfAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    /** One posted journal line on a current account, with the balance after it. */
    public record CurrentAccountLine(LocalDate date, UUID transactionId, String transactionNumber, String description,
                                     BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    /**
     * Statement of a SHAREHOLDER_CURRENT account. Balances are credit minus debit: positive means the company owes
     * the shareholder or director, negative means the shareholder or director owes the company.
     */
    public record CurrentAccountStatement(UUID accountId, String accountCode, String accountName, LocalDate dateFrom,
                                          LocalDate dateTo, BigDecimal openingBalance, List<CurrentAccountLine> lines,
                                          BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closingBalance) {}

    public List<ChartOfAccount> shareholderAccounts() {
        return accountRepository.findByCloseCheckRoleOrderByAccountCodeAsc(CloseCheckRole.SHAREHOLDER_CURRENT);
    }

    public CurrentAccountStatement generateShareholderStatement(UUID accountId, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("Tanggal awal dan akhir wajib diisi");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("Tanggal akhir sebelum tanggal awal");
        }
        ChartOfAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Akun tidak ditemukan: " + accountId));
        if (account.getCloseCheckRole() != CloseCheckRole.SHAREHOLDER_CURRENT) {
            throw new IllegalArgumentException("Akun " + account.getAccountCode()
                    + " bukan rekening koran pemegang saham/direksi");
        }
        BigDecimal opening = journalEntryRepository.sumCreditBeforeDate(accountId, dateFrom)
                .subtract(journalEntryRepository.sumDebitBeforeDate(accountId, dateFrom));
        BigDecimal balance = opening;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<CurrentAccountLine> lines = new ArrayList<>();
        for (JournalEntry j : journalEntryRepository.findPostedLinesOnAccount(accountId, dateFrom, dateTo)) {
            BigDecimal debit = j.getDebitAmount();
            BigDecimal credit = j.getCreditAmount();
            balance = balance.add(credit).subtract(debit);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            lines.add(new CurrentAccountLine(j.getTransaction().getTransactionDate(), j.getTransaction().getId(),
                    j.getTransaction().getTransactionNumber(), j.getTransaction().getDescription(), debit, credit, balance));
        }
        return new CurrentAccountStatement(account.getId(), account.getAccountCode(), account.getAccountName(),
                dateFrom, dateTo, opening, lines, totalDebit, totalCredit, balance);
    }

    public AccountStatement generateClientStatement(UUID clientId, String clientCode, String clientName,
                                                      LocalDate dateFrom, LocalDate dateTo) {
        // Opening balance = invoices before dateFrom - payments before dateFrom
        BigDecimal invoicesBefore = invoiceRepository.sumInvoicesBeforeDate(clientId, dateFrom);
        BigDecimal paymentsBefore = invoicePaymentRepository.sumPaymentsBeforeDate(clientId, dateFrom);
        BigDecimal openingBalance = invoicesBefore.subtract(paymentsBefore);

        // Get invoices and payments in date range
        List<Invoice> invoices = invoiceRepository.findByClientIdAndDateRange(clientId, dateFrom, dateTo);
        List<InvoicePayment> payments = invoicePaymentRepository.findByClientIdAndDateRange(clientId, dateFrom, dateTo);

        // Merge into chronological entries
        List<StatementEntry> entries = new ArrayList<>();
        int invIdx = 0;
        int pmtIdx = 0;
        BigDecimal balance = openingBalance;

        while (invIdx < invoices.size() || pmtIdx < payments.size()) {
            boolean useInvoice;
            if (invIdx >= invoices.size()) {
                useInvoice = false;
            } else if (pmtIdx >= payments.size()) {
                useInvoice = true;
            } else {
                LocalDate invDate = invoices.get(invIdx).getInvoiceDate();
                LocalDate pmtDate = payments.get(pmtIdx).getPaymentDate();
                useInvoice = !invDate.isAfter(pmtDate);
            }

            if (useInvoice) {
                Invoice inv = invoices.get(invIdx++);
                BigDecimal amount = inv.getTotalAmount();
                balance = balance.add(amount);
                entries.add(new StatementEntry(
                        inv.getInvoiceDate(),
                        "INVOICE",
                        inv.getInvoiceNumber(),
                        "Invoice " + inv.getInvoiceNumber(),
                        amount,
                        BigDecimal.ZERO,
                        balance
                ));
            } else {
                InvoicePayment pmt = payments.get(pmtIdx++);
                BigDecimal amount = pmt.getAmount();
                balance = balance.subtract(amount);
                entries.add(new StatementEntry(
                        pmt.getPaymentDate(),
                        "PAYMENT",
                        pmt.getReferenceNumber() != null ? pmt.getReferenceNumber() : "-",
                        "Pembayaran " + pmt.getInvoice().getInvoiceNumber(),
                        BigDecimal.ZERO,
                        amount,
                        balance
                ));
            }
        }

        return new AccountStatement("CLIENT", clientCode, clientName,
                dateFrom, dateTo, openingBalance, entries, balance);
    }

    public AccountStatement generateVendorStatement(UUID vendorId, String vendorCode, String vendorName,
                                                      LocalDate dateFrom, LocalDate dateTo) {
        BigDecimal billsBefore = billRepository.sumBillsBeforeDate(vendorId, dateFrom);
        BigDecimal paymentsBefore = billPaymentRepository.sumPaymentsBeforeDate(vendorId, dateFrom);
        BigDecimal openingBalance = billsBefore.subtract(paymentsBefore);

        List<Bill> bills = billRepository.findByVendorIdAndDateRange(vendorId, dateFrom, dateTo);
        List<BillPayment> payments = billPaymentRepository.findByVendorIdAndDateRange(vendorId, dateFrom, dateTo);

        List<StatementEntry> entries = new ArrayList<>();
        int billIdx = 0;
        int pmtIdx = 0;
        BigDecimal balance = openingBalance;

        while (billIdx < bills.size() || pmtIdx < payments.size()) {
            boolean useBill;
            if (billIdx >= bills.size()) {
                useBill = false;
            } else if (pmtIdx >= payments.size()) {
                useBill = true;
            } else {
                LocalDate billDate = bills.get(billIdx).getBillDate();
                LocalDate pmtDate = payments.get(pmtIdx).getPaymentDate();
                useBill = !billDate.isAfter(pmtDate);
            }

            if (useBill) {
                Bill bill = bills.get(billIdx++);
                BigDecimal amount = bill.getTotalAmount();
                balance = balance.add(amount);
                entries.add(new StatementEntry(
                        bill.getBillDate(),
                        "BILL",
                        bill.getBillNumber(),
                        "Tagihan " + bill.getBillNumber(),
                        amount,
                        BigDecimal.ZERO,
                        balance
                ));
            } else {
                BillPayment pmt = payments.get(pmtIdx++);
                BigDecimal amount = pmt.getAmount();
                balance = balance.subtract(amount);
                entries.add(new StatementEntry(
                        pmt.getPaymentDate(),
                        "PAYMENT",
                        pmt.getReferenceNumber() != null ? pmt.getReferenceNumber() : "-",
                        "Pembayaran " + pmt.getBill().getBillNumber(),
                        BigDecimal.ZERO,
                        amount,
                        balance
                ));
            }
        }

        return new AccountStatement("VENDOR", vendorCode, vendorName,
                dateFrom, dateTo, openingBalance, entries, balance);
    }
}
