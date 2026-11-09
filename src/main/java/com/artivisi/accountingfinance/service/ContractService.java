package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Contract;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.ContractRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contract register: entries, their file, and links to invoices and transactions. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContractService {

    private final ContractRepository contractRepository;
    private final ClientRepository clientRepository;
    private final VendorRepository vendorRepository;
    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final DocumentService documentService;

    public record ContractData(String contractNumber, String title, UUID clientId, UUID vendorId, LocalDate startDate,
                               LocalDate endDate, BigDecimal contractValue, String notes) {}

    @Transactional(readOnly = true)
    public Contract findById(UUID id) {
        return contractRepository.findWithParties(id)
                .orElseThrow(() -> new EntityNotFoundException("Kontrak tidak ditemukan: " + id));
    }

    @Transactional(readOnly = true)
    public List<Invoice> linkedInvoices(UUID contractId) {
        return invoiceRepository.findByContractId(contractId);
    }

    @Transactional(readOnly = true)
    public List<Transaction> linkedTransactions(UUID contractId) {
        return transactionRepository.findByContractId(contractId);
    }

    public Contract create(ContractData data, String username) {
        String number = requireText(data.contractNumber(), "Nomor kontrak wajib diisi");
        if (contractRepository.existsByContractNumber(number)) {
            throw new IllegalStateException("Nomor kontrak " + number + " sudah terdaftar");
        }
        Contract contract = new Contract();
        contract.setCreatedBy(username);
        apply(contract, data, number);
        Contract saved = contractRepository.save(contract);
        log.info("Contract {} registered", com.artivisi.accountingfinance.security.LogSanitizer.sanitize(saved.getContractNumber()));
        return saved;
    }

    public Contract update(UUID id, ContractData data) {
        Contract contract = findById(id);
        String number = requireText(data.contractNumber(), "Nomor kontrak wajib diisi");
        if (contractRepository.existsByContractNumberAndIdNot(number, id)) {
            throw new IllegalStateException("Nomor kontrak " + number + " sudah terdaftar");
        }
        boolean counterpartyChanged = !java.util.Objects.equals(data.clientId(), contract.getClient() != null ? contract.getClient().getId() : null)
                || !java.util.Objects.equals(data.vendorId(), contract.getVendor() != null ? contract.getVendor().getId() : null);
        if (counterpartyChanged && !invoiceRepository.findByContractId(id).isEmpty()) {
            throw new IllegalStateException("Pihak kontrak tidak dapat diubah selama ada invoice yang tertaut");
        }
        apply(contract, data, number);
        return contractRepository.save(contract);
    }

    /** Stores the contract file. A replaced file stays stored and keeps its history in the documents table. */
    public Contract attachFile(UUID id, MultipartFile file, String username) throws IOException {
        Contract contract = findById(id);
        Document document = documentService.upload(file, username);
        document.setEvidenceType(EvidenceType.CONTRACT);
        contract.setDocument(document);
        return contractRepository.save(contract);
    }

    public void linkInvoice(UUID contractId, String invoiceNumber) {
        Contract contract = findById(contractId);
        if (contract.getClient() == null) {
            throw new IllegalStateException("Invoice hanya dapat ditautkan ke kontrak klien");
        }
        Invoice invoice = invoiceRepository.findByInvoiceNumber(requireText(invoiceNumber, "Nomor invoice wajib diisi"))
                .orElseThrow(() -> new EntityNotFoundException("Invoice " + invoiceNumber.trim() + " tidak ditemukan"));
        if (!invoice.getClient().getId().equals(contract.getClient().getId())) {
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " milik klien lain");
        }
        if (invoice.getContract() != null && !invoice.getContract().getId().equals(contractId)) {
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " sudah tertaut ke kontrak "
                    + invoice.getContract().getContractNumber());
        }
        invoice.setContract(contract);
        invoiceRepository.save(invoice);
    }

    public void unlinkInvoice(UUID contractId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice tidak ditemukan: " + invoiceId));
        if (invoice.getContract() == null || !invoice.getContract().getId().equals(contractId)) {
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " tidak tertaut ke kontrak ini");
        }
        invoice.setContract(null);
        invoiceRepository.save(invoice);
    }

    public void linkTransaction(UUID contractId, String transactionNumber) {
        Contract contract = findById(contractId);
        Transaction transaction = transactionRepository.findByTransactionNumber(
                        requireText(transactionNumber, "Nomor transaksi wajib diisi"))
                .orElseThrow(() -> new EntityNotFoundException("Transaksi " + transactionNumber.trim() + " tidak ditemukan"));
        if (transaction.getContract() != null && !transaction.getContract().getId().equals(contractId)) {
            throw new IllegalStateException("Transaksi " + transaction.getTransactionNumber() + " sudah tertaut ke kontrak "
                    + transaction.getContract().getContractNumber());
        }
        transaction.setContract(contract);
        transactionRepository.save(transaction);
    }

    public void unlinkTransaction(UUID contractId, UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaksi tidak ditemukan: " + transactionId));
        if (transaction.getContract() == null || !transaction.getContract().getId().equals(contractId)) {
            throw new IllegalStateException("Transaksi " + transaction.getTransactionNumber() + " tidak tertaut ke kontrak ini");
        }
        transaction.setContract(null);
        transactionRepository.save(transaction);
    }

    private void apply(Contract contract, ContractData data, String number) {
        if ((data.clientId() == null) == (data.vendorId() == null)) {
            throw new IllegalArgumentException("Pilih tepat satu pihak: klien atau vendor");
        }
        if (data.startDate() == null) {
            throw new IllegalArgumentException("Tanggal mulai wajib diisi");
        }
        if (data.endDate() != null && data.endDate().isBefore(data.startDate())) {
            throw new IllegalArgumentException("Tanggal berakhir sebelum tanggal mulai");
        }
        if (data.contractValue() != null && data.contractValue().signum() < 0) {
            throw new IllegalArgumentException("Nilai kontrak tidak boleh negatif");
        }
        contract.setContractNumber(number);
        contract.setTitle(requireText(data.title(), "Judul kontrak wajib diisi"));
        contract.setClient(data.clientId() == null ? null : clientRepository.findById(data.clientId())
                .orElseThrow(() -> new EntityNotFoundException("Klien tidak ditemukan: " + data.clientId())));
        contract.setVendor(data.vendorId() == null ? null : vendorRepository.findById(data.vendorId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor tidak ditemukan: " + data.vendorId())));
        contract.setStartDate(data.startDate());
        contract.setEndDate(data.endDate());
        contract.setContractValue(data.contractValue());
        contract.setNotes(data.notes() == null || data.notes().isBlank() ? null : data.notes().trim());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
