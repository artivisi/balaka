package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end receivable and payable lifecycles as the user manual shows them (screenshots
 * 10-*): invoice send, partial and final payment with the receivables aging and client statement
 * after each step; bill approval, payment, payables aging and vendor statement.
 *
 * Documents are dated in 2091 for a client/vendor created by the test; aging is viewed as of
 * 15 March 2091 so the invoice (due 1 March) and bill (due 7 March) fall in the 1-30 days bucket.
 */
@DisplayName("Invoice & Bill Lifecycle - E2E Test with Screenshots")
@Import(ServiceTestDataInitializer.class)
class InvoiceLifecycleTest extends PlaywrightTestBase {

    private static final int YEAR = 2091;
    private static final String AGING_AS_OF = YEAR + "-03-15";

    @Autowired private InvoiceService invoiceService;
    @Autowired private BillService billService;
    @Autowired private ClientService clientService;
    @Autowired private VendorService vendorService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ChartOfAccountRepository chartOfAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ArApFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ArApFixture(clientService, vendorService, invoiceService, billService,
                productRepository, chartOfAccountRepository);
        loginAsAdmin();
    }

    @Test
    @DisplayName("Invoice lifecycle: send -> partial payment -> full payment, with aging and statement after each step")
    void invoiceFullLifecycle() {
        Client client = fixture.client("PT Klien Siklus");
        Invoice invoice = fixture.draftInvoice(client, LocalDate.of(YEAR, 2, 1), LocalDate.of(YEAR, 3, 1),
                "15000000", null);
        String detail = "/invoices/" + invoice.getInvoiceNumber();

        navigateTo(detail);
        waitForPageLoad();
        assertThat(page.getByTestId("invoice-status")).hasText("Draf");
        assertThat(page.getByTestId("payment-form-section")).hasCount(0);
        takeManualScreenshot("10-invoice-created");

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-send button[type='submit']").click();
        waitForPageLoad();
        assertThat(page.getByTestId("invoice-status")).hasText("Terkirim");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 15,000,000");
        assertThat(invoiceService.findById(invoice.getId()).getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(jdbcTemplate.queryForList("""
                SELECT status || '|' || transaction_date || '|' || amount FROM transactions
                WHERE source_document_type = 'INVOICE' AND source_document_id = ?""", String.class, invoice.getId()))
                .containsExactly("DRAFT|" + YEAR + "-02-01|15000000.00");
        takeManualScreenshot("10-invoice-sent");

        openAging("receivables");
        assertAgingRow(client.getCode(), "Rp 15,000,000");
        takeManualScreenshot("10-aging-receivables-unpaid");

        navigateTo(detail);
        waitForPageLoad();
        pay(YEAR + "-02-15", "5000000", "TRF-PARTIAL-001");
        assertThat(page.getByTestId("invoice-status")).hasText("Sebagian");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 10,000,000");
        takeManualScreenshot("10-invoice-partial-payment");

        openAging("receivables");
        assertAgingRow(client.getCode(), "Rp 10,000,000");
        takeManualScreenshot("10-aging-receivables-partial");

        navigateTo(detail);
        waitForPageLoad();
        pay(YEAR + "-02-20", "10000000", "TRF-FINAL-001");
        assertThat(page.getByTestId("invoice-status")).hasText("Lunas");
        assertThat(page.getByTestId("payment-history").locator("tbody tr")).hasCount(2);
        assertThat(invoiceService.findById(invoice.getId()).getStatus()).isEqualTo(InvoiceStatus.PAID);
        takeManualScreenshot("10-invoice-paid");

        openAging("receivables");
        assertThat(page.locator("[data-testid='aging-row-" + client.getCode() + "']")).hasCount(0);
        takeManualScreenshot("10-aging-receivables-cleared");

        navigateTo("/statements/client/" + client.getCode() + "?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='opening-balance'] td").last()).hasText("Rp 0");
        assertThat(statementBalances()).hasText(new String[] {"Rp 15,000,000", "Rp 10,000,000", "Rp 0"});
        assertThat(page.locator("[data-testid='closing-balance'] td").last()).hasText("Rp 0");
        takeManualScreenshot("10-client-statement");
    }

    @Test
    @DisplayName("Bill lifecycle: approve -> payables aging -> payment -> vendor statement")
    void billFullLifecycle() {
        Vendor vendor = fixture.vendor("PT Cloud Hosting Indonesia");
        Bill bill = fixture.draftBill(vendor, LocalDate.of(YEAR, 2, 5), LocalDate.of(YEAR, 3, 7), "6000000", null);
        String detail = "/bills/" + bill.getBillNumber();

        navigateTo(detail);
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-approve button").click();
        waitForPageLoad();
        assertThat(page.getByTestId("bill-status")).hasText("Disetujui");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 6,000,000");
        takeManualScreenshot("10-bill-approved");

        openAging("payables");
        assertAgingRow(vendor.getCode(), "Rp 6,000,000");
        takeManualScreenshot("10-aging-payables-unpaid");

        navigateTo(detail);
        waitForPageLoad();
        pay(YEAR + "-02-25", "6000000", "TRF-BILL-LC-001");
        assertThat(page.getByTestId("bill-status")).hasText("Lunas");
        assertThat(billService.findById(bill.getId()).getStatus()).isEqualTo(BillStatus.PAID);
        takeManualScreenshot("10-bill-payment");

        openAging("payables");
        assertThat(page.locator("[data-testid='aging-row-" + vendor.getCode() + "']")).hasCount(0);

        navigateTo("/statements/vendor/" + vendor.getCode() + "?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();
        assertThat(statementBalances()).hasText(new String[] {"Rp 6,000,000", "Rp 0"});
        assertThat(page.locator("[data-testid='closing-balance'] td").last()).hasText("Rp 0");
        takeManualScreenshot("10-vendor-statement");
    }

    private void openAging(String type) {
        navigateTo("/reports/aging/" + type + "?asOfDate=" + AGING_AS_OF);
        waitForPageLoad();
    }

    private void assertAgingRow(String code, String oneToThirtyDays) {
        Locator cells = page.locator("[data-testid='aging-row-" + code + "'] td:not(:first-child)");
        assertThat(cells).hasText(new String[] {"Rp 0", oneToThirtyDays, "Rp 0", "Rp 0", "Rp 0", oneToThirtyDays});
    }

    private void pay(String date, String amount, String reference) {
        page.getByTestId("toggle-payment-form").click();
        page.locator("#paymentDate").fill(date);
        page.locator("#amount").fill(amount);
        page.locator("#paymentMethod").selectOption("TRANSFER");
        page.locator("#referenceNumber").fill(reference);
        page.getByTestId("submit-payment").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Pembayaran berhasil dicatat");
    }

    private Locator statementBalances() {
        return page.locator("[data-testid='statement-table'] tbody tr:not([data-testid]) td:last-child");
    }
}
