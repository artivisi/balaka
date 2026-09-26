package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.BillPayment;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.VendorService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Creates the clients, vendors, invoices, bills and payments that receivable/payable tests
 * (payments, aging, statements) assert on. Every record gets a unique code so tests never
 * depend on or change seeded data.
 */
class ArApFixture {

    private static final String SALES_ACCOUNT = "4.1.01";
    private static final String EXPENSE_ACCOUNT = "5.1.20";

    private final ClientService clientService;
    private final VendorService vendorService;
    private final InvoiceService invoiceService;
    private final BillService billService;
    private final ProductRepository productRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private Product service;

    ArApFixture(ClientService clientService, VendorService vendorService, InvoiceService invoiceService,
                BillService billService, ProductRepository productRepository,
                ChartOfAccountRepository chartOfAccountRepository) {
        this.clientService = clientService;
        this.vendorService = vendorService;
        this.invoiceService = invoiceService;
        this.billService = billService;
        this.productRepository = productRepository;
        this.chartOfAccountRepository = chartOfAccountRepository;
    }

    Client client(String name) {
        Client client = new Client();
        client.setCode("AR-" + shortId());
        client.setName(name + " " + client.getCode());
        return clientService.create(client);
    }

    Vendor vendor(String name) {
        Vendor vendor = new Vendor();
        vendor.setCode("AP-" + shortId());
        vendor.setName(name + " " + vendor.getCode());
        return vendorService.create(vendor);
    }

    Invoice draftInvoice(Client client, LocalDate invoiceDate, LocalDate dueDate, String amount, String taxRate) {
        InvoiceLine line = new InvoiceLine();
        line.setProduct(service());
        line.setDescription("Jasa " + client.getCode());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(amount));
        line.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
        line.calculateAmounts();

        Invoice invoice = new Invoice();
        invoice.setClient(client);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(dueDate);
        invoice.setAmount(new BigDecimal(amount));
        return invoiceService.create(invoice, List.of(line));
    }

    Invoice sentInvoice(Client client, LocalDate invoiceDate, LocalDate dueDate, String amount, String taxRate) {
        Invoice invoice = draftInvoice(client, invoiceDate, dueDate, amount, taxRate);
        return asAdmin(() -> invoiceService.send(invoice.getId()));
    }

    Invoice payInvoice(Invoice invoice, LocalDate date, String amount, String reference) {
        InvoicePayment payment = new InvoicePayment();
        payment.setPaymentDate(date);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMethod(PaymentMethod.TRANSFER);
        payment.setReferenceNumber(reference);
        return invoiceService.recordPayment(invoice.getId(), payment);
    }

    Bill draftBill(Vendor vendor, LocalDate billDate, LocalDate dueDate, String amount, String taxRate) {
        BillLine line = new BillLine();
        line.setExpenseAccount(chartOfAccountRepository.findByAccountCode(EXPENSE_ACCOUNT)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + EXPENSE_ACCOUNT)));
        line.setDescription("Beban " + vendor.getCode());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(amount));
        line.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));

        Bill bill = new Bill();
        bill.setVendor(vendor);
        bill.setBillDate(billDate);
        bill.setDueDate(dueDate);
        return billService.create(bill, List.of(line));
    }

    Bill approvedBill(Vendor vendor, LocalDate billDate, LocalDate dueDate, String amount, String taxRate) {
        Bill bill = draftBill(vendor, billDate, dueDate, amount, taxRate);
        return asAdmin(() -> billService.approve(bill.getId()));
    }

    Bill payBill(Bill bill, LocalDate date, String amount, String reference) {
        BillPayment payment = new BillPayment();
        payment.setPaymentDate(date);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMethod(PaymentMethod.TRANSFER);
        payment.setReferenceNumber(reference);
        return billService.recordPayment(bill.getId(), payment);
    }

    private Product service() {
        if (service == null) {
            Product product = new Product();
            product.setCode("SVC-AR-" + shortId());
            product.setName("Jasa Konsultasi");
            product.setUnit("paket");
            product.setTrackInventory(false);
            product.setSalesAccount(chartOfAccountRepository.findByAccountCode(SALES_ACCOUNT)
                    .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + SALES_ACCOUNT)));
            service = productRepository.save(product);
        }
        return service;
    }

    private static <T> T asAdmin(Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
