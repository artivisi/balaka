package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.InvoicePaymentRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records a faktur and a customer's bupot against an invoice. Both become {@link TaxTransactionDetail} rows, so the
 * reports and exports that read that table see them. A bupot composes a DRAFT transaction: nothing reaches the ledger
 * until it is posted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceTaxLinkService {

    public static final String BUPOT_TEMPLATE = "Pemotongan PPh 23 oleh Pelanggan";
    private static final String INVOICE_SOURCE = "INVOICE";

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final TaxTransactionDetailService taxDetailService;
    private final DocumentPostingService documentPostingService;
    private final CompanyConfigService companyConfigService;
    private final WithholdingAccountResolver accountResolver;

    @Builder
    public record RecordFaktur(UUID invoiceId, String fakturNumber, LocalDate fakturDate, String transactionCode,
                               BigDecimal dpp, BigDecimal ppn) {}

    /** paymentId is optional: a receipt booked through the mark-paid screen has no payment row. */
    @Builder
    public record RecordBupot(UUID invoiceId, UUID paymentId, String bupotNumber, LocalDate bupotDate, String taxObjectCode,
                              BigDecimal grossAmount, BigDecimal taxRate, BigDecimal taxAmount, String createdBy) {}

    /**
     * Attaches the faktur to the invoice's first non-void revenue transaction. An invoice with several revenue
     * transactions still has one faktur. A second faktur through this path is refused.
     */
    public TaxTransactionDetail recordFaktur(RecordFaktur command) {
        if (isBlank(command.fakturNumber()) || command.fakturDate() == null || isBlank(command.transactionCode())
                || command.dpp() == null || command.ppn() == null) {
            throw new IllegalArgumentException("Nomor faktur, tanggal faktur, kode transaksi, DPP dan PPN wajib diisi");
        }
        Invoice invoice = issuedInvoice(command.invoiceId());
        List<Transaction> transactions = transactionRepository
                .findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(INVOICE_SOURCE, invoice.getId()).stream()
                .filter(t -> t.getStatus() != TransactionStatus.VOID)
                .toList();
        if (transactions.isEmpty()) {
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " tidak punya transaksi pendapatan yang aktif");
        }
        for (Transaction transaction : transactions) {
            for (TaxTransactionDetail existing : taxDetailService.findByTransactionId(transaction.getId())) {
                if (existing.getTaxType() == TaxType.PPN_KELUARAN && !isBlank(existing.getFakturNumber())) {
                    throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " sudah punya faktur "
                            + existing.getFakturNumber() + ". Perbaiki detail pajak pada transaksi " + transaction.getTransactionNumber());
                }
            }
        }

        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(TaxType.PPN_KELUARAN);
        detail.setFakturNumber(command.fakturNumber().trim());
        detail.setFakturDate(command.fakturDate());
        detail.setTransactionCode(command.transactionCode());
        detail.setDpp(command.dpp());
        detail.setPpn(command.ppn());
        applyCounterparty(detail, invoice.getClient());
        return taxDetailService.save(transactions.getFirst().getId(), detail);
    }

    /**
     * Composes the DRAFT Dr Kredit Pajak PPh 23, Cr Piutang and attaches the bupot to it. The transaction date is the
     * bupot date, which decides the masa of the PPh 23 credit.
     */
    public TaxTransactionDetail recordBupot(RecordBupot command) {
        if (isBlank(command.bupotNumber()) || command.bupotDate() == null || isBlank(command.taxObjectCode())
                || command.grossAmount() == null || command.taxRate() == null || command.taxAmount() == null) {
            throw new IllegalArgumentException("Nomor bukti potong, tanggal, kode objek pajak, bruto, tarif dan PPh wajib diisi");
        }
        if (command.taxAmount().signum() <= 0) {
            throw new IllegalArgumentException("PPh pada bukti potong harus lebih dari nol");
        }
        Invoice invoice = issuedInvoice(command.invoiceId());
        InvoicePayment payment = null;
        if (command.paymentId() != null) {
            payment = paymentRepository.findById(command.paymentId())
                    .orElseThrow(() -> new EntityNotFoundException("Pembayaran tidak ditemukan: " + command.paymentId()));
            if (!payment.getInvoice().getId().equals(invoice.getId())) {
                throw new IllegalArgumentException("Pembayaran bukan milik invoice " + invoice.getInvoiceNumber());
            }
        }

        ChartOfAccount receivable = companyConfigService.getConfig().getReceivableAccount();
        if (receivable == null) {
            throw new IllegalStateException("Akun Piutang Usaha belum dikonfigurasi di Pengaturan Perusahaan");
        }
        Transaction transaction = documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName(BUPOT_TEMPLATE)
                .date(command.bupotDate())
                .description("Bukti potong " + command.bupotNumber().trim() + " - Invoice " + invoice.getInvoiceNumber())
                .amount(command.taxAmount())
                .hintToAccount(Map.of("KREDIT_PPH23", accountResolver.taxCreditPph23Account().getId(), "PIUTANG", receivable.getId()))
                .createdBy(command.createdBy())
                .sourceDocumentType(INVOICE_SOURCE)
                .sourceDocumentId(invoice.getId())
                .build());

        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(TaxType.PPH_23);
        detail.setBupotNumber(command.bupotNumber().trim());
        detail.setTaxObjectCode(command.taxObjectCode());
        detail.setGrossAmount(command.grossAmount());
        detail.setTaxRate(command.taxRate());
        detail.setTaxAmount(command.taxAmount());
        detail.setInvoicePayment(payment);
        applyCounterparty(detail, invoice.getClient());
        return taxDetailService.save(transaction.getId(), detail);
    }

    private Invoice issuedInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice tidak ditemukan: " + invoiceId));
        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " berstatus "
                    + invoice.getStatus().getIndonesianName() + ": faktur dan bukti potong hanya untuk invoice terkirim");
        }
        return invoice;
    }

    private void applyCounterparty(TaxTransactionDetail detail, Client client) {
        detail.setCounterpartyName(client.getName());
        detail.setCounterpartyNpwp(client.getNpwp());
        detail.setCounterpartyNitku(client.getNitku());
        detail.setCounterpartyNik(client.getNik());
        detail.setCounterpartyIdType(client.getIdType());
        detail.setCounterpartyAddress(client.getAddress());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
