package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("Customer/Vendor Statements - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class StatementTest extends PlaywrightTestBase {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private BillService billService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private VendorService vendorService;

    @Autowired
    private VendorRepository vendorRepository;

    private Client testClient;
    private Vendor testVendor;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));

        try {
            testClient = clientRepository.findByCode("MANDIRI").orElseThrow();

            // Create invoice, send, and record payment for client statement
            Invoice invoice = new Invoice();
            invoice.setClient(testClient);
            invoice.setInvoiceDate(LocalDate.of(2026, 2, 10));
            invoice.setDueDate(LocalDate.of(2026, 3, 10));
            invoice.setAmount(new BigDecimal("8000000"));

            InvoiceLine line = new InvoiceLine();
            line.setDescription("Jasa Konsultasi Statement Test");
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(new BigDecimal("8000000"));
            line.calculateAmounts();

            Invoice saved = invoiceService.create(invoice, List.of(line));
            invoiceService.send(saved.getId());

            // Record a payment
            InvoicePayment payment = new InvoicePayment();
            payment.setPaymentDate(LocalDate.of(2026, 2, 20));
            payment.setAmount(new BigDecimal("3000000"));
            payment.setPaymentMethod(PaymentMethod.TRANSFER);
            payment.setReferenceNumber("TRF-STMT-001");
            invoiceService.recordPayment(saved.getId(), payment);

            // Create vendor and bill
            String vendorCode = "VND-STMT-TEST";
            var existing = vendorRepository.findByCode(vendorCode);
            if (existing.isPresent()) {
                testVendor = existing.get();
            } else {
                testVendor = new Vendor();
                testVendor.setCode(vendorCode);
                testVendor.setName("PT Vendor Statement Test");
                testVendor.setActive(true);
                testVendor = vendorService.create(testVendor);
            }

            Bill bill = new Bill();
            bill.setVendor(testVendor);
            bill.setBillDate(LocalDate.of(2026, 2, 12));
            bill.setDueDate(LocalDate.of(2026, 3, 12));
            bill.setAmount(new BigDecimal("4000000"));

            BillLine billLine = new BillLine();
            billLine.setDescription("Pembelian Peralatan Statement Test");
            billLine.setQuantity(BigDecimal.ONE);
            billLine.setUnitPrice(new BigDecimal("4000000"));
            billLine.calculateAmounts();

            Bill savedBill = billService.create(bill, List.of(billLine));
            billService.approve(savedBill.getId());
        } finally {
            SecurityContextHolder.clearContext();
        }

        loginAsAdmin();
    }

    @Test
    @DisplayName("Client statement page loads with statement table")
    void shouldDisplayClientStatementPage() {
        navigateTo("/statements/client/MANDIRI?dateFrom=2026-02-01&dateTo=2026-02-28");
        waitForPageLoad();

        assertThat(page.locator("[data-testid='client-statement']")).isVisible();
        assertThat(page.locator("[data-testid='statement-table']")).isVisible();
        assertThat(page.locator("[data-testid='opening-balance']")).isVisible();
        assertThat(page.locator("[data-testid='closing-balance']")).isVisible();

        // Client code should appear (name may be modified by other tests)
        String pageContent = page.content();
        assertThat(pageContent).contains("MANDIRI");

        log.info("Client statement page loaded");
    }

    @Test
    @DisplayName("Client statement shows invoice and payment entries")
    void shouldShowInvoiceAndPaymentEntries() {
        navigateTo("/statements/client/MANDIRI?dateFrom=2026-02-01&dateTo=2026-02-28");
        waitForPageLoad();

        // Should show invoice entry (type badge and invoice number)
        String pageContent = page.content();
        assertThat(pageContent).contains("Invoice");
        assertThat(pageContent).contains("INV-");

        // Should show payment entry with reference number
        assertThat(pageContent).contains("Pembayaran");
        assertThat(pageContent).contains("TRF-STMT-001");

        log.info("Client statement entries verified");
    }

    @Test
    @DisplayName("Client statement with date range filter")
    void shouldFilterByDateRange() {
        // Use a date range that excludes our test data
        navigateTo("/statements/client/MANDIRI?dateFrom=2025-01-01&dateTo=2025-01-31");
        waitForPageLoad();

        assertThat(page.locator("[data-testid='client-statement']")).isVisible();

        // Either no-data message or empty entries
        // The opening/closing balances should still be visible
        assertThat(page.locator("[data-testid='opening-balance']")).isVisible();
        assertThat(page.locator("[data-testid='closing-balance']")).isVisible();

        log.info("Client statement date filter works");
    }

    @Test
    @DisplayName("Vendor statement page loads with statement table")
    void shouldDisplayVendorStatementPage() {
        navigateTo("/statements/vendor/" + testVendor.getCode() + "?dateFrom=2026-02-01&dateTo=2026-02-28");
        waitForPageLoad();

        assertThat(page.locator("[data-testid='vendor-statement']")).isVisible();
        assertThat(page.locator("[data-testid='statement-table']")).isVisible();
        assertThat(page.locator("[data-testid='opening-balance']")).isVisible();
        assertThat(page.locator("[data-testid='closing-balance']")).isVisible();

        // Vendor name should appear
        String pageContent = page.content();
        assertThat(pageContent).contains("PT Vendor Statement Test");

        log.info("Vendor statement page loaded");
    }

    @Test
    @DisplayName("Client statement print page renders correctly")
    void shouldDisplayClientStatementPrintPage() {
        navigateTo("/statements/client/MANDIRI/print?dateFrom=2026-02-01&dateTo=2026-02-28");
        waitForPageLoad();

        String pageContent = page.content();
        assertThat(pageContent).contains("MANDIRI");
        assertThat(pageContent).contains("Laporan Piutang");
        assertThat(pageContent).contains("Saldo Awal");
        assertThat(pageContent).contains("Saldo Akhir");

        log.info("Client statement print page loaded");
    }

    @Test
    @DisplayName("Vendor statement print page renders correctly")
    void shouldDisplayVendorStatementPrintPage() {
        navigateTo("/statements/vendor/" + testVendor.getCode() + "/print?dateFrom=2026-02-01&dateTo=2026-02-28");
        waitForPageLoad();

        String pageContent = page.content();
        assertThat(pageContent).contains("PT Vendor Statement Test");
        assertThat(pageContent).contains("Laporan Hutang");
        assertThat(pageContent).contains("Saldo Awal");
        assertThat(pageContent).contains("Saldo Akhir");

        log.info("Vendor statement print page loaded");
    }

    @Test
    @DisplayName("Client detail page has Lihat Laporan link")
    void shouldHaveStatementLinkOnClientDetail() {
        navigateTo("/clients/MANDIRI");
        waitForPageLoad();

        assertThat(page.locator("[data-testid='view-statement']")).isVisible();
        String href = page.locator("[data-testid='view-statement']").getAttribute("href");
        assertThat(href).contains("/statements/client/MANDIRI");

        log.info("Client detail has statement link");
    }
}
