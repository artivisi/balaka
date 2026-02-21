package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    List<InvoiceLine> findByInvoiceIdOrderByLineOrder(UUID invoiceId);

    void deleteByInvoiceId(UUID invoiceId);
}
